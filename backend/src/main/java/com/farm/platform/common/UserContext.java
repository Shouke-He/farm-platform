package com.farm.platform.common;

/** 当前登录用户（ThreadLocal）：JwtInterceptor 写入，请求结束必须 clear()，否则线程复用会串号 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> ROLE_ID = new ThreadLocal<>();

    /** 写入当前请求的用户身份 */
    public static void set(Long userId, Long roleId) {
        USER_ID.set(userId);
        ROLE_ID.set(roleId);
    }

    /** 取当前用户 ID：新建数据打归属标记 / 只查自己的数据 */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 取角色 ID：1 = 管理员，2 = 普通用户；权限模型见 11 号册 5.1 */
    public static Long getRoleId() {
        return ROLE_ID.get();
    }

    /** 清空：用 remove() 而非 set(null)，避免内存堆积 */
    public static void clear() {
        USER_ID.remove();
        ROLE_ID.remove();
    }
}
