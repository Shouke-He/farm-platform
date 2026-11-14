package com.farm.platform.interceptor;

import com.farm.platform.common.BusinessException;
import com.farm.platform.common.UserContext;
import com.farm.platform.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 鉴权第一道关：验 Authorization 头里的 token，把 userId / roleId 塞进 UserContext */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /** 进 Controller 前先验 token；不过直接抛 401（HTTP 状态码仍是 200，错误码在响应体） */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {   // 【验令牌】
        // 跨域预检不带 Authorization，不放行会全被 401 挡下
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {   // 【401统一】
            throw new BusinessException(401, "未登录或登录已过期");
        }
        try {
            // 去掉 "Bearer " 这 7 个字符
            Claims claims = jwtUtil.parse(auth.substring(7));
            Long userId = Long.valueOf(claims.getSubject());
            Long roleId = claims.get("roleId", Long.class);   // 1=管理员 2=普通用户
            UserContext.set(userId, roleId);
            return true;
        } catch (Exception e) {
            // 过期、签名不对、格式错都归 401，不把具体原因暴露给前端
            throw new BusinessException(401, "Token 无效或已过期");
        }
    }

    /** 请求处理完（成功或报错都算）清空 ThreadLocal，防止线程复用读到上个用户 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
