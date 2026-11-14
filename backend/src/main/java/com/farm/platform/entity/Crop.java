package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应数据库 crop 表（作物表）：作物字典，记录玉米、小麦这类大田作物的类别、全生长周期天数与适宜种植季节。 */
@Data
@TableName("crop")
public class Crop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;   // 类别：粮食/蔬菜/油料等
    private Integer growthDays;
    private String season;
    private String remark;
    private LocalDateTime createTime;
}
