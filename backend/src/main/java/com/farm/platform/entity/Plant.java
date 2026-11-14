package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 对应数据库 plant 表（用户盆栽）：用户自己添加的一盆植物，是系统里「种植物」的正主，农事任务、生长记录、设备绑定都挂在它身上。 */
@Data
@TableName("plant")
public class Plant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;          // 归属用户，指向 sys_user.id
    private String name;
    private Long speciesId;       // 指向 plant_species.id，决定这盆植物的适宜温湿度、浇水周期
    private String photo;
    private LocalDate plantDate;
    private Long deviceId;        // 绑定的传感器设备，指向 device.id（一套设备可绑多盆）
    private String remark;
    private LocalDateTime createTime;
    /** 种植环境（添加向导 Step2）：阳台/窗台/露台/小院/室内/菜地 */
    private String location;
    /** 种植方式：土培/水培 */
    private String growMethod;
    /** 光照档位：少于2小时/2-4小时/4-6小时/6小时以上 */
    private String sunlight;
    /** 容器：小盆/中盆/大盆/水培盒/还没准备 */
    private String container;
    /** 生长阶段（添加向导 Step3 所选，取品种 growthStages 的 stage 名） */
    private String stageName;
    /** 手动标注生长阶段的起始日期（标注当天为第 1 天，进度按此自增） */
    private LocalDate stageDate;

    // 本行起往下都是非表字段（exist=false）：数据库里没有这些列，由 Service 层查完主表后再补进来给前端用
    @TableField(exist = false)
    private String speciesName;
    @TableField(exist = false)
    private String speciesEmoji;
    @TableField(exist = false)
    private String deviceName;
    /** 绑定设备在线状态（前端离线时不显示指标数据） */
    @TableField(exist = false)
    private String deviceStatus;
    /** 绑定设备最新指标：{soil_humidity: 21.3, ...} */
    @TableField(exist = false)
    private Map<String, Object> latest;
    /** 品种适宜区间（建议引擎/前端展示用） */
    @TableField(exist = false)
    private PlantSpecies species;
    /** 生长期推算结果（GrowthStageService 填充） */
    @TableField(exist = false)
    private Map<String, Object> stage;
    /** 浇水时机 5 档（PlantService.fill 填充） */
    @TableField(exist = false)
    private Map<String, Object> watering;
}
