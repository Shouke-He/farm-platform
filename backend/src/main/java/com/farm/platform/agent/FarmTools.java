package com.farm.platform.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantDisease;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.entity.Reminder;
import com.farm.platform.entity.SensorData;
import com.farm.platform.entity.SolarTerm;
import com.farm.platform.mapper.DeviceMapper;
import com.farm.platform.mapper.PlantDiseaseMapper;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import com.farm.platform.service.GrowthStageService;
import com.farm.platform.service.ReminderService;
import com.farm.platform.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** 智能助手的工具集：模型通过这些方法去"查真数据"，而不是凭空编。 */
@Slf4j
@Component
@RequiredArgsConstructor
// 与 AgentConfig / AgentService 同一个开关：关掉时必须整包消失，不留半个 bean
@ConditionalOnProperty(name = "farm.agent.enabled", havingValue = "true")   // agent.enabled=true 才装配
public class FarmTools {

    private final PlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final PlantDiseaseMapper diseaseMapper;
    private final DeviceMapper deviceMapper;
    private final GrowthStageService growthStageService;
    private final WeatherService weatherService;
    private final ReminderService reminderService;
    private final KnowledgeIndexer knowledgeIndexer;

    /** 回给模型的绝对时间格式（yyyy-MM-dd HH:mm）—— 时间闸门拒绝时用它把"现在几点"告诉模型 */
    private static final java.time.format.DateTimeFormatter WHEN_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 指标名 → {中文名, 单位}。sensor_data.metric 存的是英文代码，直接丢给模型会答出"soil_humidity=21.3"这种话 */
    // ⚠️ rainfall（雨量）这一项**已经下线**（室内设备没有雨量计，固件把它换成了 rssi，见 IotTelemetryService），
    private static final Map<String, String[]> METRICS = Map.of(
            "soil_humidity", new String[]{"土壤湿度", "%"},
            "soil_temp", new String[]{"土壤温度", "℃"},
            "air_temp", new String[]{"气温", "℃"},
            "air_humidity", new String[]{"空气湿度", "%"},
            "light", new String[]{"光照", "lux"},
            "rainfall", new String[]{"雨量", "mm"});

    /** 已接入天气预报的城市（与 WeatherService.CITY_NAMES / application.yml farm.weather.cities 同步） */
    private static final Map<String, String> SUPPORTED_CITIES = Map.of(
            "南宁", "101300101", "南宁市", "101300101",
            "柳州", "101300301", "柳州市", "101300301",
            "桂林", "101300501", "桂林市", "101300501");

    // ------------------------------------------------------------------ 工具

    @Tool(description = """
            查询当前用户某盆植物的实时环境读数（土壤湿度、土壤温度、气温、空气湿度、光照、雨量），
            并给出该品种的适宜区间和是否越界。
            当用户问"我的花/菜现在怎么样""土壤湿度多少""要不要浇水""温度合不合适""光照够不够"时调用。
            """)
    public String getSensorReading(
            @ToolParam(description = "植物名字，例如「薄荷」「横县茉莉花」") String plantName,
            ToolContext ctx) {
        return sensorReadingText(currentUserId(ctx), plantName);
    }

