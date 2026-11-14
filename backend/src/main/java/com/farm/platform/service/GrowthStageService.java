package com.farm.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.platform.entity.PlantSpecies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生长期推算：按品种 growth_stages 模型（[{"stage":"幼苗期","days":20,"desc":".."}]） */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthStageService {

    private final ObjectMapper objectMapper;

    /** 推算当前生长阶段。 */
    public Map<String, Object> calc(PlantSpecies sp, LocalDate plantDate) {   // 【追问·生长阶段】
        // 默认返回值：所有字段先给上，保证前端拿到的 Map 结构永远一致（不会因为缺 key 而取值报错）
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stageName", "未种植");
        out.put("stageIndex", -1);   // -1 = 没有阶段（未种植/数据不全），正常阶段从 0 开始编号
        out.put("stageDay", 0);
        out.put("stageTotal", 0);
        out.put("dayPast", 0);
        out.put("nextStage", null);
        out.put("desc", null);
        out.put("totalDays", 0);
        // 前置条件不满足直接返回默认结构：没品种 / 品种没配阶段模型 / 模型是空白串 / 没有种植日期
        if (sp == null || sp.getGrowthStages() == null || sp.getGrowthStages().isBlank()
                || plantDate == null) {
            return out;
        }
        try {
            // 品种表 growth_stages 存的是 JSON 数组字符串，形如
            List<Map<String, Object>> stages = objectMapper.readValue(   // 【读阶段表】
                    sp.getGrowthStages(), new TypeReference<List<Map<String, Object>>>() {});
            if (stages.isEmpty()) {
                return out;   // 模型是空数组，同样按"未种植"处理
            }
            // 从种植日到今天过了几天（结束日 - 开始日，同一天为 0）
            long dayPast = ChronoUnit.DAYS.between(plantDate, LocalDate.now());
            // 种植日期在未来（手滑填错日期）→ 也算未种植，但仍把 dayPast 返回给前端（负数，前端好提示）
            if (dayPast < 0) {
                out.put("stageName", "未种植");
                out.put("dayPast", dayPast);
                return out;
            }
            // 全周期总天数 = 各阶段 days 累加
            int total = 0;
            for (Map<String, Object> s : stages) {
                total += ((Number) s.get("days")).intValue();
            }
            // remain = 减去前面各阶段后「剩下的天数」，用来判断当前落在哪个阶段
            long remain = dayPast;
            boolean hit = false;   // 是否成功定位到某个阶段（false 表示已经超出整个生长周期）
            for (int i = 0; i < stages.size(); i++) {   // 【扣天数】
                Map<String, Object> s = stages.get(i);
                int days = ((Number) s.get("days")).intValue();
                // 剩余天数小于本阶段长度 → 当前就在这个阶段里
                if (remain < days) {
                    out.put("stageName", s.get("stage"));
                    out.put("stageIndex", i);
                    out.put("stageDay", remain + 1);   // +1：进入本阶段当天算第 1 天（remain 是已经过完的天数）
                    out.put("stageTotal", days);
                    out.put("desc", s.get("desc"));
                    // 下一阶段名；已经是最后一个阶段则给 null（前端显示「已是最后阶段」）
                    out.put("nextStage", i + 1 < stages.size() ? stages.get(i + 1).get("stage") : null);
                    out.put("totalDays", total);
                    hit = true;
                    break;
                }
                remain -= days;   // 本阶段已走完，扣掉它的天数继续往下一阶段找
            }
            // 超过总天数：长期阶段（9999）停在最后阶段，否则标记已过周期
            if (!hit) {   // 【停最后阶段】
                // 走完所有阶段还有富余天数 → 停在最后一个阶段展示
                Map<String, Object> last = stages.get(stages.size() - 1);
                int lastDays = ((Number) last.get("days")).intValue();
                out.put("stageIndex", stages.size() - 1);
                out.put("stageName", last.get("stage"));
                out.put("desc", last.get("desc"));
                // 在最后阶段里已经待了多少天 = 总天数 - 最后阶段之前的天数，再 +1 变成"第几天"
                out.put("stageDay", dayPast - (total - lastDays) + 1);
                out.put("stageTotal", lastDays);
                // 最后阶段天数 < 9999 说明这是个有头有尾的阶段，超期就标记 overdue=true（前端提示"已过生长周期"）；
                if (lastDays < 9999) {
                    out.put("overdue", true);
                }
                out.put("totalDays", total);
            }
            out.put("dayPast", dayPast);   // 无论走到哪个分支，都回填"种植至今第几天"
        } catch (Exception e) {   // 【资料坏不崩】
            // JSON 格式损坏 / days 字段不是数字等 → 不抛出、不中断列表查询，只记日志并返回默认结构
            log.warn("生长阶段解析失败 species={}: {}", sp.getId(), e.getMessage());
        }
        return out;
    }
}
