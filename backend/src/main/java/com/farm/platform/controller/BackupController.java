package com.farm.platform.controller;

import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 数据库备份：手动触发一次 mysqldump 全量备份，以及查看已有的备份文件列表。 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /** 立即执行一次数据库全量备份，返回备份文件名。 */
    @PostMapping("/run")
    @OpLog(module = "系统", operation = "手动数据库备份")
    public Result<String> run() {   // 【手动备份】
        if (UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可执行备份");
        }
        return Result.ok(backupService.backup());
    }

    /** 备份文件列表（文件名 / 大小 / 生成时间，按时间倒序）。 */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        if (UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可查看备份");
        }
        return Result.ok(backupService.list());
    }
}
