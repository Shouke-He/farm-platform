package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Reminder;
import com.farm.platform.entity.SysUser;
import com.farm.platform.mapper.ReminderMapper;
import com.farm.platform.mapper.SysUserMapper;
import com.farm.platform.ws.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** 天气预警调度器：每 2 小时拉取 3 天预报，命中恶劣天气（强降水/高温/低温/大风/降雪） */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherWarnScheduler {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");   // 解析接口返回的日期
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M月d日");          // 标题/正文里的"8月19日"

    /** 默认区域：南宁（sys_user.city 为空视为南宁，与查询层一致） */
    private static final String DEFAULT_CITY = "101300101";

    private final WeatherService weatherService;
    private final ReminderMapper reminderMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;

    @Value("${farm.weather.cities:101300101}")   // 城市 ID，逗号分隔
    private String cities;

    /** 恶劣天气关键词 → 预警类型（二维数组，一行 = 一对 {天气文字里出现的关键词, 内部类型码}）。 */
    private static final String[][] RAIN_KEYS = {
            {"暴雨", "strong_rain"}, {"大雨", "heavy_rain"}, {"中雨", "rain"},
            {"雷阵雨", "thunderstorm"}, {"台风", "typhoon"}, {"冰雹", "hail"}, {"雪", "snow"}
    };

    /** 扫描入口：把配置里的城市列表（逗号分隔，如 "101300101,101300301,101300501"）拆开， */
    @Scheduled(fixedDelay = 2 * 3600_000L, initialDelay = 60_000)   // 【2小时一轮】
    public void scan() {
        purgeExpired();   // 先清掉已过期的旧预警，再扫新的（见方法注释）
        for (String city : Arrays.stream(cities.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
            scanCity(city);
        }
    }

    /** 清理过期天气预警（物理删除）。 */
    private void purgeExpired() {
        try {
            int n = reminderMapper.delete(new LambdaQueryWrapper<Reminder>()
                    .eq(Reminder::getType, "weather")
                    .eq(Reminder::getStatus, "pending")
                    .lt(Reminder::getRemindTime, LocalDate.now().atStartOfDay()));
            if (n > 0) {
                log.info("清理过期天气预警 {} 条", n);
            }
        } catch (Exception e) {
            // 清理失败不能连累本轮预警生成：单独兜住，记一条 WARN 即可
            log.warn("清理过期天气预警失败：{}", e.getMessage());
        }
    }

    /** 单个城市：拉 3 天预报 → 命中恶劣天气落库（同日同类型同城去重）。 */
    private void scanCity(String cityId) {
        log.info("天气预警扫描开始: {}", cityId);
        List<WeatherService.DayWeather> days = weatherService.forecast3d(cityId);
        log.info("天气预警扫描（{}）：拉到 {} 天数据", cityId, days.size());
        if (days.isEmpty()) {
            return;   // 拉不到预报（网络/配额/城市ID错）就直接跳过，scan 会继续扫下一个城市
        }
        int created = 0;
        for (WeatherService.DayWeather d : days) {   // 3 天预报 = 循环 3 次
            for (String[] rule : RAIN_KEYS) {   // 【恶劣天气判据】
                if (d.textDay().contains(rule[0])) {
                    created += saveWarn(d, rule[1], rule[0], cityId);
                }
            }
            if (d.tempMax() >= 35) {   // 35℃：作物蒸腾急剧加剧、人也容易中暑的高温线
                created += saveWarn(d, "heat", "高温", cityId);
            }
            if (d.tempMin() <= 5) {    // 5℃：多数露地作物开始受冻害威胁的低温线
                created += saveWarn(d, "cold", "低温", cityId);
            }
            if (d.windScaleDay() >= 6) {   // 6 级风：喷药会飘散、大棚易受损
                created += saveWarn(d, "gale", "大风", cityId);
            }
        }
        if (created > 0) {
            log.info("天气预警扫描（{}）完成，新增 {} 条", cityId, created);
        }
    }

    /** 落库 + 广播；同日同类型同城已存在则跳过。 */
    private int saveWarn(WeatherService.DayWeather d, String type, String keyWord, String cityId) {
        LocalDate date = LocalDate.parse(d.fxDate(), YMD);
        // 标题带城市名，避免多城市预警无法区分（如「8月19日 南宁市 中雨预警」）
        String title = MD.format(date) + " " + WeatherService.cityName(cityId) + " " + keyWord + "预警";
        Long exists = reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()   // 【同城去重】
                .eq(Reminder::getType, "weather")
                .eq(Reminder::getTitle, title)
                .eq(Reminder::getCity, cityId));
        if (exists != null && exists > 0) {
            return 0;   // 这条预警已经生成过了（不限状态：完成后也不重复生成）
        }
        String content = buildContent(d, type, keyWord);   // 按天气类型拼对应的农事应对建议
        Reminder r = new Reminder();
        r.setTitle(title);
        r.setType("weather");
        r.setContent(content);
        r.setRemindTime(date.atTime(8, 0));   // 提醒时间落在"预警当天早上 8 点"，当天一早就出现在清单里
        r.setRepeatType("none");              // 一次性提醒，不参与每日重复重置
        // 优先级：暴雨/台风最紧急（urgent），其余恶劣天气给 high
        r.setPriority("strong_rain".equals(type) || "typhoon".equals(type) ? "urgent" : "high");
        r.setStatus("pending");
        r.setSource("天气预警引擎");
        r.setCity(cityId); // 覆盖区域=扫描中的城市
        reminderMapper.insert(r);
        // 定向推送：只推给该城市用户（sys_user.city=该城市，或 city 为空默认南宁）
        pushToCity(cityId, title, content);
        return 1;
    }

    /** 按城市定向推送天气预警：不推给其他区域的在线用户（南宁的暴雨对柳州用户没有意义）。 */
    private void pushToCity(String cityId, String title, String content) {   // 【按城市推】
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>().eq(SysUser::getCity, cityId);
        if (DEFAULT_CITY.equals(cityId)) {
            w.or().isNull(SysUser::getCity); // 南宁天气也推给未设置区域（默认南宁）的用户
        }
        int pushed = 0;
        for (SysUser u : sysUserMapper.selectList(w)) {
            notificationService.sendToUser(u.getId(), "weather", title, content);
            pushed++;
        }
        if (pushed > 0) {
            // pushed 是"匹配到该城市的用户数"；能否即时送达取决于对方当时是否在线，见 NotificationService.sendToUser
            log.info("天气预警推送完成（{}）：{} 个用户在线接收", cityId, pushed);
        }
    }

    /** 拼提醒正文：前半段是"哪天什么天气、温度风力多少"，后半段按预警类型给一条具体的农事应对建议。 */
    private String buildContent(WeatherService.DayWeather d, String type, String keyWord) {
        StringBuilder sb = new StringBuilder();
        // 前半段：把预报事实摆出来，如「8月19日预报：中雨，最高温31℃，最低温25℃，风力3级。」
        sb.append(MD.format(LocalDate.parse(d.fxDate(), YMD))).append("预报：").append(d.textDay())
                .append("，最高温").append(d.tempMax()).append("℃，最低温").append(d.tempMin()).append("℃，风力").append(d.windScaleDay()).append("级");
        sb.append("。");
        // 后半段：按预警类型给可执行的农事建议
        switch (type) {
            case "strong_rain", "heavy_rain", "rain", "thunderstorm" ->
                    sb.append("请暂停露天作业，检查排水沟渠，保护农机设备，注意防雷。");
            case "typhoon" -> sb.append("台风过境，暂停一切户外作业，加固大棚与设施。");
            case "snow" -> sb.append("降雪降温，及时清除棚顶积雪，做好防寒保暖。");
            case "heat" -> sb.append("高温作业请避开正午时段，合理补水，预防中暑。");
            case "cold" -> sb.append("低温注意防霜冻，幼嫩作物加盖保温膜。");
            case "gale" -> sb.append("大风天气请勿进行喷药作业，检查大棚固定。");
            default -> sb.append("请注意农事作业安全。");
        }
        return sb.toString();
    }
}
