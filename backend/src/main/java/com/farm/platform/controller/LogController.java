package com.farm.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.OperationLog;
import com.farm.platform.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作日志：分页查询 + 导出 CSV。 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /** 操作日志分页查询：支持按模块、关键字（模糊匹配）、执行结果筛选，页码和每页条数有默认值。 */
    @GetMapping
    public Result<Page<OperationLog>> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String module,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        if (UserContext.getRoleId() != 1) {   // 【只管理员导出】
            return Result.error(403, "仅管理员可查看操作日志");
        }
        return Result.ok(logService.page(page, size, module, keyword, status));
    }

    /** 导出 CSV：把当前筛选条件下的全部日志（不分页）拼成一段 CSV 文本返回，前端自己落成文件下载。 */
    @GetMapping("/export")
    @OpLog(module = "系统", operation = "导出操作日志")
    public Result<String> export(@RequestParam(required = false) String module,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String status) {
        if (UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可导出操作日志");
        }
        return Result.ok(logService.exportCsv(module, keyword, status));
    }
}
