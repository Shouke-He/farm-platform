package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Device;
import com.farm.platform.entity.SensorData;
import com.farm.platform.mapper.DeviceMapper;
import com.farm.platform.mapper.SensorDataMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 遥测处理：MQTT 回调 → 设备上下线 → 三重净化入库 → 交告警引擎；全设备数据的唯一入口 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotTelemetryService {

    /** 报文里 "t" 字段（采集时间）的格式，固定 yyyy-MM-dd HH:mm:ss */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 合法指标清单（防脏数据灌库）：室内无雨量计，rainfall 已下线换成 rssi */
    static final Set<String> METRICS = Set.of(
            "soil_humidity", "soil_temp", "air_temp", "air_humidity", "light", "rssi");

    /** 在线判定窗口（秒）：上报周期 5 秒，30 秒 = 容忍连丢五次；三处必须共用此值，否则状态会抖动 */
    // 2026-11-14 从 15s 放宽到 30s：15s 只容忍连丢两次，设备一旦网络抖动两次就被误判离线
    public static final int ONLINE_WINDOW_SECONDS = 30;   // 【三条离线线】

    /** 各指标合法量程 [下限, 上限] 闭区间；越界多半是探针脱落/ADC 触顶，原样入库会污染曲线并误判告警 */
    private static final Map<String, BigDecimal[]> RANGES = Map.of(
            "soil_humidity", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("100")},
            "soil_temp", new BigDecimal[]{new BigDecimal("-20"), new BigDecimal("60")},
            "air_temp", new BigDecimal[]{new BigDecimal("-40"), new BigDecimal("80")},
            "air_humidity", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("100")},
            "light", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("100000")},
            "rssi", new BigDecimal[]{new BigDecimal("-127"), BigDecimal.ZERO});

    private final DeviceMapper deviceMapper;
    private final SensorDataMapper sensorDataMapper;
    private final SensorAlertEngine alertEngine;
    private final ObjectMapper objectMapper;

    /** 最近上报设备表（内存，含未注册设备）：供「发现设备」用，见 GET /api/devices/discover */
    public static final Map<String, LocalDateTime> RECENT_DEVICES = new ConcurrentHashMap<>();

    /** 处理一条遥测：四关 —— ① 是否已注册；② 模拟器还是真硬件；③ 指标是否合法（白名单+数字+量程）； */
    public void handle(String deviceNo, String payload) {
        // 无论是否注册都登记（发现设备用）
        RECENT_DEVICES.put(deviceNo, LocalDateTime.now());
        // 内存保护：涨到 200 条以上才顺手清一次，超 1 小时没动静的不算"最近活跃"
        if (RECENT_DEVICES.size() > 200) {
            RECENT_DEVICES.entrySet().removeIf(e -> e.getValue().isBefore(LocalDateTime.now().minusHours(1)));
        }
        // 第①关：没注册过的设备直接丢弃
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getDeviceNo, deviceNo));   // 【设备白名单】
        if (device == null) {
            log.warn("收到未注册设备上报: {}", deviceNo);
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            boolean sim = node.has("sim") && node.get("sim").asBoolean();   // 带 sim=true 即模拟器数据
            // 第②关：只有真实上报才驱动在线状态，模拟数据不入此列
            if (!sim) {   // 【标在线】
                if (!"online".equals(device.getStatus())) {
                    device.setStatus("online");
                    log.info("设备上线: {} ({})", device.getDeviceNo(), device.getDeviceName());
                }
                device.setLastOnlineTime(LocalDateTime.now());
                deviceMapper.updateById(device);
            }
            // 采集时间优先用报文里的 "t"，没带就按服务器收到的时间
            LocalDateTime collect = node.has("t")
                    ? LocalDateTime.parse(node.get("t").asText(), TIME_FMT)
                    : LocalDateTime.now();
            // 第③关：三重净化。注意 Jackson 的 has(f) 对 {"f":null} 返回 true 且 decimalValue() 回退为 0，
            ObjectNode valid = objectMapper.createObjectNode();
            node.fieldNames().forEachRemaining(field -> {
                if (!METRICS.contains(field)) {   // 【指标白名单】
                    return;   // 不在白名单：连日志都不打，免得第三方乱塞字段刷日志
                }
                JsonNode v = node.get(field);
                if (v == null || !v.isNumber()) {
                    log.warn("遥测空值/非数值已拒收 device={} metric={} payload={}", deviceNo, field, payload);
                    return;
                }
                BigDecimal value = v.decimalValue();
                BigDecimal[] range = RANGES.get(field);   // range[0]=下限 range[1]=上限
                if (range != null && (value.compareTo(range[0]) < 0 || value.compareTo(range[1]) > 0)) {
                    log.warn("遥测越界已拒收 device={} metric={} value={} 合法区间=[{}, {}] payload={}",
                            deviceNo, field, value, range[0], range[1], payload);
                    return;
                }
                valid.put(field, value);
            });
            // 第④关：每个指标单独一行（长表结构，新增指标不用改表）
            valid.fields().forEachRemaining(e -> {   // 【一项一行】
                SensorData sd = new SensorData();
                sd.setDeviceId(device.getId());
                sd.setMetric(e.getKey());
                sd.setValue(e.getValue().decimalValue());
                sd.setCollectTime(collect);
                sensorDataMapper.insert(sd);
            });
            // 先入库再判告警：保证「能看到告警的人一定也能查到触发它的那条数据」
            alertEngine.evaluate(device, valid);   // 【判告警】
        } catch (Exception e) {
            // 只记日志不抛出：MQTT 回调线程抛异常会中断订阅，一条坏报文不能拖垮整条数据通道
            log.error("遥测数据解析失败 device={} payload={}: {}", deviceNo, payload, e.getMessage());
        }
    }

    /** LWT 遗嘱：设备拔电/断网时 broker 即时广播，这里秒级置离线；重连后下一条遥测会自动置回 online */
    public void markOffline(String deviceNo) {
        try {
            Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getDeviceNo, deviceNo));
            // 幂等：可能已被 OfflineCheckScheduler 判过
            if (device != null && "online".equals(device.getStatus())) {
                device.setStatus("offline");
                deviceMapper.updateById(device);
                log.info("设备离线(LWT): {} ({})", device.getDeviceNo(), device.getDeviceName());
            }
        } catch (Exception e) {
            // 同样只记日志，避免影响 MQTT 订阅线程
            log.error("LWT 处理失败 device={}: {}", deviceNo, e.getMessage());
        }
    }
}
