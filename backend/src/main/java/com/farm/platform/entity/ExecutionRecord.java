package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应数据库 execution_record 表（农事任务执行记录）：任务被标记完成、或用户主动登记执行明细时落的一行；inputs/photos 存 JSON 字符串，由服务层序列化。 */
@Data
@TableName("execution_record")
public class ExecutionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reminderId;      // 指向 reminder.id，说明完成的是哪条农事任务
    private Long userId;          // 执行人，指向 sys_user.id
    private LocalDateTime executeTime;   // 实际执行时间，默认取点「完成」的那一刻
    private String note;
    private String inputs;        // 农资明细 JSON 数组字符串：[{"name":"复合肥","amount":30,"unit":"g"}]
    private String photos;        // 照片 JSON 数组字符串：["/uploads/xxx.jpg"]
    private LocalDateTime createTime;
}
