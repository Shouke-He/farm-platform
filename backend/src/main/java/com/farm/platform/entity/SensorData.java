package com.farm.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应数据库 sensor_data 表（传感器遥测数据）：一行一个「指标=数值」，ESP32 每 5 秒上报一次，表是系统里增长最快的一张。 */
@Data
@TableName("sensor_data")
public class SensorData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;        // 指向 device.id，说明是哪台设备上报的
    private String metric;        // 指标名：soil_humidity土壤湿度 / soil_temp土温 / air_temp气温 / air_humidity空气湿度 / light光照 / rssi信号强度
    private BigDecimal value;     // 指标数值，单位随 metric 而定（湿度 % / 温度 ℃ / 光照 lux / 信号强度 dBm）
    private LocalDateTime collectTime;
}
