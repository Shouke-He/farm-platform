package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farm.platform.common.BusinessException;
import com.farm.platform.entity.ExecutionRecord;
import com.farm.platform.entity.Reminder;
import com.farm.platform.entity.SysUser;
import com.farm.platform.mapper.ExecutionRecordMapper;
import com.farm.platform.mapper.ReminderMapper;
import com.farm.platform.mapper.SysUserMapper;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import com.farm.platform.common.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 提醒服务：提醒表 CRUD + 提醒处理统计（个人盆栽建议流 / 天气预警 / 自定义提醒共用） */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    /** 默认区域：南宁（与 application.yml farm.weather.location 一致）；用户没设置城市时按南宁算 */
    private static final String DEFAULT_CITY = "101300101";

    private final ReminderMapper reminderMapper;
    private final SysUserMapper sysUserMapper;
    private final PlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final ExecutionRecordMapper executionRecordMapper;

    /** 分页查提醒 —— 三种口径共用这一个方法，靠入参区分： */
    public Page<Reminder> page(int page, int size, String type, Long userId) {
        LambdaQueryWrapper<Reminder> qw = new LambdaQueryWrapper<Reminder>()
                .orderByDesc(Reminder::getRemindTime);   // 按下发时间倒序
        if (type != null && !type.isBlank()) {
            qw.eq(Reminder::getType, type);   // 显式指定类型（如 sensor）时按该类型查
        }
        // 管理端任务管理视图：不传 type 时只显示农事任务（custom）——weather/sensor/plant_advice/plant_pest 由引擎生成，growth/solar 是预留类型（无生成器）
        if (userId == null && (type == null || type.isBlank())) {
            qw.eq(Reminder::getType, "custom");   // 管理员不传 type 时默认只看农事任务
        }
        applyVisibility(qw, userId);   // 普通用户再叠加"只能看自己的 + 本区域全局项"的可见范围
        Page<Reminder> result = reminderMapper.selectPage(new Page<>(page, size), qw);
        fillPlant(result.getRecords());   // 补齐 plantName / plantEmoji，前端表格直接展示
        return result;
    }

    /** 个人用户可见范围（列表与统计共用）： */
    private void applyVisibility(LambdaQueryWrapper<Reminder> qw, Long userId) {
        if (userId == null) {
            return;   // admin 不限可见范围，直接放行
        }
        // 先查用户档案拿城市（用户没填城市就按默认城市南宁算，与天气预警推送口径一致）
        SysUser u = sysUserMapper.selectById(userId);
        String city = (u != null && u.getCity() != null && !u.getCity().isBlank()) ? u.getCity() : DEFAULT_CITY;
        // 最终 SQL 形状：WHERE ( user_id = 我 OR ( user_id IS NULL AND type <> 'sensor' AND ( city IS NULL OR city = 我的城市 ) ) )
        qw.and(w -> w.eq(Reminder::getUserId, userId)   // 【无主同城可见】   // 【追问·无主提醒】
                .or(q -> q.isNull(Reminder::getUserId).ne(Reminder::getType, "sensor")
                        .and(q2 -> q2.isNull(Reminder::getCity).or().eq(Reminder::getCity, city))));
    }

    /** 校验关联植物存在且归属自己（管理角色可关联任意植物）；plantId 为空=通用任务，直接放行 */
    private void checkPlant(Long plantId) {   // 【植物归属】
        if (plantId == null) {
            return;   // 不关联植物 = 通用农活（浇水、施肥这类），无需校验
        }
        Plant p = plantMapper.selectById(plantId);
        if (p == null) {
            throw new BusinessException(400, "关联的种植物不存在");
        }
        // 角色从 UserContext（请求级 ThreadLocal，由登录拦截器写入）取：roleId=1 是管理员
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !p.getUserId().equals(UserContext.getUserId()))) {
            throw new BusinessException(400, "只能关联自己的种植物");
        }
    }

    /** 新建提醒（人工创建农事任务）。 */
    public Reminder create(Reminder r) {   // 【默认值】
        checkPlant(r.getPlantId());
        if (r.getStatus() == null) {
            r.setStatus("pending");       // 不传状态就默认"待办"
        }
        if (r.getPriority() == null) {
            r.setPriority("normal");      // 不传优先级就默认"普通"
        }
        if (r.getRepeatType() == null) {
            r.setRepeatType("none");      // 不传重复方式就默认"只做一次"
        }
        if ("interval".equals(r.getRepeatType()) && r.getIntervalDays() == null) {
            r.setIntervalDays(1);         // 每 N 天却没带 N（前端漏传/旧数据）→ 当"每天"，别让任务算不出哪天该做
        }
        reminderMapper.insert(r);
        return r;
    }

    /** 修改提醒（前端编辑农事任务）。 */
    public void update(Reminder r) {
        checkPlant(r.getPlantId());
        reminderMapper.updateById(r);
    }

    /** 分页记录批量填充植物名/emoji（plantId 非空才查，避免无关联列表零开销） */
    private void fillPlant(List<Reminder> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 一页 10 条提醒可能挂着好几个植物：先把 plantId 去重收集，再一次性批量查，避免 N+1 查询
        Set<Long> plantIds = list.stream().map(Reminder::getPlantId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (plantIds.isEmpty()) {
            return;   // 这一页一条都没关联植物（纯自定义任务），直接返回
        }
        // 一次查回所有涉及的植物，转成 id → 植物 的 Map 供循环里 O(1) 取用
        Map<Long, Plant> plants = plantMapper.selectBatchIds(plantIds).stream()
                .collect(Collectors.toMap(Plant::getId, p -> p));
        // 再拿这些植物的品种 id，批量查出品种（emoji 存在品种表上，不在植物表上）
        Set<Long> speciesIds = plants.values().stream().map(Plant::getSpeciesId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PlantSpecies> speciesMap = speciesIds.isEmpty() ? Map.of()
                : speciesMapper.selectBatchIds(speciesIds).stream().collect(Collectors.toMap(PlantSpecies::getId, s -> s));
        for (Reminder r : list) {
            Plant p = r.getPlantId() == null ? null : plants.get(r.getPlantId());
            if (p != null) {
                r.setPlantName(p.getName());
                PlantSpecies sp = p.getSpeciesId() == null ? null : speciesMap.get(p.getSpeciesId());
                r.setPlantEmoji(sp == null ? null : sp.getEmoji());   // 植物的 emoji 从它的品种上取
            }
        }
    }

    /** 删除提醒（按主键物理删除）。谁调它：ReminderController.delete（DELETE /api/reminders/{id}） */
    public void delete(Long id) {
        reminderMapper.deleteById(id);
    }

    /** 每日 00:05 重置重复农事任务（repeatType != none 且已完成）为待办—— */
    @Scheduled(cron = "0 5 0 * * *")   // 每天 00:05 重置重复任务
    public void resetRepeatedTasks() {   // 【回到待办】
        // 一条 SQL 批量重置（n = 受影响行数），条件三选二：重复方式≠none、状态=done，然后置为 pending + 清空完成时间
        int n = reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                .ne(Reminder::getRepeatType, "none")
                .eq(Reminder::getStatus, "done")
                .set(Reminder::getStatus, "pending")
                .set(Reminder::getDoneTime, null));
        if (n > 0) {
            log.info("重复农事任务已重置为待办: {} 条", n);
        }
    }

    /** 启动补偿（2026-09-01 修复）：系统停机跨天后 00:05 定点重置会错过——停机期间完成态滞留， */
    @EventListener(ApplicationReadyEvent.class)   // 【回到待办】
    public void resetExpiredOnStartup() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();   // 今天 0 点这条分界线
        // 与定时重置同一套动作，只多一个条件：完成时刻在"今天 0 点之前"（或没记完成时刻）才重置。
        int n = reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                .ne(Reminder::getRepeatType, "none")
                .eq(Reminder::getStatus, "done")
                .and(w -> w.isNull(Reminder::getDoneTime).or().lt(Reminder::getDoneTime, todayStart))
                .set(Reminder::getStatus, "pending")
                .set(Reminder::getDoneTime, null));
        if (n > 0) {
            log.info("启动补偿：重置停机期间未重置的重复农事任务 {} 条", n);
        }
    }

    /** 标记为已完成/已取消；done 记录完成时间，恢复 pending 清空完成时间。 */
    public void changeStatus(Long id, String status) {   // 【完成归档】
        Reminder r = reminderMapper.selectById(id);
        if (r == null) {
            throw new BusinessException("提醒不存在");
        }
        // 入参 status 有三种走法：done（点完成，会补执行记录）、pending（取消勾选，会删掉自动留痕）、
        if ("done".equals(r.getStatus())) {
            if ("done".equals(status)) {
                throw new BusinessException(400, "任务已完成，今日无需重复完成；补充记录请使用「记录」功能");
            }
            if ("pending".equals(status)) {
                throw new BusinessException(400, "已完成的任务不可取消，补充记录请使用「记录」功能");
            }
            // 注意：定时重置器是直接改库的，不经过本方法，所以重复任务次日照样能变回待办
        }
        // 设备告警（sensor）确认处理仅限设备主人：管理员只做平台监控，无法代替用户处理土壤干旱等现实问题
        Long roleId = UserContext.getRoleId();
        if (roleId != null && roleId == 1 && "sensor".equals(r.getType())) {
            throw new BusinessException(403, "管理员只能查看设备告警，确认处理由设备主人执行");
        }
        LocalDateTime now = LocalDateTime.now();
        if ("done".equals(status)) {
            // ---- 走法一：点"完成" ----
            r.setStatus(status);
            r.setDoneTime(now);   // 记下完成时刻，完成记录和次日重置都要用它
            // 重复勾选覆盖（2026-09-01）：当天该任务已存在"自动留痕"（无备注/农资/照片的完成痕迹）
            List<ExecutionRecord> todayAuto = executionRecordMapper.selectList(new LambdaQueryWrapper<ExecutionRecord>()
                    .eq(ExecutionRecord::getReminderId, id)
                    .isNull(ExecutionRecord::getNote)
                    .eq(ExecutionRecord::getInputs, "[]")
                    .and(w -> w.isNull(ExecutionRecord::getPhotos).or().eq(ExecutionRecord::getPhotos, "[]").or().eq(ExecutionRecord::getPhotos, ""))
                    .ge(ExecutionRecord::getExecuteTime, now.toLocalDate().atStartOfDay()));
            if (todayAuto.isEmpty()) {
                // 今天还没有自动留痕 → 再看最近一条执行记录，判断这次完成是不是"弹窗保存"那条链路带来的
                ExecutionRecord latest = executionRecordMapper.selectOne(
                        new LambdaQueryWrapper<ExecutionRecord>()
                                .eq(ExecutionRecord::getReminderId, id)
                                .orderByDesc(ExecutionRecord::getExecuteTime)
                                .last("LIMIT 1"));
                // 防重复：60 秒内刚记过一次就不重复写（用户先在弹窗里填了备注保存，紧接着又点了完成，
                if (latest == null || latest.getExecuteTime().isBefore(now.minusSeconds(60))) {
                    // 自动留痕：只记"谁、什么时候完成的"，备注/农资/照片都留空，
                    ExecutionRecord e = new ExecutionRecord();
                    e.setReminderId(id);
                    e.setUserId(UserContext.getUserId());   // 完成人 = 当前登录用户
                    e.setExecuteTime(now);
                    e.setInputs("[]");                       // 空的农资明细数组（前端按 JSON 解析，给空数组而不是 null）
                    executionRecordMapper.insert(e);
                }
            }
            reminderMapper.updateById(r);   // 最后更新提醒本身的状态和完成时间
        } else if ("pending".equals(status)) {
            // 取消勾选（2026-09-01）：删除本次完成留下的"自动留痕"（空记录）——用户登记的真实记录
            executionRecordMapper.delete(new LambdaQueryWrapper<ExecutionRecord>()
                    .eq(ExecutionRecord::getReminderId, id)
                    .isNull(ExecutionRecord::getNote)
                    .eq(ExecutionRecord::getInputs, "[]")
                    .and(w -> w.isNull(ExecutionRecord::getPhotos).or().eq(ExecutionRecord::getPhotos, "[]").or().eq(ExecutionRecord::getPhotos, ""))
                    .ge(ExecutionRecord::getExecuteTime, now.toLocalDate().atStartOfDay()));
            // 这里必须用 UpdateWrapper 显式 set，而不是 setDoneTime(null) + updateById：
            reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                    .eq(Reminder::getId, id)
                    .set(Reminder::getStatus, "pending")
                    .set(Reminder::getDoneTime, null));
        } else {
            // ---- 走法三：其它状态值（如 cancelled） ----
            r.setStatus(status);
            reminderMapper.updateById(r);
        }
    }

    /** 近期完成记录（农事任务页「完成记录」/植物详情「任务与生长记录」数据源）： */
    public List<Map<String, Object>> history(int limit, Long userId, Long plantId, String type, Long ownerId) {
        List<Map<String, Object>> items = new ArrayList<>();
        // ① 农事任务完成痕迹（2026-09-04 修复）：原仅取 status=done，daily 等重复任务次日 00:05
        LambdaQueryWrapper<Reminder> qw = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) {
            qw.eq(Reminder::getType, type);       // 传了类型就按类型过滤
        }
        if (plantId != null) {
            qw.eq(Reminder::getPlantId, plantId); // 传了植物就只看这盆的
        }
        if (ownerId != null) {
            qw.eq(Reminder::getUserId, ownerId);  // 管理端"按用户查看"
        }
        applyVisibility(qw, userId);
        List<Reminder> doneList = reminderMapper.selectList(qw);
        fillPlant(doneList);   // 先补好植物名/emoji，后面拼每个条目时直接取用
        // 一次查出这些任务的全部执行记录（同任务多次执行逐条展示）
        Map<Long, List<ExecutionRecord>> execMap = new HashMap<>();
        if (!doneList.isEmpty()) {
            List<Long> doneIds = doneList.stream().map(Reminder::getId).collect(Collectors.toList());
            List<ExecutionRecord> execs = executionRecordMapper.selectList(
                    new LambdaQueryWrapper<ExecutionRecord>()
                            .in(ExecutionRecord::getReminderId, doneIds)
                            .orderByDesc(ExecutionRecord::getExecuteTime));
            for (ExecutionRecord e : execs) {
                execMap.computeIfAbsent(e.getReminderId(), k -> new ArrayList<>()).add(e);
            }
        }
        // 遍历任务，按"有没有执行记录"分两种展示方式合并成统一的条目列表
        for (Reminder r : doneList) {
            List<ExecutionRecord> execs = execMap.get(r.getId());
            if (execs != null && !execs.isEmpty()) {
                // 有执行记录：一次执行 = 一条记录（时间取实际执行时间，带备注/农资/照片），不受 status 影响
                for (ExecutionRecord e : execs) {
                    items.add(taskItem(r, e.getExecuteTime(), e.getNote(), e.getInputs(), e.getPhotos(), e.getId()));
                }
            } else if ("done".equals(r.getStatus()) && r.getDoneTime() != null) {
                // 没有执行记录但确实完成过：退回用任务自身的完成时间展示一条（备注/农资/照片均为空）
                items.add(taskItem(r, r.getDoneTime(), null, null, null, null));
            }
        }
        // ② 孤儿执行记录（关联任务已删除，不重复展示任务名，保留农资/备注）（【删任务】）
        LambdaQueryWrapper<ExecutionRecord> eq = new LambdaQueryWrapper<>();
        if (userId != null) {
            eq.eq(ExecutionRecord::getUserId, userId);    // 普通用户只看自己的执行记录
        }
        if (ownerId != null) {
            eq.eq(ExecutionRecord::getUserId, ownerId);   // 管理端按用户筛
        }
        List<ExecutionRecord> execs = executionRecordMapper.selectList(eq);
        if (plantId == null && !execs.isEmpty()) {
            // 指定植物筛选时孤儿记录（无植物信息）整体不返回
            Set<Long> execReminderIds = execs.stream().map(ExecutionRecord::getReminderId).collect(Collectors.toSet());
            Set<Long> aliveIds = reminderMapper.selectBatchIds(execReminderIds).stream()
                    .map(Reminder::getId).collect(Collectors.toSet());
            for (ExecutionRecord e : execs) {
                if (aliveIds.contains(e.getReminderId())) {
                    continue; // 任务仍在，走 ① 的执行记录条目
                }
                // 孤儿条目：kind=orphan 是给前端的标记，前端据此不显示植物信息、标题显示"已删除的任务"；
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", "orphan");
                m.put("time", e.getExecuteTime());
                m.put("title", "已删除的任务");
                m.put("plantId", null);
                m.put("plantName", null);
                m.put("plantEmoji", null);
                m.put("note", e.getNote());
                m.put("inputs", e.getInputs());
                m.put("reminderId", e.getReminderId());
                m.put("execId", e.getId());
                items.add(m);
            }
        }
        // 两类条目混在一起后统一按时间倒序（最新的在前），再截取前 limit 条
        return items.stream()
                .sorted((a, b) -> ((LocalDateTime) b.get("time")).compareTo((LocalDateTime) a.get("time")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** 某植物的系统推送（种植建议/虫害预防/生长提醒，按时间倒序）—— */
    public List<Reminder> plantReminders(Long plantId) {
        Plant p = plantMapper.selectById(plantId);
        if (p == null) {
            throw new BusinessException(400, "种植物不存在");
        }
        // 归属校验：非管理员只能看自己那盆（与 checkPlant 同一套角色判定）
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !p.getUserId().equals(UserContext.getUserId()))) {
            throw new BusinessException(403, "只能查看自己的种植物");
        }
        // 查这盆植物名下的两类系统推送，按提醒时间倒序（最新的在最上面）。
        return reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getPlantId, plantId)
                .in(Reminder::getType, List.of("plant_advice", "plant_pest"))
                .orderByDesc(Reminder::getRemindTime));
    }

    /** history 条目构造：已完成任务（或某次执行记录）的展示字段。 */
    private Map<String, Object> taskItem(Reminder r, LocalDateTime time, String note, String inputs, String photos, Long execId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "task");
        m.put("time", time);                       // 展示用时间：优先是实际执行时间，退而求其次是完成时间
        m.put("title", r.getTitle());
        m.put("plantId", r.getPlantId());
        m.put("plantName", r.getPlantName());      // 由 fillPlant 提前补好
        m.put("plantEmoji", r.getPlantEmoji());
        m.put("reminderId", r.getId());
        m.put("type", r.getType());
        m.put("note", note);                       // 备注（无执行记录时为 null）
        m.put("inputs", inputs);                   // 农资投入明细 JSON 串
        m.put("photos", photos);                   // 现场照片
        m.put("execId", execId);
        return m;
    }

    /** 提醒处理统计：总数/待办/已完成/取消 + 处理率 + 按来源分组。 */
    public Map<String, Object> reminderStats(Long userId) {
        LambdaQueryWrapper<Reminder> qw = new LambdaQueryWrapper<>();
        if (userId != null) {
            qw.eq(Reminder::getUserId, userId);
        }
        List<Reminder> all = reminderMapper.selectList(qw);   // 【处理率算法】
        Map<String, Object> result = new LinkedHashMap<>();

        // 三个状态各自计数（内存里算，不再打数据库；统计口径是全量一次查出来）
        long done = all.stream().filter(r -> "done".equals(r.getStatus())).count();
        long pending = all.stream().filter(r -> "pending".equals(r.getStatus())).count();
        long cancelled = all.stream().filter(r -> "cancelled".equals(r.getStatus())).count();
        result.put("total", all.size());
        result.put("pending", pending);
        result.put("done", done);
        result.put("cancelled", cancelled);
        // 处理率 = 已完成 / 总数，保留一位小数：先 ×1000 四舍五入取整再 ÷10（避免浮点误差），
        result.put("rate", all.isEmpty() ? 0.0 : Math.round(done * 1000.0 / all.size()) / 10.0);

        // 按来源分组：{ plant_advice: {total, done}, ... }，只保留有数据的类型
        Map<String, Map<String, Object>> byType = new LinkedHashMap<>();
        for (Reminder r : all) {
            byType.computeIfAbsent(r.getType(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("total", 0);
                m.put("done", 0);
                return m;
            });
            Map<String, Object> m = byType.get(r.getType());
            m.put("total", (int) m.get("total") + 1);   // 该类型总数 +1
            if ("done".equals(r.getStatus())) {
                m.put("done", (int) m.get("done") + 1); // 该类型里已完成的再 +1
            }
        }
        result.put("byType", byType);
        return result;
    }
}
