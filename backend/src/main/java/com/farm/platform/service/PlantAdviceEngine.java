package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantDisease;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.entity.Reminder;
import com.farm.platform.entity.SensorData;
import com.farm.platform.mapper.DeviceMapper;
import com.farm.platform.mapper.PlantDiseaseMapper;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import com.farm.platform.mapper.ReminderMapper;
import com.farm.platform.ws.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 种植建议引擎：每 5 分钟扫描所有盆栽， */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlantAdviceEngine {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");   // 与预报接口返回的 fxDate 同格式

    private final PlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final PlantDiseaseMapper diseaseMapper;
    private final DeviceMapper deviceMapper;
    private final ReminderMapper reminderMapper;
    private final NotificationService notificationService;
    private final WeatherService weatherService;

    /** 天气每日缓存（避免每 5 分钟调用和风 API 烧配额） */
    private LocalDate weatherDate;   // 缓存的是哪一天的预报，跨天才重新拉
    private Map<String, List<WeatherService.DayWeather>> weatherCache = Map.of();   // key=预报日期，value=那天的预报条目

    /** 体检入口：遍历所有盆栽，每盆依次跑四条检查，累加本次新生成的提醒条数。 */
    @Scheduled(fixedDelay = 300_000, initialDelay = 15_000)   // 【5分钟全扫】
    public int scan() {
        List<Plant> plants = plantMapper.selectList(null);   // 全量盆栽（跨用户），每盆只按自己的品种/设备/主人算
        if (plants.isEmpty()) {
            return 0;
        }
        Map<String, List<WeatherService.DayWeather>> forecast = forecastToday();   // 全程只取一次预报，循环里复用
        int created = 0;
        for (Plant p : plants) {
            // 按品种 id 查品种档案（适宜区间、易感病害、光照偏好都在这张表里）
            PlantSpecies sp = p.getSpeciesId() == null ? null : speciesMapper.selectById(p.getSpeciesId());
            if (sp == null) {
                continue;
            }
            Map<String, Object> latest = latestValues(p);   // 该盆栽绑定设备的最新一轮读数（没绑设备则为空 Map）
            created += advise(p, sp, latest);          // ① 适宜度检查
            created += pestPrevention(p, sp);          // ② 病虫害预防
            created += rainAdvice(p, sp, latest, forecast);   // ③ 明天有雨提示（暂缓还是补水，看土壤湿度）
            created += recoveryCheck(p, sp, latest);   // ④ 旧提醒的恢复检查
        }
        if (created > 0) {
            log.info("种植建议引擎：本次生成 {} 条提醒", created);
        }
        return created;
    }

    /** 当天预报缓存：每天第一次调用时拉一次和风 3 天预报，按"预报日期"分组存进 Map。 */
    private Map<String, List<WeatherService.DayWeather>> forecastToday() {
        LocalDate today = LocalDate.now();
        if (!today.equals(weatherDate)) {   // 跨天（或首次运行）才真正去拉一次
            List<WeatherService.DayWeather> list = weatherService.forecast3d();
            // 2026-10-17 修：**拉失败（返回空列表）时不要把日期记成"今天已拉"** ——
            // 否则当天剩下每一轮（每 5 分钟）都用空缓存，"明日有雨"一整天都不会触发。
            // 这里直接沿用上一份缓存（日期对不上时 rainAdvice 取不到"明天"，会安全跳过），
            // 让下一轮 5 分钟的重扫再试一次真调。
            if (list.isEmpty()) {
                return weatherCache;
            }
            weatherCache = new HashMap<>();
            for (WeatherService.DayWeather d : list) {
                // 同一天可能有多条（白天/夜间），按 fxDate 归到一个 list 里
                weatherCache.computeIfAbsent(d.fxDate(), k -> new java.util.ArrayList<>()).add(d);
            }
            weatherDate = today;
        }
        return weatherCache;
    }

    /** 取该盆栽绑定设备的最新一轮读数（每项指标取最近一条，SQL 见 DeviceMapper.selectLatestByDevice）。 */
    private Map<String, Object> latestValues(Plant p) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (p.getDeviceId() == null) {
            return map;
        }
        for (SensorData sd : deviceMapper.selectLatestByDevice(p.getDeviceId())) {   // 【缺指标跳过】
            map.put(sd.getMetric(), sd.getValue());
        }
        return map;
    }

    /** 从读数 Map 里取某项指标的 double 值；没有这项指标（返回 null）时调用方一律跳过该条判定 */
    private Double val(Map<String, Object> latest, String metric) {
        Object v = latest.get(metric);
        return v == null ? null : ((BigDecimal) v).doubleValue();
    }

    /** 写提醒并推送（同标题 pending 去重）。 */
    private boolean fire(Plant p, String type, String title, String content, String priority) {
        Long pending = reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()   // 【追问·建议重复】
                .eq(Reminder::getType, type).eq(Reminder::getTitle, title)
                .eq(Reminder::getStatus, "pending"));
        if (pending != null && pending > 0) {
            return false;   // 同一条提醒还没被处理/取消，不重复生成、不重复推送
        }
        Reminder r = new Reminder();
        r.setTitle(title);
        r.setType(type);                 // plant_advice（种植建议）或 plant_pest（病虫害预防）
        r.setContent(content);
        r.setUserId(p.getUserId());      // 归属盆栽主人 → 只有主人能在列表里看到
        r.setPlantId(p.getId());         // 关联盆栽 → 植物详情抽屉里能按植物筛出来
        r.setRemindTime(LocalDateTime.now());
        r.setRepeatType("none");         // 建议类提醒都是一次性事件，不参与每日重复重置
        r.setPriority(priority);
        r.setStatus("pending");
        // 来源文案按类型区分，前端列表的"来源"列直接展示这个字符串
        r.setSource(type.equals("plant_pest") ? "病虫害预防引擎" : "种植建议引擎");
        reminderMapper.insert(r);
        notificationService.sendToUser(p.getUserId(), type, title, content);
        return true;
    }

    /** ① 土壤/温度/光照/空气湿度 适宜度判定 —— 拿"实时读数"和"品种适宜区间"逐项比对。 */
    private int advise(Plant p, PlantSpecies sp, Map<String, Object> latest) {
        int n = 0;
        // ---- 土壤湿度 ----
        Double soil = val(latest, "soil_humidity");   // 【四项检查】
        if (soil != null && sp.getSoilMin() != null) {
            if (soil < sp.getSoilMin()) {
                if (fire(p, "plant_advice",
                        p.getName() + "·土壤偏干",
                        String.format("%s 当前土壤湿度 %.0f%%，低于 %s 适宜下限 %d%%。建议尽快浇水，浇透至盆底出水即可。", p.getName(), soil, sp.getName(), sp.getSoilMin()),
                        "urgent")) {
                    n++;
                }
            } else if (soil > sp.getSoilMax() + 10) {   // +10：刚过上限不算过湿，超出一截才提醒，避免临界反复告警
                if (fire(p, "plant_advice",
                        p.getName() + "·土壤过湿",
                        String.format("%s 当前土壤湿度 %.0f%%，高于 %s 适宜上限 %d%%。暂缓浇水，注意通风排水，防止闷根烂根。", p.getName(), soil, sp.getName(), sp.getSoilMax()),
                        "important")) {
                    n++;
                }
            }
        }
        // ---- 气温（同样两条规则互斥） ----
        Double temp = val(latest, "air_temp");
        if (temp != null && sp.getTempMin() != null) {
            if (temp > sp.getTempMax()) {
                if (fire(p, "plant_advice",
                        p.getName() + "·温度偏高",
                        String.format("当前气温 %.0f℃，高于 %s 适宜上限 %d℃。注意通风遮阳，避免暴晒灼伤。", temp, sp.getName(), sp.getTempMax()),
                        "important")) {
                    n++;
                }
            } else if (temp < sp.getTempMin()) {
                if (fire(p, "plant_advice",
                        p.getName() + "·温度偏低",
                        String.format("当前气温 %.0f℃，低于 %s 适宜下限 %d℃。注意保暖，移至室内温暖处，避免冷风直吹。", temp, sp.getName(), sp.getTempMin()),
                        "important")) {
                    n++;
                }
            }
        }
        // ---- 光照（2000 lux 是"室内靠窗但明显偏暗"的量级；品种标注为"耐阴"的直接豁免） ----
        Double light = val(latest, "light");
        if (light != null && light < 2000 && !"耐阴".equals(sp.getLightLevel())) {
            if (fire(p, "plant_advice",
                    p.getName() + "·光照不足",
                    String.format("%s 为%s植物，当前光照仅 %.0f lux，明显不足。请移到靠近窗户的明亮处，每天保证 3-4 小时光照。", sp.getName(), sp.getLightLevel(), light),
                    "important")) {
                n++;
            }
        }
        // ---- 空气湿度（30% 是多数观叶植物叶片开始明显失水的干燥线；这条只提醒、不紧急） ----
        Double hum = val(latest, "air_humidity");
        if (hum != null && hum < 30) {
            if (fire(p, "plant_advice",
                    p.getName() + "·空气干燥",
                    String.format("当前空气湿度仅 %.0f%%，%s 叶片易失水。可向叶面喷水或放置加湿器，提高周围空气湿度。", hum, sp.getName()),
                    "normal")) {
                n++;
            }
        }
        return n;   // 返回本次这一盆新增的建议条数（0~4 条）
    }

    /** ② 季节 + 品种易发病虫害 → 预防提醒（近 7 天同标题不重复）。 */
    private int pestPrevention(Plant p, PlantSpecies sp) {
        if (sp.getDiseases() == null || sp.getDiseases().isBlank()) {
            return 0;   // 该品种没登记易感病害，跳过
        }
        String season = currentSeason();
        int n = 0;
        for (String dn : sp.getDiseases().split(",")) {   // diseases 是逗号分隔的字符串，逐个拆开
            String name = dn.trim();
            if (name.isEmpty()) {
                continue;
            }
            // 病害库里按"名字相等 + season 字段含当前季节"筛（like → season 字段里写"春夏"也能被"春"命中）
            List<PlantDisease> hits = diseaseMapper.selectList(new LambdaQueryWrapper<PlantDisease>()   // 【季节性病虫害】
                    .eq(PlantDisease::getName, name).like(PlantDisease::getSeason, season));
            for (PlantDisease d : hits) {
                // 7 天防重：最近 7 天内已经发过同标题的预防提醒就不再发（注意这里查的是 create_time，
                Long recent = reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getType, "plant_pest")
                        .eq(Reminder::getTitle, p.getName() + "·" + d.getName() + "预防")
                        .ge(Reminder::getCreateTime, LocalDateTime.now().minusDays(7)));
                if (recent != null && recent > 0) {
                    continue;
                }
                // 主语：植物名默认是按品种预填的（「薄荷」的品种也叫「薄荷」），拼成「薄荷 的 薄荷」读着绕；
                String subject = p.getName().equals(sp.getName())
                        ? p.getName()
                        : p.getName() + "（" + sp.getName() + "）";
                // 季节：一是 season 是单字（春/夏/秋/冬，见 currentSeason），要补一个"季"字才成词
                String ds = d.getSeason() == null ? "" : d.getSeason();
                String seasonText = (ds.equals(season) || ds.equals(season + "季"))
                        ? season + "季"
                        : season + "季（" + ds + "）";
                String content = String.format("%s 在%s易发%s。症状：%s。预防：%s。推荐用药：%s。",
                        subject, seasonText, d.getName(), d.getSymptom(), d.getPrevention(), d.getDrug());
                if (fire(p, "plant_pest", p.getName() + "·" + d.getName() + "预防", content, "normal")) {
                    n++;
                }
            }
        }
        return n;
    }

    /** ③ 明天有雨 → 提示浇水可酌情暂缓（保守策略：土壤已跌破品种下限时，改提示先少量补水）。 */
    private int rainAdvice(Plant p, PlantSpecies sp, Map<String, Object> latest, Map<String, List<WeatherService.DayWeather>> forecast) {
        String tomorrow = LocalDate.now().plusDays(1).format(YMD);   // 【下雨不浇】
        List<WeatherService.DayWeather> list = forecast.get(tomorrow);   // 按日期字符串取"明天"那组预报
        if (list == null || list.isEmpty()) {
            return 0;
        }
        String text = list.get(0).textDay();   // 同一天可能有多条，取第一条的白天天气文字
        if (text == null || (!text.contains("雨") && !text.contains("雪"))) {
            return 0;   // 不下雨不下雪，这条建议不成立
        }
        String head = String.format("天气预报：明天 %s（%d/%d℃）。", text, list.get(0).tempMax(), list.get(0).tempMin());
        // 保守策略：雨天只是「通常不用浇」，不是「一定不能浇」——土壤若已跌破品种下限，
        Double soil = val(latest, "soil_humidity");
        String content;
        if (soil != null && sp.getSoilMin() != null && soil < sp.getSoilMin()) {
            content = head + String.format("虽然明天有雨，但当前土壤湿度 %.0f%% 已低于 %s 适宜下限 %d%%，建议先少量补水，同时注意排水防涝。",
                    soil, sp.getName(), sp.getSoilMin());
        } else {
            content = head + "雨天空气湿度大，浇水可酌情暂缓；若盆土已偏干，仍建议少量补水，并注意排水防涝。";
        }
        if (fire(p, "plant_advice", p.getName() + "·明日有雨", content, "normal")) {
            return 1;
        }
        return 0;   // 已存在同标题未处理提醒（fire 返回 false），本次不算新增
    }

    /** ④ 条件恢复自动取消（pending → cancelled）—— 闭环的最后一环： */
    private int recoveryCheck(Plant p, PlantSpecies sp, Map<String, Object> latest) {
        // 只捞这盆植物还没处理的种植建议（标题前缀是"盆栽名·"），缩小遍历范围
        List<Reminder> pending = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getType, "plant_advice")
                .eq(Reminder::getStatus, "pending")
                .like(Reminder::getTitle, p.getName() + "·"));
        int n = 0;
        Double soil = val(latest, "soil_humidity");   // 循环外取一次，四条规则共用
        for (Reminder r : pending) {
            boolean recover = false;
            // 用"标题后缀"区分是哪种建议（标题由 fire 生成，后缀固定），再各自比对恢复条件
            if (r.getTitle().endsWith("土壤偏干") && soil != null && sp.getSoilMin() != null && soil >= sp.getSoilMin() + 5) {   // 【好转自动撤】
                recover = true;   // +5：要比触发线高出 5 个百分点才算真的缓过来了
            } else if (r.getTitle().endsWith("土壤过湿") && soil != null && sp.getSoilMax() != null && soil <= sp.getSoilMax()) {
                recover = true;   // 回落到品种上限以内即恢复
            } else if (r.getTitle().endsWith("温度偏高") && val(latest, "air_temp") != null && sp.getTempMax() != null && val(latest, "air_temp") <= sp.getTempMax()) {
                recover = true;
            } else if (r.getTitle().endsWith("温度偏低") && val(latest, "air_temp") != null && sp.getTempMin() != null && val(latest, "air_temp") >= sp.getTempMin()) {
                recover = true;
            }
            if (recover) {
                r.setStatus("cancelled");   // 系统自动取消（不是用户点完成）
                reminderMapper.updateById(r);
                n++;
            }
        }
        return n;
    }

    /** 当前季节：按月份粗分，3-5 春、6-8 夏、9-11 秋、12-2 冬（用于匹配病虫害的高发季节） */
    private String currentSeason() {
        int m = LocalDate.now().getMonthValue();
        if (m >= 3 && m <= 5) return "春";
        if (m >= 6 && m <= 8) return "夏";
        if (m >= 9 && m <= 11) return "秋";
        return "冬";
    }
}
