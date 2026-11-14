package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.common.BusinessException;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.entity.Reminder;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import com.farm.platform.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 养护计划：把「品种档案里的浇水周期 / 施肥周期」变成用户自己的农事任务，落进「今日农事清单」。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarePlanService {

    private final PlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final ReminderMapper reminderMapper;
    private final ReminderService reminderService;

    /** 时间格式：返回给前端的提醒时间 */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 两件日常作业：任务标题 + 正文里的称呼，顺序与品种里的 waterCycle / fertilizeCycle 对应 */
    private static final String[] TITLES = { "💧 浇水", "🌿 施肥" };
    private static final String[] LABELS = { "浇水", "施肥" };

    /** 为某株种植物生成养护计划。 */
    public Map<String, Object> generate(Long plantId) {
        Plant p = plantMapper.selectById(plantId);
        if (p == null) {
            throw new BusinessException(404, "种植物不存在");
        }
        // 归属校验：口径与 PlantController 的改/删一致 —— 管理员可为任意植物生成，普通用户只能为自己名下的生成
        Long roleId = UserContext.getRoleId();
        if (roleId == null || (roleId != 1 && !p.getUserId().equals(UserContext.getUserId()))) {
            throw new BusinessException(403, "无权为该种植物生成养护计划");
        }
        PlantSpecies sp = p.getSpeciesId() == null ? null : speciesMapper.selectById(p.getSpeciesId());
        if (sp == null) {
            throw new BusinessException(400, "这株种植物还没有关联品种，先补上品种再生成养护计划");
        }

        // 首次提醒时间 = 下一个整点：生成完不会立刻变成「已逾期」，到点提醒也能在整点准时弹（见 TaskDueNotifyScheduler）
        LocalDateTime base = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);

        List<Map<String, Object>> items = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        Integer[] cycles = { sp.getWaterCycle(), sp.getFertilizeCycle() };
        for (int i = 0; i < TITLES.length; i++) {
            Integer cycle = cycles[i];
            if (cycle == null || cycle <= 0) {
                continue;   // 品种没维护这项周期（或数据是 0）就跳过，不硬造
            }
            String repeat = mapRepeat(cycle);
            // 已经有同名待办（同一株、同一人）就不重复生成 —— 反复点按钮不会堆出一串重复任务
            boolean exists = reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()   // 【计划重复】
                    .eq(Reminder::getUserId, p.getUserId())
                    .eq(Reminder::getType, "custom")
                    .eq(Reminder::getPlantId, plantId)
                    .eq(Reminder::getTitle, TITLES[i])
                    .eq(Reminder::getStatus, "pending")) > 0;
            if (exists) {
                skipped++;
                continue;
            }
            Reminder r = new Reminder();
            r.setTitle(TITLES[i]);
            r.setType("custom");                 // 与用户手建任务同一种类型：进今日清单、可改可删、完成写执行记录
            // 施肥比浇水多一句农艺常识：土壤过干时施肥容易烧根，应先浇透水再施（2026-10-02 十.152）
            String extra = "施肥".equals(LABELS[i]) ? "土壤过干时先浇透水再施肥，避免烧根。" : "";
            String repeatText = "daily".equals(repeat) ? "每天" : "每 " + cycle + " 天";
            r.setContent(String.format("按「%s」的品种档案：约每 %d 天%s一次（已生成「%s」重复的待办）。"
                    + "实际间隔请结合土壤湿度、天气和长势灵活调整，时间与次数随时可改。%s",
                    sp.getName(), cycle, LABELS[i], repeatText, extra));
            r.setUserId(p.getUserId());           // 任务归种植物主人，不是当前操作人（管理员代生成也一样）
            r.setRemindTime(base);                // 既是首次提醒时刻，也是「每 N 天」的锚点（第 0 天）
            r.setRepeatType(repeat);
            r.setIntervalDays("interval".equals(repeat) ? cycle : null);   // 只有每 N 天才落天数
            r.setPriority("normal");
            r.setStatus("pending");
            r.setSource("养护计划");               // 来源标记，便于事后区分「自己建的」和「系统按品种生成的」
            r.setPlantId(plantId);
            reminderService.create(r);            // 复用提醒服务：它会再校验一次植物归属并补默认值
            created++;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", TITLES[i]);
            item.put("cycleDays", cycle);
            item.put("repeatType", repeat);
            item.put("intervalDays", "interval".equals(repeat) ? cycle : null);
            item.put("remindTime", base.format(FMT));
            items.add(item);
        }
        if (created == 0 && skipped == 0) {
            throw new BusinessException(400, "该品种还没有维护浇水/施肥周期，无法生成养护计划");
        }
        log.info("养护计划：{} 新建 {} 条、跳过已存在 {} 条", p.getName(), created, skipped);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plant", p.getName());
        out.put("created", created);
        out.put("skipped", skipped);
        out.put("items", items);
        return out;
    }

    /** 天数 → 重复方式（2026-10-04 起**不再就近映射**，改按真实天数）。 */
    private static String mapRepeat(int days) {
        return days <= 1 ? "daily" : "interval";
    }
}
