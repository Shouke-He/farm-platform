package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Device;
import com.farm.platform.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 设备离线兜底：主通道是 MQTT 遗嘱（秒级），这里覆盖遗嘱丢失的极端场景 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineCheckScheduler {

    private final DeviceMapper deviceMapper;

    /** 每 15 秒扫一次在线设备，超时未上报的降级为离线（窗口 + 间隔 ⇒ 最迟约 30 秒变灰） */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)   // 【三条离线线】
    public void checkOffline() {
        List<Device> online = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getStatus, "online"));   // 只查在线的，正常情况下没几台
        // 窗口值引 IotTelemetryService.ONLINE_WINDOW_SECONDS，与遥测入口、手动刷新三处口径一致
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(IotTelemetryService.ONLINE_WINDOW_SECONDS);
        for (Device d : online) {
            // 从未上报过也判离线（防御性判断）
            if (d.getLastOnlineTime() == null || d.getLastOnlineTime().isBefore(cutoff)) {
                d.setStatus("offline");   // 只降不升：上线只能由真实上报驱动
                deviceMapper.updateById(d);
                log.info("设备离线: {}", d.getDeviceNo());
            }
        }
    }
}
