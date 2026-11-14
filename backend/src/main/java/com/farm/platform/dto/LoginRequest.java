package com.farm.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 登录请求体 —— POST /api/auth/login 的入参，用户端 5173 与管理端 5174 的登录页共用。 */
@Data
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    /** 可选：登录入口角色（ADMIN=管理端 / USER=用户端）；null=不校验（注册后自动登录沿用） */
    private String role;
}
