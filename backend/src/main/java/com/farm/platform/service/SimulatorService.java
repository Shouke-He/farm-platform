package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.config.MqttConfig;
import com.farm.platform.entity.Device;
import com.farm.platform.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 传感器模拟器（软硬解耦：与真实传感器同一 MQTT 协议，演示/演示零硬件运行； */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatorService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");   // 报文里的采集时间格式
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");                 // 只取日期，用于天气缓存

    /** 模拟开关：配置项 farm.simulator.enabled，缺省为 true（演示环境默认开着） */
    @Value("${farm.simulator.enabled:true}")   // 模拟器开关
    private boolean enabled;

    private final DeviceMapper deviceMapper;
    private final MqttConfig mqttConfig;
    private final WeatherService weatherService;

    /** 每设备模拟状态：{土壤湿度, 剩余降雨 tick}。按设备 id 存内存，重启后重新初始化 */
    private final Map<Long, double[]> states = new ConcurrentHashMap<>();

    /** 天气缓存（每日最多拉一次真实预报，省 API 配额） */
    private volatile String cacheDate = "";                     // 已缓存的是哪一天，用于"每天只拉一次"
    private volatile double tempMax = 33, tempMin = 26;         // 缓存的最新/最低温，初值 33/26 是南宁常见夏日区间
    private volatile boolean willRain = false;                  // 今天预报是否有雨（影响光照衰减、是否触发降雨事件）

    /** 模拟上报主循环（每 60 秒一次）。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)   // 每 60 秒
    public void tick() {
        if (!enabled) {
            return;   // 开关关掉就什么都不做（换真硬件时用这个开关停掉模拟）
        }
        loadWeather();
        List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>().eq(Device::getDeviceType, "sensor"));
        for (Device d : devices) {
            // 首次见到这台设备时给个初始土壤湿度 30%~46%（随机），降雨倒计时从 0 开始；
            double[] st = states.computeIfAbsent(d.getId(), id -> new double[]{30 + Math.random() * 16, 0});
            Map<String, BigDecimal> data = model(st);
            // 手工拼 JSON（字段名与真硬件固件完全一致）：{"sim":true,"t":"2026-09-26 10:30:00","soil_humidity":41.2,...}
            StringBuilder payload = new StringBuilder("{\"sim\":true,\"t\":\"");
            payload.append(LocalDateTime.now().format(TIME_FMT)).append('"');
            data.forEach((k, v) -> payload.append(",\"").append(k).append("\":").append(v.toPlainString()));
            payload.append('}');
            mqttConfig.publish("farm/device/" + d.getDeviceNo() + "/telemetry", payload.toString());
        }
    }

    /** 物理模型：根据当前时刻 + 当天真实气温区间 + 这台设备上一 tick 的状态，算出 6 项指标。 */
    private Map<String, BigDecimal> model(double[] st) {
        LocalDateTime now = LocalDateTime.now();
        int h = now.getHour();                 // 当前小时（0~23）
        double minute = now.getMinute() / 60.0; // 分钟折算成小时的小数部分，让曲线平滑推进
        Map<String, BigDecimal> m = new LinkedHashMap<>();   // 有序，报文里字段顺序固定

        // 气温：正弦曲线（峰值 14 点）
        double phase = (h + minute - 8.0) / 24.0 * 2 * Math.PI;
        // 中位值 (最高+最低)/2，振幅 (最高-最低)/2，再用 ±0.5℃ 的随机噪声让它别太"完美"
        double airTemp = (tempMax + tempMin) / 2.0 + (tempMax - tempMin) / 2.0 * Math.sin(phase)
                + (rnd() - 0.5) * 1.0;

        // 空气湿度：与温度负相关（气温越高、相对湿度越低）
        double airHumidity = clamp(86 - (airTemp - tempMin) * 2.4 + (rnd() - 0.5) * 6, 40, 96);

        // 光照：6-19 点正弦，晴阴雨衰减
        double light = 0;
        if (h >= 6 && h < 19) {   // 6 点前 / 19 点后没太阳，光照恒为 0
            // 白天 13 个小时按正弦走：6 点 0 → 12:30 前后最强 → 19 点回 0
            double ratio = Math.sin((h + minute - 6.0) / 13.0 * Math.PI);
            // 天空遮挡系数：下雨天只有 0.2（阴雨昏暗），不下雨在 0.5~1.0 之间随机（晴到多云）
            double sky = willRain ? 0.2 : 0.5 + rnd() * 0.5;
            light = ratio * 80_000 * sky;   // 80_000 lux ≈ 晴天正午的典型照度
        }

        // 土壤湿度：下雨入渗回升 / 晴天蒸发（1 tick ≈ 1 小时）
        double humidity = st[0];
        double rainfall = 0;
        if (st[1] > 0) {
            // 还在降雨：这一 tick 的雨量取 0.5~8.0 mm，按 1.2 的系数折算成湿度百分点抬升，
            rainfall = 0.5 + rnd() * 7.5;
            humidity = Math.min(60, humidity + rainfall * 1.2);
        } else {
            // 不下雨：每个 tick 蒸发 0.4~1.4 个百分点（这就对应类头说的"演示加速"，1 分钟顶 1 小时）
            humidity = humidity - (0.4 + rnd() * 1.0);
            if (humidity < 12) {
                humidity = 12 + rnd() * 4;   // 毛细水兜底：降到 12% 以下时回升到 12~16%，模拟深层土壤毛细水补给
            }
        }
        // 降雨事件：预报有雨时概率触发新降雨（持续 2-6 tick）
        int rainTicks = (int) st[1];
        if (willRain && rnd() < 0.12) {
            // 每次 tick 有 12% 概率下一场新雨，持续 2~6 个 tick（即 2~6 分钟）——
            rainTicks = 2 + (int) (rnd() * 5);
        }
        rainTicks = Math.max(0, rainTicks - 1);   // 本 tick 消耗一次倒计时，减到 0 为止（不会变负数）
        st[0] = humidity;      // 把新状态写回，供下一 tick 继续演进
        st[1] = rainTicks;

        m.put("soil_humidity", dec(humidity));
        // 土壤温度：比气温变化平缓（约 88%）且整体偏暖 2℃——土壤热惯性大，升温降温都慢半拍
        m.put("soil_temp", dec(airTemp * 0.88 + 2));
        m.put("air_temp", dec(airTemp));
        m.put("air_humidity", dec(airHumidity));
        m.put("light", dec(light));
        m.put("rssi", dec(-40 - rnd() * 30));   // 模拟 WiFi 信号强度 dBm（与真实固件一致，雨量已下线）：-40 ~ -70
        return m;
    }

    /** 拉取当天真实天气预报驱动模型（每日一次）。 */
    private void loadWeather() {
        String today = LocalDateTime.now().format(YMD);
        if (!today.equals(cacheDate)) {   // 跨天（或首次运行）才真正去拉一次
            try {
                List<WeatherService.DayWeather> days = weatherService.forecast3d();
                if (!days.isEmpty()) {
                    WeatherService.DayWeather d = days.get(0);   // 第 0 条 = 今天的预报
                    tempMax = d.tempMax();
                    tempMin = d.tempMin();
                    willRain = d.textDay().contains("雨");        // 天气文字里带"雨"就算有雨（含雷阵雨、小雨等）
                    cacheDate = today;                            // 记下已缓存的日期，今天不再拉
                    log.info("模拟器天气驱动: {} {}-{}℃ 降雨={}", d.textDay(), tempMax, tempMin, willRain);
                }
            } catch (Exception e) {
                // 注意：这里失败不写 cacheDate，所以下一分钟还会再试一次
                log.warn("模拟器获取天气失败，沿用缓存: {}", e.getMessage());
            }
        }
    }

    /** 取 [0,1) 随机数的小工具，统一出口方便阅读 */
    private static double rnd() {
        return Math.random();
    }

    /** 把 double 收成 2 位小数的 BigDecimal（先做四舍五入再 setScale，保证入库数值干净） */
    private static BigDecimal dec(double v) {
        return BigDecimal.valueOf(Math.round(v * 100) / 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    /** 区间夹取：把 v 限制在 [lo, hi] 内（先 Math.min 取上限，再 Math.max 取下限） */
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