    /** 读数的中文描述 —— <b>工具与读数闸门的确定性兜底共用这一份</b>（同一份数据、同一句话，不各写一遍）。 */
    public String sensorReadingText(Long userId, String plantName) {
        Plant p = findMyPlant(userId, plantName);
        if (p == null) {
            return "当前用户名下没有找到叫「" + plantName + "」的植物。" + rosterHint(userId)
                    + "请如实告诉用户他名下没有这盆，可以把现有的盆栽名报给他，"
                    + "但绝对不要替它编一个读数或状态出来。";
        }
        if (p.getDeviceId() == null) {
            return "「" + p.getName() + "」还没有绑定监测设备，所以查不到环境读数。";
        }

        List<SensorData> rows = deviceMapper.selectLatestByDevice(p.getDeviceId());
        if (rows == null || rows.isEmpty()) {
            return "「" + p.getName() + "」绑定的设备暂时没有上报过数据。";
        }

        PlantSpecies sp = speciesMapper.selectById(p.getSpeciesId());

        // 采集时间（2026-09-30 深夜加）：只报"最新读数"而不报时间，被追问时模型很容易说成"现在"——
        LocalDateTime collectedAt = rows.stream()
                .map(SensorData::getCollectTime)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // 🔴 只保留「和最新一条时间接近」的指标（2026-09-30 深夜加）：
        LocalDateTime freshLine = collectedAt == null ? null : collectedAt.minusMinutes(5);
        if (freshLine != null) {
            final LocalDateTime line = freshLine;
            rows = rows.stream()
                    .filter(r -> r.getCollectTime() == null || !r.getCollectTime().isBefore(line))
                    .toList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("「").append(p.getName()).append("」最新环境读数");
        if (collectedAt != null) {
            sb.append("（采集时间 ").append(collectedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("）");
        }
        sb.append("：");
        for (SensorData r : rows) {
            // METRICS 同时充当白名单：sensor_data 里还有 rssi(信号强度) 这类运维指标，
            String[] meta = METRICS.get(r.getMetric());
            if (meta == null) {
                continue;
            }
            sb.append(meta[0]).append(" ").append(trim(r.getValue())).append(meta[1]).append("；");
        }

        // 适宜区间跟着读数一起给：模型只有读数、没有区间时，很容易自己编一个"适宜下限"出来
        if (sp != null) {
            sb.append("该品种（").append(sp.getName()).append("）适宜：")
              .append("土壤湿度 ").append(sp.getSoilMin()).append("%~").append(sp.getSoilMax()).append("%、")
              .append("气温 ").append(sp.getTempMin()).append("~").append(sp.getTempMax()).append("℃。");
            String verdict = judge(rows, sp);
            if (!verdict.isEmpty()) {
                sb.append("当前").append(verdict);
            }
        }
        return sb.toString();
    }

    @Tool(description = """
            查询某盆植物的品种资料和当前生长阶段：品种名、当前处于哪个生长阶段、
            适宜土壤湿度上下限、适宜气温上下限、光照需求、浇水周期、施肥周期、养护要点。
            当用户问"该怎么养""现在是什么阶段""多久浇一次水""施什么肥"时调用。
            追问里用「它」「这盆」指代时同样要调用 —— 名字对上了不等于参数记得住，
            浇水周期、施肥周期这类数字一律以本工具返回的为准，不许凭印象报。
            🔴 用户**没有养**的品种照样可以查：品种库里有 188 个品种（草莓、水稻这类常见品种都在），
            问「草莓怎么养」时也要调用本工具，但回答里必须注明"这是品种库里的通用资料，不是你家某盆的数据"。
            """)
    public String getPlantProfile(   // 【6个工具】
            @ToolParam(description = "植物名字，例如「薄荷」") String plantName,
            ToolContext ctx) {

        Long userId = currentUserId(ctx);
        Plant p = findMyPlant(userId, plantName);
        if (p == null) {
            // 🔴 不在用户名下 ≠ 查不到（2026-09-30 深夜补）。品种库里有 188 个品种，
            PlantSpecies lib = findSpeciesByName(plantName);
            if (lib != null) {
                return "「" + lib.getName() + "」（这是**品种库里的品种资料**，该用户名下**并没有**这盆植物）："
                        + speciesFacts(lib)
                        + "回答时必须先讲清楚：这是品种库的通用资料、不是他家某盆植物的数据；"
                        + "他要是想知道实时读数或生长阶段，得先添加这盆植物。";
            }
            return "当前用户名下没有找到叫「" + plantName + "」的植物，品种库里也没有这个品种。" + rosterHint(userId)
                    + "请如实告诉用户这两件事（名下没有、品种库也没有），不要编造品种和养护建议。";
        }

        PlantSpecies sp = speciesMapper.selectById(p.getSpeciesId());
        if (sp == null) {
            return "「" + p.getName() + "」没有关联品种资料，查不到养护参数。";
        }

        String stage = null;
        try {
            Map<String, Object> s = growthStageService.calc(sp, p.getPlantDate());
            if (s != null && s.get("stageName") != null) {
                stage = String.valueOf(s.get("stageName"));
            }
        } catch (Exception e) {
            log.warn("生长阶段推算失败：{}", e.getMessage());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("「").append(p.getName()).append("」（品种：").append(sp.getName()).append("）");
        sb.append("：栽种日期 ").append(p.getPlantDate());
        sb.append(stage == null ? "，当前生长阶段推算不出来。" : "，当前处于「" + stage + "」。");
        sb.append(speciesFacts(sp));
        return sb.toString();
    }

    /** 品种参数那一段（适宜区间 / 浇水施肥周期 / 光照 / 养护要点 / 易发病虫害）。 */
    private String speciesFacts(PlantSpecies sp) {
        StringBuilder sb = new StringBuilder();
        sb.append("适宜土壤湿度 ").append(sp.getSoilMin()).append("%~").append(sp.getSoilMax()).append("%；");
        sb.append("适宜气温 ").append(sp.getTempMin()).append("~").append(sp.getTempMax()).append("℃；");
        sb.append("光照需求 ").append(sp.getLightLevel()).append("；");
        if (sp.getWaterCycle() != null) {
            sb.append("建议浇水周期 ").append(sp.getWaterCycle()).append(" 天；");
        }
        if (sp.getFertilizeCycle() != null) {
            sb.append("建议施肥周期 ").append(sp.getFertilizeCycle()).append(" 天；");
        }
        if (sp.getCareTips() != null && !sp.getCareTips().isBlank()) {
            sb.append("养护要点：").append(sp.getCareTips()).append("；");
        }
        if (sp.getDiseases() != null && !sp.getDiseases().isBlank()) {
            sb.append("易发病虫害：").append(sp.getDiseases()).append("。");
        }
        return sb.toString();
    }

    @Tool(description = """
            查询「某种植物容易得哪些病虫害」—— 只给名单，不给某个具体症状是什么病，也不给防治方法。
            当用户问"我的X容易得什么病""X会生什么虫""X的易感病虫害有哪些"时调用。
            换了一盆再问（"那茉莉花呢""它呢""另一盆呢"）同样要调用 —— 换一盆就是一次全新的查询。
            当用户描述了具体症状问"这是什么病""怎么治"时，不要用本工具，改用知识库检索工具。
            调用后按返回值里的【本轮回答要求】分两步回答。
            """)
    public String getSusceptibleDiseases(   // 【6个工具】
            @ToolParam(description = "植物名字，例如「薄荷」「横县茉莉花」") String plantName,
            ToolContext ctx) {

        Long userId = currentUserId(ctx);
        String want = plantName == null ? "" : plantName.trim();
        if (want.isEmpty()) {
            return "植物名字是空的，没有查询。请先问清楚用户问的是哪一盆。";
        }

        // 拿"该用哪些名字去和病虫害库比对"。
        Plant p = findMyPlant(userId, want);
        List<String> sources = new ArrayList<>();
        String subject;
        if (p != null) {
            PlantSpecies sp = speciesMapper.selectById(p.getSpeciesId());
            subject = "「" + p.getName() + "」";
            if (sp != null && sp.getName() != null && !sp.getName().isBlank()) {
                subject += "（品种：" + sp.getName() + "）";
                sources.add(sp.getName());
            }
            sources.add(p.getName());
        } else {
            // 不在用户名下 —— 仍然可以问"薄荷容易得什么病"这类公共知识问题。
            PlantSpecies sp = findSpeciesByName(want);
            subject = "「" + want + "」（不属于该用户）";
            sources.add(want);
            if (sp != null && sp.getName() != null && !sp.getName().isBlank()) {
                sources.add(sp.getName());
            }
        }

        // 🔴 反查病虫害库（plant_disease，158 条，人工逐条维护）—— 这是本模块唯一的可信源。
        List<PlantDisease> all = diseaseMapper.selectList(null);
        List<PlantDisease> hits = new ArrayList<>();
        List<String> matchedWords = new ArrayList<>();
        for (PlantDisease d : all) {
            String w = matchedWord(d.getPlants(), sources);
            if (w != null) {
                hits.add(d);
                if (!matchedWords.contains(w)) {
                    matchedWords.add(w);
                }
            }
        }
        log.info("助手反查易感病虫害：{} → 比对名 {}，命中 {} 条（匹配词 {}）",
                want, sources, hits.size(), matchedWords);

        if (hits.isEmpty()) {
            // 这是高频路径，不是边角料：病虫害库只覆盖 82 个植物名，品种库有 188 个品种。
            return "【未收录】病虫害库（共 " + all.size() + " 条）里没有任何一条把" + subject
                    + "列为易感植物，也没有名字对得上的记录。\n"
                    + "请如实告诉用户「病虫害库暂未收录这种植物的病虫害」。"
                    + "不要说成\"这种植物没有病虫害\"，也不要用相近植物（同科/同属/同类）的记录顶替。\n"
                    + "如果用户能描述具体症状（叶子发黄、长斑、有虫、枯萎、烂根），"
                    + "提示他可以描述症状，你再用知识库检索工具帮他查。";
        }

        List<String> names = new ArrayList<>();
        for (PlantDisease d : hits) {
            names.add(d.getName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(subject).append(" 的易感病虫害 · 名单】\n");
        sb.append("收录情况：病虫害库把").append(subject).append("列为易感植物的共 ")
          .append(names.size()).append(" 种 —— ").append(String.join("、", names)).append("。\n");
        sb.append("【本轮回答要求】\n");
        sb.append("① 第一句先正面回答\"是，病虫害库把它列为 ").append(names.size())
          .append(" 种病虫害的易感植物\"，并把名字全部报出来；\n");
        sb.append("② 然后问用户想看哪一个的症状和防治方法 —— **本轮不要给出症状、防治方法和药剂**；\n");
        sb.append("③ 除了上面这几个名字，一个都不许再加：不要拿\"同科/同属/养护相近\"去补，"
                + "也不要把\"花卉/蔬菜\"这类大类名扯进来凑数；\n");
        sb.append("④ 用户选定某一个之后，用知识库检索工具查它的症状和防治方法。");
        return sb.toString();
    }

    @Tool(description = """
            查询某个城市今明后三天的天气预报（白天天气、最高最低气温）。
            当用户问"明天会不会下雨""要不要把花盆搬进来""这几天要不要浇水"时调用。
            目前只接入了南宁、柳州、桂林三个城市。
            🔴 回答末尾必须原样保留返回值里的出处说明（"数据来源：和风天气…"），
            这句是让用户知道数据是从接口查来的、不是模型编的。
            """)
    public String getWeather(   // 【6个工具】
            @ToolParam(description = "城市名，例如「南宁」「桂林」；用户没说城市时传空字符串") String city) {

        String cityId;
        if (city == null || city.isBlank()) {
            cityId = "101300101";                                   // 与 application.yml farm.weather.location 一致
        } else {
            cityId = SUPPORTED_CITIES.get(city.trim());
            if (cityId == null && !city.trim().endsWith("市")) {
                cityId = SUPPORTED_CITIES.get(city.trim() + "市");
            }
            if (cityId == null) {
                return "目前只接入了「南宁」「柳州」「桂林」三个城市的天气，查不了「" + city + "」。"
                        + "请如实告诉用户查不到这个城市，不要拿别的城市的数据顶上。";
            }
        }

        try {
            List<WeatherService.DayWeather> days = weatherService.forecast3d(cityId);
            if (days == null || days.isEmpty()) {
                return "天气服务暂时没有返回数据。";
            }
            StringBuilder sb = new StringBuilder(WeatherService.cityName(cityId)).append("未来三天预报：");
            for (WeatherService.DayWeather d : days) {
                sb.append(d.fxDate()).append(" ").append(d.textDay())
                  .append("，").append(d.tempMin()).append("~").append(d.tempMax()).append("℃；");
            }
            // 2026-11-14 加出处标注：天气回答原来只有文字，看不出是查接口来的还是模型编的，
            // 和读数工具的做法对齐——把数据来源和时间写进返回值，要求模型原样带上
            sb.append("（数据来源：和风天气接口，查询时间 ")
              .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
              .append("，不是模型生成的。）");
            return sb.toString();
        } catch (Exception e) {
            log.warn("天气查询失败：{}", e.getMessage());
            return "天气服务暂时不可用。";
        }
    }

    @Tool(description = """
            检索农业知识库，查病虫害的症状、发病原因、防治方法和可用药剂，以及二十四节气的农事要点。
            用户描述植物异常（叶子发黄、长斑、有虫、枯萎、烂根）问"怎么办"时调用。
            用户问节气农事（"现在这个节气该干什么""这个节气种什么"）时**必须先调用本工具** ——
            返回的每条节气都带公历日期，据此对照今天的日期选出当前节气，不要凭记忆报节气名。
            这是查资料，不是查用户自己的数据。
            """)
    public String searchKnowledge(   // 【6个工具】
            @ToolParam(description = "检索用的问句，尽量用用户的原话，例如「叶子发黄怎么办」「白粉病怎么治」") String query) {

        if (query == null || query.isBlank()) {
            return "检索词是空的，没有检索。请先问清楚用户想查什么。";
        }

        // 库里 158 条病虫害有相当一部分症状描述是同一段模板文字，彼此向量很近、会互相挤占名额，
        List<Document> hits = knowledgeIndexer.retrieve(query, 6);
        List<Document> picked = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Document d : hits) {
            if (!seen.contains(d.getText())) {
                seen.add(d.getText());
                picked.add(d);
            }
            if (picked.size() >= 3) {
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        // 命中节气类内容时，把"按今天日期算出的当前节气"确定性地补上。
        boolean solarHit = picked.stream().anyMatch(d -> "solar_term".equals(d.getMetadata().get("type")));
        if (solarHit) {
            SolarTerm cur = knowledgeIndexer.currentSolarTerm(LocalDate.now());
            if (cur != null) {
                sb.append("【今天是 ").append(LocalDate.now())
                  .append("，按日期算当前节气是「").append(cur.getTermName())
                  .append("」（公历约 ").append(cur.getMonthDay()).append("）。")
                  .append("它的农事要点：").append(cur.getAdvice()).append("】\n");
            }
        }

        if (picked.isEmpty()) {
            // 索引还没建好、或者确实没有相关内容 —— 两种都如实说，不要让模型自己发挥
            return sb + "知识库里没有检索到和「" + query + "」相关的内容。"
                    + "请如实告诉用户知识库里没有，不要凭常识编一条防治方法给他。";
        }

        sb.append("知识库检索到以下内容：\n");
        for (int i = 0; i < picked.size(); i++) {
            sb.append(i + 1).append(". ").append(picked.get(i).getText()).append("\n");
        }
        sb.append("请只依据以上内容回答，能对上具体病虫害就说清是哪个；"
                + "如果上面的内容答不了用户的问题，如实说知识库里没有，不要补充你自己的经验。");
        return sb.toString();
    }

    /** 写操作工具：替用户建一条农事任务。 */
    @Tool(description = """
            帮用户创建一条农事任务（待办提醒），创建后会出现在「农事任务」页面。
            只有用户明确说"帮我建个任务""提醒我…""帮我记一下要做…"时才调用；
            用户只是在提问、查询或闲聊时，绝对不要调用这个工具。
            """)
    public String createFarmTask(   // 【6个工具】
            @ToolParam(description = "植物名字，例如「薄荷」。如果是不针对某一盆的通用农活，传空字符串") String plantName,
            @ToolParam(description = "任务标题，例如「浇水」「施肥」「喷药」") String title,
            @ToolParam(description = "提醒时间，必须已换算成绝对时间，格式 yyyy-MM-dd HH:mm，例如 2026-09-30 09:00") String remindTime,
            ToolContext ctx) {

        Long userId = currentUserId(ctx);
        Long roleId = currentRoleId(ctx);

        if (title == null || title.isBlank()) {
            return "任务标题是空的，没有创建。请先问清楚用户要做什么。";
        }

        // 关联植物（可选）：给了名字就必须是自己名下的，否则不建 —— 避免把任务挂到别人的盆栽上
        Plant p = null;
        if (plantName != null && !plantName.isBlank()) {
            p = findMyPlant(userId, plantName);
            if (p == null) {
                return "当前用户名下没有找到叫「" + plantName + "」的植物，任务没有创建。"
                        + rosterHint(userId) + "请如实告诉用户，不要改挂到别的盆栽上。";
            }
        }

        LocalDateTime when = parseWhen(remindTime);
        LocalDateTime now = LocalDateTime.now();
        if (when == null) {
            return "提醒时间没能理解，任务没有创建。现在是 " + now.format(WHEN_FMT)
                    + "，请据此重新给出 yyyy-MM-dd HH:mm 格式的时间再试一次。";
        }
        // 时间闸门（2026-10-16 加）：提醒时间早于"现在"就拒掉。
        // 为什么需要：模型没有时钟，相对时间要靠 system 里给的"现在几点"换算；万一它又猜错，
        // 这里直接把真实时刻回给它，它会自己重算 —— 而不是默默建一条"已逾期"的任务（实测差过 4 小时）。
        // 留 1 分钟容差，避免边界上把"一分钟内"的正常请求挡掉。
        if (when.isBefore(now.minusMinutes(1))) {   // 【时间算错】
            return "提醒时间 " + when.format(WHEN_FMT) + " 已经过去了（现在是 " + now.format(WHEN_FMT) + "），任务没有创建。"
                    + "如果用户说的是相对时间（两分钟后 / 明天早上），请按这个真实时刻重新换算；"
                    + "如果用户是在补记已经做完的农活，请让他到「农事任务」页面用「记录」补登，不要建成待办任务。";
        }

        Reminder r = new Reminder();
        r.setUserId(userId);
        r.setPlantId(p == null ? null : p.getId());
        r.setTitle(title.trim());
        r.setType("custom");                 // 与界面新建的任务同一类型，否则不会出现在用户的任务列表里
        r.setSource("智能助手");              // 与"种植建议引擎/物联网传感器"同一约定，标明这条是谁建的
        r.setRemindTime(when);

        try {
            Reminder saved = withUserContext(userId, roleId, () -> reminderService.create(r));
            return "已创建任务：「" + saved.getTitle() + "」"
                    + (p == null ? "" : "（" + p.getName() + "）")
                    + "，提醒时间 " + format(saved.getRemindTime())
                    + "。可以在「农事任务」页面看到和修改它。"
                    + "请照这部分转述即可，不要额外承诺「一定会提醒到你」——平台的通知只在用户打开着页面时送达，关掉页面就收不到。";
        } catch (Exception e) {
            log.warn("助手建任务失败：{}", e.getMessage());
            return "任务创建失败：" + e.getMessage() + "。请如实告诉用户任务没有建成，不要说他建好了。";
        }
    }

    // ------------------------------------------------------------------ 内部

    /** 按名字找当前用户名下的盆栽；找不到返回 null。 */
    private Plant findMyPlant(Long userId, String plantName) {
        if (userId == null || userId < 0 || plantName == null || plantName.isBlank()) {
            return null;
        }
        List<Plant> mine = plantMapper.selectList(
                new LambdaQueryWrapper<Plant>().eq(Plant::getUserId, userId));
        if (mine.isEmpty()) {
            return null;
        }
        String want = plantName.trim();
        return mine.stream()
                .filter(p -> want.equals(p.getName()))
                .findFirst()
                .orElseGet(() -> mine.stream()
                        .filter(p -> p.getName() != null
                                && (p.getName().contains(want) || want.contains(p.getName())))
                        .findFirst()
                        .orElse(null));
    }

    /** 在这段文字里找出**该用户名下**的那盆植物（读数闸门的确定性兜底用）。 */
    public Plant plantMentionedIn(Long userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return null;
        }
        List<Plant> mine = plantMapper.selectList(
                new LambdaQueryWrapper<Plant>().eq(Plant::getUserId, userId).orderByAsc(Plant::getId));
        for (Plant p : mine) {
            if (p.getName() != null && !p.getName().isBlank() && text.contains(p.getName())) {
                return p;
            }
        }
        return null;
    }

    /** 按名字在品种库里找品种（用户问的植物不在他名下时走这条，属于查公共知识）。 */
    private PlantSpecies findSpeciesByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<PlantSpecies> all = speciesMapper.selectList(null);
        for (PlantSpecies s : all) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        if (name.length() <= 1) {
            return null;
        }
        for (PlantSpecies s : all) {
            String sn = s.getName();
            if (sn == null || sn.length() <= 1) {
                continue;
            }
            if (sn.contains(name) || name.contains(sn)) {
                return s;
            }
        }
        return null;
    }

    /** 把「锈病,白粉病,蚜虫」这类逗号分隔的名单切成列表；中英文逗号、顿号、分号都认 */
    private static List<String> splitList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String s : raw.split("[,，、;；]")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 品种名比病虫害库里的词多出来的尾巴：只认这些，"茉莉花"才算"茉莉"的一种 */
    private static final java.util.Set<String> VARIETY_SUFFIX =
            java.util.Set.of("花", "植物", "树", "苗", "菜", "草", "子", "果", "叶", "藤");

    /** 名字里带这些词的，**不是**后面那个词所指的植物。 */
    private static final String[][] NOT_SAME = {{"番石榴", "石榴"}, {"洋葱", "葱"}};

    /** 两个名字是不是同一种植物（含"XX花/XX植物"这类品种写法）。 */
    private static boolean samePlant(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        for (String[] pair : NOT_SAME) {
            if ((pair[0].equals(a) && pair[1].equals(b)) || (pair[0].equals(b) && pair[1].equals(a))) {
                return false;
            }
        }
        if (a.startsWith(b) && VARIETY_SUFFIX.contains(a.substring(b.length()))) {
            return true;
        }
        return a.endsWith(b);
    }

    /** 这条病虫害的"易感植物"里，有没有哪个词指的是这几个候选名字之一；返回命中的那个词 */
    private static String matchedWord(String plantsField, List<String> sources) {
        for (String w : splitList(plantsField)) {
            for (String s : sources) {
                if (samePlant(s, w) || samePlant(w, s)) {
                    return w;
                }
            }
        }
        return null;
    }

    /** 「该用户名下现在有哪几盆」——拼给模型看的一句话，用在各工具"没找到"的分支里。 */
    private String rosterHint(Long userId) {
        try {
            List<Plant> mine = plantMapper.selectList(
                    new LambdaQueryWrapper<Plant>().eq(Plant::getUserId, userId).orderByAsc(Plant::getId));
            List<String> names = new ArrayList<>();
            for (Plant p : mine) {
                if (p.getName() != null && !p.getName().isBlank()) {
                    names.add(p.getName().trim());
                }
            }
            return names.isEmpty()
                    ? "该用户名下目前没有添加任何植物。"
                    : "该用户名下现有：" + String.join("、", names) + "。";
        } catch (Exception e) {
            log.warn("查询用户名下盆栽失败：{}", e.getMessage());
            return "";
        }
    }

    /** 从工具上下文取当前用户 id。 */
    private Long currentUserId(ToolContext ctx) {   // 【身份来自令牌】
        Map<String, Object> map = ctx == null ? null : ctx.getContext();
        Object v = map == null ? null : map.get("userId");
        if (v instanceof Number n) {
            return n.longValue();
        }
        return -1L;
    }

    /** 取当前用户角色；取不到一律当普通用户(2) —— 宁可权限给小，也不能因为取不到就当管理员 */
    private Long currentRoleId(ToolContext ctx) {
        Map<String, Object> map = ctx == null ? null : ctx.getContext();
        Object v = map == null ? null : map.get("roleId");
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 2L;
    }

    /** 🔴 给 {@link ReminderService#create} 搭的 ThreadLocal 身份桥。 */
    private <T> T withUserContext(Long userId, Long roleId, Supplier<T> action) {
        Long prevUserId = UserContext.getUserId();
        Long prevRoleId = UserContext.getRoleId();
        UserContext.set(userId, roleId);
        try {
            return action.get();
        } finally {
            if (prevUserId == null && prevRoleId == null) {
                UserContext.clear();
            } else {
                UserContext.set(prevUserId, prevRoleId);
            }
        }
    }

    /** 把模型给的时间字符串解析成 LocalDateTime。 */
    private LocalDateTime parseWhen(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim().replace('T', ' ').replaceAll("\\s+", " ");
        String[] patterns = {"yyyy-M-d H:m", "yyyy-M-d H:m:s", "yyyy/M/d H:m", "yyyy.M.d H:m"};
        for (String p : patterns) {
            try {
                return LocalDateTime.parse(t, DateTimeFormatter.ofPattern(p));
            } catch (Exception ignored) {
                // 换下一种格式接着试
            }
        }
        return null;
    }

    /** 回话里显示的时间，固定成 yyyy-MM-dd HH:mm，别把 ISO 的 T 露给用户看 */
    private String format(LocalDateTime t) {
        return t == null ? "未设置" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /** 拿读数和品种区间比一比，返回"土壤湿度 21.3% 低于适宜下限 40%"这类中文结论；没有可比项返回空串 */
    private String judge(List<SensorData> rows, PlantSpecies sp) {
        StringBuilder sb = new StringBuilder();
        for (SensorData r : rows) {
            double v = r.getValue() == null ? Double.NaN : r.getValue().doubleValue();
            if (Double.isNaN(v)) {
                continue;
            }
            if ("soil_humidity".equals(r.getMetric()) && sp.getSoilMin() != null && sp.getSoilMax() != null) {
                if (v < sp.getSoilMin()) {
                    sb.append("土壤湿度 ").append(trim(r.getValue())).append("% 低于适宜下限 ").append(sp.getSoilMin()).append("%；");
                } else if (v > sp.getSoilMax()) {
                    sb.append("土壤湿度 ").append(trim(r.getValue())).append("% 高于适宜上限 ").append(sp.getSoilMax()).append("%；");
                }
            } else if (("air_temp".equals(r.getMetric()) || "soil_temp".equals(r.getMetric()))
                    && sp.getTempMin() != null && sp.getTempMax() != null) {
                if (v < sp.getTempMin()) {
                    sb.append(METRICS.get(r.getMetric())[0]).append(" ").append(trim(r.getValue())).append("℃ 低于适宜下限 ").append(sp.getTempMin()).append("℃；");
                } else if (v > sp.getTempMax()) {
                    sb.append(METRICS.get(r.getMetric())[0]).append(" ").append(trim(r.getValue())).append("℃ 高于适宜上限 ").append(sp.getTempMax()).append("℃；");
                }
            }
        }
        return sb.toString();
    }

    /** BigDecimal 直接 toString 会出现 21.3000 这种尾巴，去掉多余的 0 再给模型 */
    private String trim(BigDecimal v) {
        if (v == null) {
            return "无";
        }
        return v.stripTrailingZeros().toPlainString();
    }
}
