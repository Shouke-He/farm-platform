package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.common.BusinessException;
import com.farm.platform.entity.Device;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.entity.SensorData;
import com.farm.platform.mapper.DeviceMapper;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 种植物服务：「我的盆栽」页面的数据管家（对应数据库 plant 表）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlantService {

    private final PlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final DeviceMapper deviceMapper;
    private final GrowthStageService growthStageService;
    private final WeatherService weatherService;
    private final ObjectMapper objectMapper;

    /** 我的盆栽列表：只查自己名下（plant.user_id = 当前用户）的，按 id 倒序（新加的排最前）， */
    public List<Plant> listByUser(Long userId) {
        List<Plant> list = plantMapper.selectList(new LambdaQueryWrapper<Plant>()
                .eq(Plant::getUserId, userId).orderByDesc(Plant::getId));
        list.forEach(this::fill);
        return list;
    }

    /** 补全品种信息/设备名/最新指标/生长期/浇水时机 */
    private void fill(Plant p) {
        // 品种：土壤湿度上下限、水肥周期这些养护参数都挂在品种（plant_species）上
        PlantSpecies sp = p.getSpeciesId() == null ? null : speciesMapper.selectById(p.getSpeciesId());   // 【补齐品种设备】
        if (sp != null) {
            p.setSpecies(sp);
            p.setSpeciesName(sp.getName());
            p.setSpeciesEmoji(sp.getEmoji());
        }
        if (p.getDeviceId() != null) {
            Device d = deviceMapper.selectById(p.getDeviceId());
            if (d != null) {
                p.setDeviceName(d.getDeviceName());
                p.setDeviceStatus(d.getStatus());
                // 设备最新指标：sensor_data 里每个 metric 取最新一条，拼成 {soil_humidity: 21.3, ...}
                Map<String, Object> map = new LinkedHashMap<>();
                for (SensorData sd : deviceMapper.selectLatestByDevice(d.getId())) {
                    map.put(sd.getMetric(), sd.getValue());
                }
                p.setLatest(map);
            }
        }
        // 生长期推算（品种阶段模型 + 种植日期）；用户手动标注过生长阶段则以其为准
        Map<String, Object> stage = growthStageService.calc(sp, p.getPlantDate());   // 【阶段与浇水】
        if (p.getStageName() != null && !p.getStageName().isBlank()) {
            overrideStage(stage, sp, p.getStageName(), p.getStageDate());
        }
        p.setStage(stage);
        // 浇水时机 5 档（品种适宜区间 + 土壤实时湿度 + 明日天气）
        p.setWatering(wateringAdvice(sp, p.getLatest()));
    }

    /** 用户手动标注的生长阶段优先：替换推算阶段名，并从品种阶段表补齐 desc/下一阶段。 */
    private void overrideStage(Map<String, Object> stage, PlantSpecies sp, String name, LocalDate stageDate) {   // 【手动优先】
        // 与按天数推算的阶段一致时无需覆盖（保留阶段内天数进度展示）
        if (name.equals(stage.get("stageName"))) {
            return;
        }
        stage.put("stageName", name);
        if (sp == null || sp.getGrowthStages() == null || sp.getGrowthStages().isBlank()) {
            return;
        }
        try {
            // 品种的阶段模型是一段 JSON：[{"stage":"幼苗期","days":20,"desc":"..."}, ...]
            List<Map<String, Object>> list = objectMapper.readValue(sp.getGrowthStages(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            // 先定位人工标注的是第几个阶段；标注名不在模型里（品种被改过/手工填写）就只换名字，不补进度
            int start = -1;
            for (int i = 0; i < list.size(); i++) {
                if (name.equals(list.get(i).get("stage"))) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                return;
            }
            // 自标注日起过了几天（+1 在下面，因为标注当天要算第 1 天）；标注日期落在未来时按 0 兜底
            long remain = stageDate == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(stageDate, LocalDate.now()));
            // 从标注的那个阶段开始往后消耗天数，直到"剩下的天数装得进某个阶段"为止
            int idx = start;
            // 最后一个阶段不参与消耗——它是兜底落点：全周期都过完了也停在这，不会越界
            while (idx < list.size() - 1) {
                int d = ((Number) list.get(idx).get("days")).intValue();
                // days>=9999 是长期阶段（多年生/盆栽持续生长期），本身就不到期，直接停在它上面
                if (d >= 9999 || remain < d) {
                    break;
                }
                remain -= d;   // 这个阶段已经走完 → 顺延到下一阶段继续消耗
                idx++;
            }
            Map<String, Object> cur = list.get(idx);
            int total = ((Number) cur.get("days")).intValue();
            // 顺延过就是下一阶段的名字；没顺延（remain 还装得下）则仍是人工标注的那个名字
            stage.put("stageName", cur.get("stage"));
            stage.put("stageIndex", idx);
            stage.put("desc", cur.get("desc"));
            stage.put("stageTotal", total);
            stage.put("stageDay", Math.max(1, remain + 1));   // 进入本阶段当天算第 1 天
            // 下一阶段名；已经是最后一个阶段则为 null
            stage.put("nextStage", idx + 1 < list.size() ? list.get(idx + 1).get("stage") : null);
            // 超期 = 整个生长周期都走完了（落在最后一个阶段、且已超出它的天数）；
            if (remain + 1 > total && total < 9999) {
                stage.put("overdue", true);
            }
        } catch (Exception e) {
            log.warn("手动生长阶段匹配失败 plant={}: {}", name, e.getMessage());
        }
    }

    /** 浇水时机建议，按顺序往下判，命中一条就返回，返回值给前端当彩色标签用： */
    private Map<String, Object> wateringAdvice(PlantSpecies sp, Map<String, Object> latest) {
        // 兜底文案：没绑设备或还没上报数据时返回这一档
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "nodata");
        out.put("label", "暂无设备数据");
        out.put("message", "绑定设备后自动检测土壤湿度");
        // 取最近一次土壤湿度；品种参数不全也直接走兜底
        Object v = latest == null ? null : latest.get("soil_humidity");
        if (v == null || sp == null || sp.getSoilMin() == null || sp.getSoilMax() == null) {
            return out;
        }
        // 库里 value 列是 DECIMAL，取出来是 BigDecimal，先转 double 再比较
        double soil = ((BigDecimal) v).doubleValue();
        boolean rainTomorrow = hasRainTomorrow();
        // 明天有雨、且当前没干到"比适宜下限还低 10 个百分点"以下 → 建议等雨，先别浇
        if (rainTomorrow && soil >= sp.getSoilMin() - 10) {
            out.put("status", "rain");
            out.put("label", "等雨");
            out.put("message", "明天预报有雨，建议暂不浇水，雨后观察土壤湿度");
            return out;
        }
        // 已经低于品种适宜下限 → 需立即浇水
        if (soil < sp.getSoilMin()) {
            out.put("status", "urgent");
            out.put("label", "需立即浇水");
            out.put("message", String.format("当前土壤湿度 %.0f%%，低于适宜下限 %d%%，现在适合浇水", soil, sp.getSoilMin()));
            return out;
        }
        // 没到下限但只高出 10 个百分点以内 → 接近临界，建议 1 天内浇
        if (soil < sp.getSoilMin() + 10) {
            out.put("status", "critical");
            out.put("label", "接近临界");
            out.put("message", String.format("当前土壤湿度 %.0f%%，接近下限，建议 1 天内浇水", soil));
            return out;
        }
        // 高于适宜上限 → 太湿了，暂缓浇水
        if (soil > sp.getSoilMax()) {
            out.put("status", "wet");
            out.put("label", "土壤过湿");
            out.put("message", String.format("当前土壤湿度 %.0f%%，高于适宜上限 %d%%，暂缓浇水，注意排水", soil, sp.getSoilMax()));
            return out;
        }
        // 上面几档都没命中 → 落在适宜区间内
        out.put("status", "ok");
        out.put("label", "土壤适宜");
        out.put("message", String.format("当前土壤湿度 %.0f%%，处于适宜区间 %d%%~%d%%，保持观察", soil, sp.getSoilMin(), sp.getSoilMax()));
        return out;
    }

    /** 明天预报是否有雨（复用 WeatherService 30 分钟缓存，不烧和风配额） */
    private boolean hasRainTomorrow() {
        try {
            // 和风预报的日期格式是 yyyy-MM-dd，先拼出"明天"这个字符串再逐条比对
            String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            for (WeatherService.DayWeather d : weatherService.forecast3d()) {
                // 命中明天的预报，且白天天气文案里出现"雨"或"雪"就算有降水
                if (tomorrow.equals(d.fxDate()) && d.textDay() != null
                        && (d.textDay().contains("雨") || d.textDay().contains("雪"))) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 天气接口挂了也不能让页面报错，按"明天没雨"处理
            log.warn("预报查询失败，浇水时机忽略雨天因素: {}", e.getMessage());
        }
        return false;
    }

    /** 查单盆盆栽，并按和列表一样的口径装配（品种/设备最新指标/生长期/浇水时机）。 */
    public Plant getById(Long id) {
        Plant p = plantMapper.selectById(id);
        if (p != null) {
            fill(p);
        }
        return p;
    }

    /** 添加盆栽向导第 4 步：根据用户在前面几步选的品种、环境、种植方式、光照、容器、阶段， */
    public Map<String, Object> onboardAdvice(Long speciesId, String location, String growMethod,   // 【追问·上传后缀】
                                             String sunlight, String container, String stageName) {
        PlantSpecies sp = speciesMapper.selectById(speciesId);
        if (sp == null) {
            throw new BusinessException(400, "品种不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();

        // 1) 匹配判断：大田作物(crop)偏露天，盆栽(pot)偏室内；未选环境时按"中"（与文案兜底语义一致）
        String level, extra = "";
        if (location == null || location.isBlank()) {
            level = "中";
        } else if ("crop".equals(sp.getCategory())) {
            if ("菜地".equals(location) || "小院".equals(location)) {
                level = "高";
            } else if ("露台".equals(location)) {
                level = "中";
            } else {
                level = "低";
            }
        } else if ("pot".equals(sp.getCategory())) {
            if ("阳台".equals(location) || "窗台".equals(location) || "室内".equals(location)) {
                level = "高";
            } else if ("露台".equals(location) || "小院".equals(location)) {
                level = "中";
            } else {
                level = "低";
            }
        } else {
            level = "中";
        }
        // 种植方式 × 容器的搭配提示：只有"土培 + 水培盒"这一种组合会给出不推荐的提醒
        if ("水培".equals(growMethod) && "水培盒".equals(container)) {
            extra = "；水培盒与水培适配，注意定期换水";
        } else if ("土培".equals(growMethod) && "水培盒".equals(container)) {
            extra = "；⚠ 水培盒用于土培易积水烂根，建议换陶盆/加排水孔";
        } else if ("水培".equals(growMethod)) {
            extra = "；水培建议配合水培盒或专用容器";
        }
        // 匹配度文案：没选环境时用兜底句，选了就把品种名和环境名嵌进去
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("level", level);
        match.put("text", (location == null || location.isBlank())
                ? String.format("%s 的%s，可在添加后随时调整养护参数", sp.getEmoji(), sp.getName()) + extra
                : String.format("%s %s 和「%s」的匹配度%s，可以按这个环境推进", sp.getEmoji(), sp.getName(), location, level) + extra);
        out.put("match", match);

        // 2) 养护计划：阶段要点 + 光照安排 + 水肥节奏
        String stageDesc = "";
        if (sp.getGrowthStages() != null && !sp.getGrowthStages().isBlank()) {
            try {
                List<Map<String, Object>> stages = objectMapper.readValue(sp.getGrowthStages(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                for (Map<String, Object> s : stages) {
                    if (stageName != null && stageName.equals(s.get("stage"))) {
                        stageDesc = s.get("desc") == null ? "" : s.get("desc").toString();
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        String stageText = !stageDesc.isBlank() ? stageDesc
                : (sp.getCareTips() != null && !sp.getCareTips().isBlank() ? sp.getCareTips()
                : "按品种日常习性正常养护即可");
        // 养护计划固定 3 条：当前阶段 / 光照安排 / 水肥节奏
        List<Map<String, Object>> plan = new ArrayList<>();
        plan.add(Map.of("title", "当前阶段", "text", "当前处于「" + (stageName == null ? "生长" : stageName)
                + "」：" + stageText));
        plan.add(Map.of("title", "光照安排", "text", "每天" + (sunlight == null ? "2-4 小时" : sunlight)
                + "光照，" + sp.getName() + "宜" + (sp.getLightLevel() == null ? "散光" : sp.getLightLevel()) + "，避开正午强光直射"));
        plan.add(Map.of("title", "水肥节奏", "text", "约每 " + sp.getWaterCycle() + " 天浇水、每 " + sp.getFertilizeCycle()
                + " 天施肥一次，结合土壤墒情和天气灵活调整"));
        out.put("plan", plan);

        // 3) 提醒计划：固定文案，插入品种/阶段/环境
        List<Map<String, Object>> remind = new ArrayList<>();
        remind.add(Map.of("title", "提醒生成方式", "text", "后续提醒会结合「" + sp.getName() + "」的"
                + (stageName == null ? "当前" : stageName) + "阶段和植物习性自动生成"));
        remind.add(Map.of("title", "提醒生成方式", "text", "系统会结合植物习性、当前阶段和天气变化生成养护提醒"));
        remind.add(Map.of("title", "提醒生成方式", "text", "天气变化时，会结合「"
                + (location == null || location.isBlank() ? "当前" : location) + "」场景追加提醒"));
        out.put("remind", remind);
        return out;
    }
}
