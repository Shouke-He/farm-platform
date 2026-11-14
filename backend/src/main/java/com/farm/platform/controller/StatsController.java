package com.farm.platform.controller;

import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 统计看板聚合接口：一次请求把四块数据全给前端 —— 提醒处理情况、设备在线率、 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 看板总览：一次请求拿全四块聚合数据（用 Map 返回，不单独定义 DTO）。 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        if (UserContext.getRoleId() == null || UserContext.getRoleId() != 1) {   // 【只管理员】
            return Result.error(403, "仅管理员可查看统计看板");
        }
        return Result.ok(statsService.overview());
    }
}
