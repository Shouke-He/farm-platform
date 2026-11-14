package com.farm.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.Reminder;
import com.farm.platform.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.farm.platform.common.BusinessException;
import java.util.List;
import java.util.Map;

/** 提醒 / 农事任务：分页列表、创建、修改、删除、状态流转、处理统计、完成记录、某植物的系统推送。 */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    /** 提醒分页列表，可按类型 type 筛选（农事任务 custom / 设备告警 sensor / 天气 weather 等）。 */
    @GetMapping
    public Result<Page<Reminder>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String type) {
        Long roleId = UserContext.getRoleId();
        Long userId = (roleId != null && roleId == 1) ? null : UserContext.getUserId();
        return Result.ok(reminderService.page(page, size, type, userId));
    }

    /** 新建提醒：只允许人工建 custom（农事任务）类型，天气/环境告警等系统类型由对应引擎自动生成。 */
    @PostMapping   // 【两道拦截】
    @OpLog(module = "提醒", operation = "创建提醒")
    public Result<Reminder> create(@RequestBody Reminder reminder) {
        Long roleId = UserContext.getRoleId();
        if (roleId != null && roleId == 1) {
            return Result.error(403, "管理员不发布农事任务，任务由种植用户自主设置");
        }
        // 人工创建仅允许农事任务；天气/环境告警等系统类型由对应引擎自动生成
        if (reminder.getType() != null && !"custom".equals(reminder.getType())) {
            return Result.error(400, "任务类型仅支持农事任务（custom），其余类型由系统自动生成");
        }
        // 前端表单不带归属，这里兜底记在当前登录用户名下：归属为空的任务收不到到点提醒，还会被当成全局任务给所有用户看（2026-10-15）
        if (reminder.getUserId() == null) {
            reminder.setUserId(UserContext.getUserId());
        }
        return Result.ok(reminderService.create(reminder));
    }

    /** 修改提醒：同样只放行 custom 类型，系统推送类任务不允许人工改。 */
    @PutMapping
    @OpLog(module = "提醒", operation = "修改提醒")
    public Result<Void> update(@RequestBody Reminder reminder) {
        // 人工修改仅限农事任务（custom）；其余类型（天气/环境告警/种植建议/虫害预防）由对应引擎自动生成，不允许人工改
        if (reminder.getType() != null && !"custom".equals(reminder.getType())) {
            return Result.error(400, "系统推送类任务不可人工修改（天气/告警/建议等由系统自动生成）");
        }
        reminderService.update(reminder);
        return Result.ok();
    }

    /** 删除提醒：按路径上的 id 直删。 */
    @DeleteMapping("/{id}")
    @OpLog(module = "提醒", operation = "删除提醒")
    public Result<Void> delete(@PathVariable Long id) {
        reminderService.delete(id);
        return Result.ok();
    }

    /** 提醒处理统计：总数、待办数、处理率、按来源分组的条数。 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long roleId = UserContext.getRoleId();
        Long userId = (roleId != null && roleId == 1) ? null : UserContext.getUserId();
        return Result.ok(reminderService.reminderStats(userId));
    }

    /** 近期完成记录：已完成的农事任务 + 关联任务已被删的孤儿执行记录，两类合并按时间倒序取 limit 条。 */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam(defaultValue = "10") int limit,
                                                     @RequestParam(required = false) Long plantId,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) Long ownerId) {
        Long roleId = UserContext.getRoleId();
        if (ownerId != null && (roleId == null || roleId != 1)) {
            throw new BusinessException(403, "仅管理员可按用户查看完成记录");
        }
        Long userId = (roleId != null && roleId == 1) ? null : UserContext.getUserId();
        return Result.ok(reminderService.history(limit, userId, plantId, type, ownerId));
    }

    /** 某株植物的系统推送：种植建议、虫害预防（白名单里还留着预留类型 growth，当前没有生成器）。 */
    @GetMapping("/plant/{plantId}")
    public Result<List<Reminder>> plantReminders(@PathVariable Long plantId) {
        return Result.ok(reminderService.plantReminders(plantId));
    }

    /** 完成 / 取消提醒：查询参数 status 传 done 表示完成（记完成时间并自动补一条执行记录）， */
    @PutMapping("/{id}/status")
    @OpLog(module = "提醒", operation = "提醒状态变更")
    public Result<Void> status(@PathVariable Long id, @RequestParam String status) {
        reminderService.changeStatus(id, status);
        return Result.ok();
    }
}
