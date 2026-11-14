package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 对应数据库 device 表（物联网设备）：田里/阳台上的传感器与控制器，传感器上报的读数落在 sensor_data 表。 */
@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceNo;        // 设备编号，全库唯一；传感器上报时靠它认设备
    private String deviceName;
    private String deviceType;      // sensor=传感器 / controller=控制器
    /** 归属用户（空=演示设备，个人用户设备必须归属自己） */
    private Long userId;
    private String status;          // online=在线 / offline=离线
    private LocalDateTime lastOnlineTime;
    private String remark;
    private LocalDateTime createTime;

    /** 最近一次各指标值：{soil_humidity: 21.3, ...}；exist=false 表示它不是 device 表的列， */
    @TableField(exist = false)
    private Map<String, Object> latest;
}
