package com.farm.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日全量备份：凌晨 03:00 触发一次，只写日志、不打扰用户（备份与清理由 BackupService 做） */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;

    /** 每天 03:00；失败只记 error 不抛出，免得调度线程刷堆栈 */
    // cron 六位依次是 秒 分 时 日 月 周
    @Scheduled(cron = "0 0 3 * * ?")   // 【每天3点】
    public void dailyBackup() {
        try {
            String file = backupService.backup();
            log.info("每日备份完成：{}", file);
        } catch (Exception e) {
            log.error("每日备份失败：{}", e.getMessage());
        }
    }
}
