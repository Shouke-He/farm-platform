package com.farm.platform.controller;

import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.dto.ExecutionRequest;
import com.farm.platform.entity.ExecutionRecord;
import com.farm.platform.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 执行记录：登记 / 按任务查 / 统计 / 删除（归属校验在 ExecutionService 里做） */
@RestController
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    /** 登记一条执行记录（两端完成弹窗保存时调） */
    @PostMapping("/api/executions")
    @OpLog(module = "执行记录", operation = "登记执行记录")
    public Result<ExecutionRecord> add(@RequestBody ExecutionRequest req) {
        return Result.ok(executionService.add(req));
    }

    /** 某条任务的全部执行记录（弹窗打开时回显之前做过几次） */
    @GetMapping("/api/executions")
    public Result<List<ExecutionRecord>> list(@RequestParam Long reminderId) {
        return Result.ok(executionService.listByReminder(reminderId));
    }

    /** 执行统计：管理员（roleId==1）传 null 走全量，其余只统计自己的 */
    @GetMapping("/api/executions/stats")
    public Result<Map<String, Object>> stats() {
        Long roleId = UserContext.getRoleId();
        Long userId = (roleId != null && roleId == 1) ? null : UserContext.getUserId();
        return Result.ok(executionService.stats(userId));
    }

    /** 删除一条执行记录 */
    @DeleteMapping("/api/executions/{id}")
    @OpLog(module = "执行记录", operation = "删除执行记录")
    public Result<Void> delete(@PathVariable Long id) {
        executionService.delete(id);
        return Result.ok();
    }
}
