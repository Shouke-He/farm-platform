package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应数据库 operation_log 表（操作日志表）：AOP 切面拦截标注了 @OpLog 的方法，落一行记录谁调了什么、耗时多少、成功还是失败。 */
@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;          // 操作用户，指向 sys_user.id
    private String username;
    private String realName;
    private String roleName;
    private String module;
    private String operation;     // @OpLog 注解里写死的操作描述
    private String method;        // HTTP 方法：GET/POST/PUT/DELETE
    private String url;
    private String ip;
    private Long durationMs;      // 接口耗时，单位毫秒
    private String status;        // success=成功 / error=抛异常
    private String errorMsg;
    private LocalDateTime createTime;
}
