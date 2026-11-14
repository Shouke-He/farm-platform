package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应数据库 reminder 表（农事提醒表）：系统里「农事任务」就是它，节气/天气/生长阶段引擎自动生成的提醒和用户自建任务都存这一张表。 */
@Data
@TableName("reminder")
public class Reminder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    /** 来源类型 growth/solar/weather/custom */
    private String type;
    private String content;
    private Long userId;          // 接收人，指向 sys_user.id（为空=全体可见）
    private LocalDateTime remindTime;
    /** 完成时间（status=done 时写入；恢复待办/每日重置时清空） */
    private LocalDateTime doneTime;
    private String repeatType;    // 重复方式：none单次/daily每天/weekly每周/monthly每月/yearly每年/interval每N天   // 【重复任务】
    /** 每 N 天（仅 repeat_type=interval 时用）：今天该做 ⟺ (今天 − remindTime 的日期) 的天数 % N == 0（2026-10-04 新增） */
    private Integer intervalDays;
    private String priority;      // 优先级：normal/important/urgent
    private String status;        // pending=待办 / done=已完成 / cancelled=已取消
    private String source;        // 自动生成来源描述（哪套引擎、依据什么生成的）
    /** 提醒覆盖区域（weather=和风城市 ID；NULL=全区域，如节气/传感器） */
    private String city;
    /** 关联的种植物 id（NULL=通用任务） */
    private Long plantId;
    // 非表字段（exist=false）：数据库没有这两列，由 Service 层查完主表后补上种植物名字和图标给前端
    @TableField(exist = false)
    private String plantName;
    @TableField(exist = false)
    private String plantEmoji;
    private LocalDateTime createTime;
}
