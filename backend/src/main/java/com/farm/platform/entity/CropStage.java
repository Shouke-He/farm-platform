package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 对应数据库 crop_stage 表（作物生长阶段模板）：按「种植后天数」把一种作物划成若干阶段，每段配一条农事建议。 */
@Data
@TableName("crop_stage")
public class CropStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cropId;          // 指向 crop.id，说明这套阶段模板属于哪种作物
    private String stageName;
    private Integer dayStart;     // 种植后天数起
    private Integer dayEnd;       // 种植后天数止
    private String advice;
}
