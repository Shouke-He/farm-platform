package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Reminder;
import com.farm.platform.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 过期提醒清理：删掉"说的那天已过去、仍是 pending"的建议，让引擎按最新数据重发 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderExpiryScheduler {

    /** 各类型有效期（天）：只清 plant_advice / plant_pest，改 plant_pest 为 7 即一周一次 */
    private static final Map<String, Integer> TTL_DAYS = Map.of(
            "plant_advice", 1,   // 种植建议
            "plant_pest", 1      // 病虫害预防
    );

    private final ReminderMapper reminderMapper;

    /** 每 5 分钟一次（启动后 12 秒首跑，比建议引擎早 3 秒：先清旧的再生成新的） */
    @Scheduled(fixedDelay = 300_000, initialDelay = 12_000)   // 每 5 分钟
    public int purgeExpired() {
        int total = 0;
        for (Map.Entry<String, Integer> e : TTL_DAYS.entrySet()) {
            LocalDateTime deadline = LocalDate.now().minusDays(e.getValue() - 1L).atStartOfDay();
            int n = reminderMapper.delete(new LambdaQueryWrapper<Reminder>()
                    .eq(Reminder::getType, e.getKey())
                    .eq(Reminder::getStatus, "pending")
                    .lt(Reminder::getRemindTime, deadline));
            if (n > 0) {
                log.info("过期提醒清理：{} 删掉 {} 条（下发时间早于 {}）", e.getKey(), n, deadline);
            }
            total += n;
        }
        return total;
    }
}
