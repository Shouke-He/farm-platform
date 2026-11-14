package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Reminder;
import com.farm.platform.mapper.ReminderMapper;
import com.farm.platform.ws.NotificationService;
import com.farm.platform.ws.UserOnlineEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 农事任务「到点提醒」：到点那一刻推一次给本人；错过了窗口的，等他一上线再补一次（见 onUserOnline）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDueNotifyScheduler {

    private final ReminderMapper reminderMapper;
    private final NotificationService notificationService;

    /** 推送窗口（分钟）：到点后超过这个时长就不再补推，防止停机恢复后集中弹旧提醒 */
    private static final long WINDOW_MINUTES = 10L;

    /** 通知标题：前端只对这个 type 走橙色告警样式 */
    private static final String TITLE = "⏰ 农事任务到点提醒";

    /** 通知里显示的时分（如 09:00） */
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    /** 已推送过的「任务 id | 日期」，保证同一条任务当天只推一次；进程重启后清空 */
    private final Map<String, LocalDate> pushedToday = new ConcurrentHashMap<>();   // 【只推一次】

    /** 每 60 秒扫一遍用户自建任务，把「今天该做、且刚到点」的推给本人。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)   // 【60秒扫描】
    public void scanDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> tasks = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getType, "custom")
                .eq(Reminder::getStatus, "pending")
                .isNotNull(Reminder::getUserId));
        int n = 0;
        for (Reminder r : tasks) {
            LocalDateTime due = dueTimeToday(r, now);
            if (due == null) {
                continue;   // 今天不该做这条（如每周任务今天不是那个星期几）
            }
            if (!isDueNow(due, now)) {
                continue;   // 还没到点，或已经过了窗口（含停机期间错过的）
            }
            // putIfAbsent 返回非 null = 今天已经推过这条了（【10分钟窗口】）
            if (pushedToday.putIfAbsent(r.getId() + "|" + now.toLocalDate(), now.toLocalDate()) != null) {
                continue;
            }
            notify(r, due);
            n++;
        }
        // 清掉昨天及更早的记录，Map 不会随着日子增长
        pushedToday.entrySet().removeIf(e -> e.getValue().isBefore(now.toLocalDate()));
        if (n > 0) {
            log.info("农事任务到点提醒已推送 {} 条", n);
        }
    }

    /** 到点判定：**到点那一刻起**、且迟到不超过 {@link #WINDOW_MINUTES} 分钟，才算该推。 */
    private static boolean isDueNow(LocalDateTime due, LocalDateTime now) {   // 【10分钟窗口】
        if (now.isBefore(due)) {
            return false;   // 还没到点：一分钟内也不提前弹
        }
        return Duration.between(due, now).toMinutes() <= WINDOW_MINUTES;
    }

    /** 这条任务「今天」该提醒的时间点；今天不该做返回 null。 */
    private static LocalDateTime dueTimeToday(Reminder r, LocalDateTime now) {   // 【算出到点】
        LocalDateTime base = r.getRemindTime();
        if (base == null) {
            return null;   // 没有提醒时间的任务不推（理论上不会出现，前端必填）
        }
        String repeat = r.getRepeatType() == null ? "none" : r.getRepeatType();
        LocalDate today = now.toLocalDate();
        switch (repeat) {
            case "daily":
                return today.atTime(base.toLocalTime());
            case "weekly":
                return base.getDayOfWeek() == today.getDayOfWeek() ? today.atTime(base.toLocalTime()) : null;
            case "monthly":
                return base.getDayOfMonth() == today.getDayOfMonth() ? today.atTime(base.toLocalTime()) : null;
            case "yearly":
                return (base.getMonthValue() == today.getMonthValue() && base.getDayOfMonth() == today.getDayOfMonth())
                        ? today.atTime(base.toLocalTime()) : null;
            case "interval": {
                // 每 N 天（2026-10-04 新增）：remind_time 既是首次提醒时刻，也是「第 0 天」的锚点，
                Integer step = r.getIntervalDays();
                if (step == null || step <= 1) {
                    return today.atTime(base.toLocalTime());   // 没记天数或 N=1：当每天处理，别把任务卡死
                }
                long days = ChronoUnit.DAYS.between(base.toLocalDate(), today);
                return (days >= 0 && days % step == 0) ? today.atTime(base.toLocalTime()) : null;
            }
            default:
                return base;   // 单次任务：就是它自己那个时间点
        }
    }

    /** 用户一连上 WebSocket，就把他「今天已经过点、还没做」的任务补推一次 —— 到点那会儿页面没开着也不丢。 */
    @EventListener   // 【上线补推】
    public void onUserOnline(UserOnlineEvent e) {
        Long userId = e.userId();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<Reminder> tasks = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getType, "custom")
                .eq(Reminder::getStatus, "pending")
                .eq(Reminder::getUserId, userId));
        int n = 0;
        for (Reminder r : tasks) {
            LocalDateTime due = dueTimeToday(r, now);
            // 只补「今天该做、且已经过点」的：还没到点的等调度器到点再推；
            // 单次任务过期好几天的不补 —— 保持「只推今天该做的那一次」这个口径
            if (due == null || !due.toLocalDate().equals(today) || now.isBefore(due)) {
                continue;
            }
            if (pushedToday.putIfAbsent(r.getId() + "|" + today, today) != null) {
                continue;   // 今天已经推过（正常到点那次，或先前上线补的那次）
            }
            notify(r, due);
            n++;
        }
        if (n > 0) {
            log.info("用户 {} 上线补推农事任务到点提醒 {} 条", userId, n);
        }
    }

    /** 推一条「该做了」的通知：到点推送与上线补推共用同一份文案 */
    private void notify(Reminder r, LocalDateTime due) {
        notificationService.sendToUser(r.getUserId(), "deadline", TITLE,
                String.format("「%s」该做了（%s）。做完记得在「农事任务」里登记农资用量和照片。",
                        r.getTitle(), due.format(HM)));
    }
}
