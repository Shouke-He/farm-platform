package com.farm.platform.service;

import com.farm.platform.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** sensor_data 定时清理：每天 03:30 删除 keep-days 天前的历史遥测数据 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataCleanScheduler {

    private final DeviceMapper deviceMapper;

    @Value("${farm.sensor.keep-days:30}")   // 保留天数
    private int keepDays;

    /** 清理入口：删除保留期之前的历史遥测。 */
    @Scheduled(cron = "0 30 3 * * ?")   // 【数据量】
    public void clean() {
        LocalDateTime before = LocalDateTime.now().minusDays(keepDays);   // 保留线：早于这条线的数据全部删除
        int n = deviceMapper.deleteBefore(before);
        if (n > 0) {
            log.info("清理 sensor_data {} 条（{} 天前）", n, keepDays);
        }
    }
}
