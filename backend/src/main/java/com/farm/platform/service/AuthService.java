package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.common.BusinessException;
import com.farm.platform.dto.LoginRequest;
import com.farm.platform.dto.LoginResponse;
import com.farm.platform.entity.SysRole;
import com.farm.platform.entity.SysUser;
import com.farm.platform.mapper.SysRoleMapper;
import com.farm.platform.mapper.SysUserMapper;
import com.farm.platform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 登录服务：查账号 → 比密码 → 查停用 → 分端校验 → 签 JWT（RBAC 口径见 11 号册 5.1） */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 登录主流程；账号不存在与密码错故意返回同一句，防止试探用户名 */
    public LoginResponse login(LoginRequest req) {   // 【查账号】
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));   // username 唯一，最多一条
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {   // 【比密码】   // 【密码】
            throw new BusinessException(401, "用户名或密码错误");
        }
        // status 为 null 也按禁用挡掉
        if (user.getStatus() == null || user.getStatus() != 1) {   // 【状态校验】
            throw new BusinessException(403, "账号已被禁用");
        }
        SysRole role = roleMapper.selectById(user.getRoleId());
        String roleCode = role != null ? role.getRoleCode() : "";
        // 分端校验：用 400 不用 401 —— 401 会被前端 axios 当成登录失效、清掉登录态
        if (req.getRole() != null && !req.getRole().isBlank()) {   // 【分端校验】   // 【后端分端】
            boolean wantAdmin = "ADMIN".equals(req.getRole());
            boolean wantUser = "USER".equals(req.getRole());
            if (!wantAdmin && !wantUser) {
                throw new BusinessException(400, "登录入口参数错误");
            }
            if (wantAdmin && !"ADMIN".equals(roleCode)) {
                throw new BusinessException(400, "普通用户请从用户端登录");
            }
            if (wantUser && !"USER".equals(roleCode)) {
                throw new BusinessException(400, "管理员账号请从管理端登录");
            }
        }
        // sub 装 userId、claim 装 roleId，有效期见 farm.jwt.expire-hours
        String token = jwtUtil.generate(user.getId(), user.getRoleId());   // 【签令牌】
        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getRealName(),
                role != null ? role.getRoleCode() : "",
                role != null ? role.getRoleName() : "");
    }
}
