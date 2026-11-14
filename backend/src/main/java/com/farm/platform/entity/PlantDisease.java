package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应数据库 plant_disease 表（病虫害知识库）：管理员维护的病虫害百科，保存症状、诱因、防治方法与推荐用药，建议引擎按名称+高发季节取用。 */
@Data
@TableName("plant_disease")
public class PlantDisease {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String plants;        // 易感植物，多个用逗号分隔
    private String symptom;
    private String cause;
    private String season;        // 高发季节
    private String prevention;
    private String drug;
    /** 病虫害图片 URL（/uploads/xxx，2026-08-24 新增，管理员上传） */
    private String photo;
    private LocalDateTime createTime;
}
