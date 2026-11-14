package com.farm.platform.ws;

/** 用户上线事件：WebSocket 握手成功、会话登记进在线表之后发出。
 *  谁监听：TaskDueNotifyScheduler —— 收到就把该用户「今天已经过点、还没做」的农事任务补推一次，
 *  解决「到点那一刻页面没开着 → 这条提醒永远收不到」的问题（2026-10-12 加）。 */
public record UserOnlineEvent(Long userId) {
}
