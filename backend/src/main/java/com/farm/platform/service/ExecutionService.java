package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.platform.common.BusinessException;
import com.farm.platform.common.UserContext;
import com.farm.platform.dto.ExecutionRequest;
import com.farm.platform.entity.ExecutionRecord;
import com.farm.platform.entity.Reminder;
import com.farm.platform.mapper.ExecutionRecordMapper;
import com.farm.platform.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 执行记录服务：用户做完一件农活后登记的「完成凭证」（对应数据库 execution_record 表）。 */
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRecordMapper executionRecordMapper;
    private final ReminderMapper reminderMapper;
    private final ObjectMapper objectMapper;

    /** 类型分组关键词（对应前端快捷任务标题） */
    private static final String[] TYPE_KEYWORDS = {"浇水", "施肥", "打药", "采收", "除草", "松土"};

    /** 校验任务存在且可登记执行记录（custom 类型 + 本人或管理角色）。 */
    private Reminder checkReminder(Long reminderId) {
        Reminder r = checkVisible(reminderId);
        // 只有 custom 类型（用户/管理员自己建的农事任务）才允许登记执行记录，sensor 等类型不行
        if (!"custom".equals(r.getType())) {
            throw new BusinessException("仅农事任务可登记执行记录");
        }
        return r;
    }

    /** 校验任务存在且当前用户可见（本人或管理角色；sensor 等非农事任务也可查看执行记录—— */
    private Reminder checkVisible(Long reminderId) {
        Reminder r = reminderMapper.selectById(reminderId);
        if (r == null) {
            throw new BusinessException("农事任务不存在");
        }
        // roleId 为 null = 不是登录用户发起的（定时任务线程等），一律挡掉
        Long roleId = UserContext.getRoleId();
        // 放行的两种情况：管理员(roleId=1)；或者任务 user_id 为空（全局共享任务）/就是当前用户
        if (roleId == null || (roleId != 1 && r.getUserId() != null && !r.getUserId().equals(UserContext.getUserId()))) {
            throw new BusinessException(403, "无权操作该任务");
        }
        return r;
    }

    /** 登记一条执行记录：先过 checkReminder 校验（任务存在 + 是农事任务 + 有权限）， */
    public ExecutionRecord add(ExecutionRequest req) {
        checkReminder(req.getReminderId());
        ExecutionRecord e = new ExecutionRecord();
        e.setReminderId(req.getReminderId());
        e.setUserId(UserContext.getUserId());
        // 执行时间：前端不填就按"现在就干完了"处理
        e.setExecuteTime(req.getExecuteTime() == null ? LocalDateTime.now() : req.getExecuteTime());
        e.setNote(req.getNote());
        // 农资明细：先过滤掉没填名称的空行，再整段转 JSON 存进 inputs 列（null 存空数组）
        e.setInputs(toJson(req.getInputs() == null ? List.of() : req.getInputs().stream()
                .filter(i -> i.getName() != null && !i.getName().isBlank()).collect(Collectors.toList())));
        // 照片：图片路径数组转 JSON 存进 photos 列
        e.setPhotos(toJson(req.getPhotos() == null ? List.of() : req.getPhotos()));
        e.setCreateTime(LocalDateTime.now());
        executionRecordMapper.insert(e);
        return e;
    }

    /** JSON 序列化（String 列） */
    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception ex) {
            // 序列化不了就当成前端传的数据格式不对，抛业务错误
            throw new BusinessException("农资明细格式不正确");
        }
    }

    /** 某个任务下的全部执行记录，按执行时间倒序（最新的在最上面）。 */
    public List<ExecutionRecord> listByReminder(Long reminderId) {
        checkVisible(reminderId);
        return executionRecordMapper.selectList(new LambdaQueryWrapper<ExecutionRecord>()
                .eq(ExecutionRecord::getReminderId, reminderId)
                .orderByDesc(ExecutionRecord::getExecuteTime));
    }

    /** 执行统计：三个数字全部从 execution_record 表算出来，前端「执行统计」图表直接用。 */
    public Map<String, Object> stats(Long userId) {
        LambdaQueryWrapper<ExecutionRecord> qw = new LambdaQueryWrapper<>();
        if (userId != null) {
            qw.eq(ExecutionRecord::getUserId, userId);
        }
        List<ExecutionRecord> list = executionRecordMapper.selectList(qw);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", list.size());
        // 类型分组：按任务标题关键词（第一个命中即归组）
        Map<Long, String> titles = new HashMap<>();
        if (!list.isEmpty()) {
            reminderMapper.selectBatchIds(list.stream().map(ExecutionRecord::getReminderId).distinct().collect(Collectors.toList()))
                    .forEach(r -> titles.put(r.getId(), r.getTitle()));
        }
        // 每个关键词先占位 0，这样前端图例的顺序和显示是固定的
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (String kw : TYPE_KEYWORDS) {
            byType.put(kw, 0);
        }
        byType.put("其他", 0);
        for (ExecutionRecord e : list) {
            String title = titles.get(e.getReminderId());
            boolean hit = false;
            if (title != null) {
                for (String kw : TYPE_KEYWORDS) {
                    // 从「浇水」开始依次找，标题里第一个出现的算这组，命中就跳出不再看后面的关键词
                    if (title.contains(kw)) {
                        byType.put(kw, byType.get(kw) + 1);
                        hit = true;
                        break;
                    }
                }
            }
            // 标题查不到（对应任务已被删）或没命中任何关键词 → 统一归「其他」
            if (!hit) {
                byType.put("其他", byType.get("其他") + 1);
            }
        }
        // 只把数量大于 0 的分组返回给前端，免得图表里出现一堆空柱子
        out.put("byType", byType.entrySet().stream()
                .filter(en -> en.getValue() > 0)
                .map(en -> Map.of("type", en.getKey(), "count", en.getValue()))
                .collect(Collectors.toList()));
        // 最近 5 条
        List<Map<String, Object>> recent = new ArrayList<>();
        // 取最新 5 条：按执行时间倒序排完只留前 5 条
        for (ExecutionRecord e : list.stream()
                .sorted(Comparator.comparing(ExecutionRecord::getExecuteTime).reversed())
                .limit(5).collect(Collectors.toList())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("title", titles.get(e.getReminderId()));
            m.put("executeTime", e.getExecuteTime());
            m.put("note", e.getNote());
            recent.add(m);
        }
        out.put("recent", recent);
        return out;
    }

    /** 删除一条执行记录。规则同上面的权限口径：管理员可删任意一条， */
    public void delete(Long id) {
        ExecutionRecord e = executionRecordMapper.selectById(id);
        if (e == null) {
            throw new BusinessException("执行记录不存在");
        }
        // 管理员(roleId=1)放行；否则要求记录的 user_id 就是当前登录人
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !e.getUserId().equals(UserContext.getUserId()))) {
            throw new BusinessException(403, "无权删除该记录");
        }
        executionRecordMapper.deleteById(id);
    }
}
