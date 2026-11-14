package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farm.platform.entity.OperationLog;
import com.farm.platform.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 操作日志服务：管理端「操作日志」页面的数据管家（对应数据库 operation_log 表）。 */
@Service
@RequiredArgsConstructor
public class LogService {

    // 导出 CSV 时的时间格式
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationLogMapper logMapper;

    /** 异步写日志，不阻塞业务请求 */
    @Async   // 【异步不拖累】
    public void save(OperationLog log) {
        try {
            if (log.getCreateTime() == null) {
                log.setCreateTime(LocalDateTime.now());
            }
            logMapper.insert(log);
        } catch (Exception ignored) {   // 【追问·日志失败】
            // 日志失败不影响业务
        }
    }

    /** 分页查询与导出共用的筛选条件（模块/结果/关键字模糊），保证两处口径一致 */
    private LambdaQueryWrapper<OperationLog> buildQw(String module, String keyword, String status) {
        // 固定按操作时间倒序（新日志在最上面）
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreateTime);
        // 三个筛选条件都是"传了才生效"，空值表示不筛
        if (module != null && !module.isBlank()) {
            qw.eq(OperationLog::getModule, module);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(OperationLog::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 关键字在 操作名/账号/姓名 三列里任一命中即可；用 and(...) 把三个 or 括成一组，
            qw.and(w -> w.like(OperationLog::getOperation, keyword)
                    .or().like(OperationLog::getUsername, keyword)
                    .or().like(OperationLog::getRealName, keyword));
        }
        return qw;
    }

    /** 操作日志分页查询：page 第几页（从 1 开始）、size 每页几条，后三个是筛选条件（可为空）。 */
    public Page<OperationLog> page(int page, int size, String module, String keyword, String status) {
        return logMapper.selectPage(new Page<>(page, size), buildQw(module, keyword, status));
    }

    /** 导出 CSV（Excel 兼容）：UTF-8 BOM 防中文乱码 + \r\n 行尾 + 逐字段转义（逗号/引号/换行） */
    public String exportCsv(String module, String keyword, String status) {
        // 和分页查询共用同一个 buildQw，所以导出的就是当前筛选条件下的全部记录（不分页）
        List<OperationLog> list = logMapper.selectList(buildQw(module, keyword, status));
        // 开头先拼一个 UTF-8 BOM 字符（字节顺序标记），加了它 Excel 打开中文才不会乱码
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("时间,账号,姓名,角色,模块,操作,方法,URL,IP,耗时(ms),结果,错误信息\r\n");
        for (OperationLog l : list) {
            // 一行一条日志：每个字段先做 CSV 转义再拼，行尾固定用 \r\n（Excel 认这个换行）
            sb.append(esc(l.getCreateTime() == null ? "" : l.getCreateTime().format(FMT))).append(',')
                    .append(esc(l.getUsername())).append(',')
                    .append(esc(l.getRealName())).append(',')
                    .append(esc(l.getRoleName())).append(',')
                    .append(esc(l.getModule())).append(',')
                    .append(esc(l.getOperation())).append(',')
                    .append(esc(l.getMethod())).append(',')
                    .append(esc(l.getUrl())).append(',')
                    .append(esc(l.getIp())).append(',')
                    .append(l.getDurationMs() == null ? "" : l.getDurationMs()).append(',')
                    .append(esc(l.getStatus())).append(',')
                    .append(esc(l.getErrorMsg())).append("\r\n");
        }
        return sb.toString();
    }

    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹，内部引号翻倍 */
    private static String esc(String v) {
        // null 一律输出空串，避免 CSV 里出现字面的 null
        if (v == null) {
            return "";
        }
        // 字段里含逗号、引号或换行会把 CSV 的列/行切乱，必须整体用双引号包起来
        if (v.contains("\"") || v.contains(",") || v.contains("\n") || v.contains("\r")) {
            // 字段内部原有的双引号按 CSV 规范写成两个双引号
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
