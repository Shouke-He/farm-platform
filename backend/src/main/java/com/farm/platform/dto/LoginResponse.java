package com.farm.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 登录响应体 —— POST /api/auth/login 的返回，注册成功后的自动登录返回的也是它。 */
@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
    private String roleName;
}
