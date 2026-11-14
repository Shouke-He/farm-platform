package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 账号表：管理员与普通用户共用，身份由 role_id 决定（标准 RBAC，演示口径见 11 号册 5.1） */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** WRITE_ONLY：只接收、不输出 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String realName;
    private String phone;
    private Long roleId;          // 指向 sys_role.id：1=管理员 2=普通用户
    private Integer status;       // 1=启用 0=禁用
    private String city;          // 和风城市 ID，NULL=南宁；天气提醒按它过滤
    private LocalDateTime createTime;

    // 非表字段：登录时按 roleId 回填，供前端判断身份
    @TableField(exist = false)
    private String roleCode;
    @TableField(exist = false)
    private String roleName;
}
