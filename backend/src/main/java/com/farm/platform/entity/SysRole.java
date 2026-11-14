package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色表：只有两条（ADMIN / USER）；单独建表是为了加角色只 INSERT、外键保证 role_id 合法 */
@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;              // 1=管理员 2=普通用户
    private String roleCode;      // 角色编码：ADMIN / USER
    private String roleName;      // 角色名称
    private String remark;
    private LocalDateTime createTime;
}
