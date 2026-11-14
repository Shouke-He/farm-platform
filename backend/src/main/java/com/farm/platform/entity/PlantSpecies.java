package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应数据库 plant_species 表（盆栽植物品种库）：各种植物的标准档案，写明适宜温湿度、光照需求、浇水施肥周期，是判定植物是否缺水/长得好不好的依据。 */
@Data
@TableName("plant_species")
public class PlantSpecies {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String emoji;
    private Integer soilMin;         // 土壤湿度适宜下限（%）
    private Integer soilMax;         // 土壤湿度适宜上限（%）
    private Integer tempMin;         // 适宜温度下限（℃）
    private Integer tempMax;         // 适宜温度上限（℃）
    private String lightLevel;       // 光照需求：喜光/散光/耐阴
    private Integer waterCycle;      // 浇水周期（天）
    private Integer fertilizeCycle;  // 施肥周期（天）
    private String careTips;
    private String diseases;         // 易发病虫害，多个用逗号分隔
    /** 分类：crop 农作物 / pot 盆栽 */
    private String category;
    /** 生长阶段模型 JSON：[{"stage":"幼苗期","days":20,"desc":"..."}] */
    private String growthStages;
    /** 品种封面图片 URL（/uploads/xxx，2026-08-24 新增，管理员上传） */
    private String photo;
    private LocalDateTime createTime;
}
