package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 对应数据库 solar_term 表（二十四节气知识库）：24 条固定数据，给节气引擎提供「今天是什么节气、该干什么农活」的知识底稿。 */
@Data
@TableName("solar_term")
public class SolarTerm {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String termName;
    private String monthDay;      // 公历日期，是约数，格式如 04-05
    private String advice;
    private String proverb;
}
