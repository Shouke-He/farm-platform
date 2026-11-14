package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应数据库 grow_record 表（生长状况记录）：用户每次量株高/看叶色登记的成长快照，assessment 评估结论由服务端算好再落库。 */
@Data
@TableName("grow_record")
public class GrowRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plantId;         // 指向 plant.id，说明是哪一盆的记录
    private LocalDateTime recordTime;
    private BigDecimal heightCm;  // 株高，单位 cm
    private String leafColor;     // 叶色：嫩绿/正常绿/发黄/干枯
    private String vigor;         // 长势：旺盛/正常/缓慢/停滞
    private String photo;
    private String assessment;    // 评估结论：正常/低于预期/优于预期
    private String remark;
    private LocalDateTime createTime;
}
