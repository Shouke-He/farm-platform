package com.farm.platform.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.UserContext;
import com.farm.platform.dto.LoginRequest;
import com.farm.platform.entity.OperationLog;
import com.farm.platform.entity.SysRole;
import com.farm.platform.entity.SysUser;
import com.farm.platform.mapper.SysRoleMapper;
import com.farm.platform.mapper.SysUserMapper;
import com.farm.platform.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/** 操作日志切面：把「谁、在什么时候、干了什么、成没成」自动记下来的 AOP 实现。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final LogService logService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    /** 环绕通知：把贴了 @OpLog 的方法整个包起来。 */
    @Around("@annotation(opLog)")   // 【环绕记录】
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        // 记下开始时间，方法结束时用它算耗时
        long start = System.currentTimeMillis();
        OperationLog record = new OperationLog();
        // 模块名和操作名直接取注解上写的值，不用调用方传
        record.setModule(opLog.module());
        record.setOperation(opLog.operation());
        fillRequest(record);
        try {
            // 放行，去执行真正的 Controller 方法
            Object result = pjp.proceed();   // 【成败都记】
            record.setStatus("success");
            return result;
        } catch (Throwable e) {   // 【成败都记】
            record.setStatus("error");
            // 错误信息：异常没带 message 就退而取异常类名；有 message 也只存前 300 字，防止超长内容撑爆日志表
            record.setErrorMsg(e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage().substring(0, Math.min(e.getMessage().length(), 300)));
            // 原样抛出，业务报错该怎么返回给前端还怎么返回，切面只负责记录
            throw e;
        } finally {
            // 走到这里说明方法已经结束（不管成功失败），补上耗时、时间和操作人后落库
            record.setDurationMs(System.currentTimeMillis() - start);   // 【耗时操作人】
            record.setCreateTime(LocalDateTime.now());
            fillUser(record, pjp);
            // 异步写库，不拖慢这次请求
            logService.save(record);
        }
    }

    /** 请求信息：IP / 方法 / URL */
    private void fillRequest(OperationLog record) {
        try {
            // 从 Spring 的请求上下文里取出当前这次 HTTP 请求
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest req = attrs.getRequest();
                // GET / POST / PUT 这类请求方式
                record.setMethod(req.getMethod());
                record.setUrl(req.getRequestURI());
                // 走没走 Nginx 之类的反向代理：真实客户端 IP 会放在 X-Forwarded-For 头里
                String ip = req.getHeader("X-Forwarded-For");   // 【真实IP反查】
                if (ip == null || ip.isBlank()) {
                    // 没有代理就直接取连接来源地址
                    ip = req.getRemoteAddr();
                }
                // 代理链是 "客户端IP, 代理1, 代理2" 这样逗号分隔的，第一个才是真正的客户端
                record.setIp(ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip);
            }
        } catch (Exception ignored) {
            // 定时任务等不是 HTTP 请求的线程拿不到请求上下文，忽略即可
        }
    }

    /** 用户信息：登录等未过拦截器的操作没有 UserContext，从请求参数补账号 */
    private void fillUser(OperationLog record, ProceedingJoinPoint pjp) {
        // 正常情况下 JwtInterceptor 已经把登录人塞进了 UserContext
        Long userId = UserContext.getUserId();   // 【真实IP反查】
        String username = null;
        if (userId == null) {   // 【追问·日志失败】
            // 没有上下文（比如登录接口本身、注册接口），只能在方法参数里找登录请求体
            for (Object arg : pjp.getArgs()) {
                if (arg instanceof LoginRequest login && login.getUsername() != null) {
                    username = login.getUsername();
                    // 用参数里的账号反查 sys_user，把用户信息补齐
                    SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, username));
                    if (u != null) {
                        userId = u.getId();
                        fillUserInfo(record, u);
                        // 找到人并填好信息就直接结束
                        return;
                    }
                }
            }
            return;
        }
        // 有上下文：按 userId 查一次用户表，补账号、姓名和角色名
        SysUser u = userMapper.selectById(userId);
        if (u != null) {
            fillUserInfo(record, u);
        }
    }

    /** 把用户 id、账号、姓名和角色名填进日志对象（角色名再查一次 sys_role）。 */
    private void fillUserInfo(OperationLog record, SysUser u) {
        record.setUserId(u.getId());
        record.setUsername(u.getUsername());
        record.setRealName(u.getRealName());
        SysRole role = roleMapper.selectById(u.getRoleId());
        record.setRoleName(role != null ? role.getRoleName() : null);
    }
}
