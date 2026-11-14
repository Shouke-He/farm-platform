package com.farm.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farm.platform.entity.Device;
import com.farm.platform.entity.SensorData;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** device 表数据访问；另手写 3 条 sensor_data 聚合 SQL（BaseMapper 表达不出来） */
public interface DeviceMapper extends BaseMapper<Device> {

    /** 各指标各取最新一条读数（子查询求 MAX(collect_time) 再回连） */
    @Select("SELECT sd.device_id, sd.metric, sd.value, sd.collect_time FROM sensor_data sd " +   // 【一条SQL取最新】
            "JOIN (SELECT metric, MAX(collect_time) mt FROM sensor_data WHERE device_id = #{deviceId} GROUP BY metric) t " +
            "ON sd.metric = t.metric AND sd.collect_time = t.mt WHERE sd.device_id = #{deviceId}")
    List<SensorData> selectLatestByDevice(@Param("deviceId") Long deviceId);

    /** 历史曲线：按设备+指标+起始时间过滤、时间升序（前端直接当横轴用） */
    @Select("SELECT device_id, metric, value, collect_time FROM sensor_data " +
            "WHERE device_id = #{deviceId} AND metric = #{metric} AND collect_time >= #{since} ORDER BY collect_time ASC")
    List<SensorData> selectHistory(@Param("deviceId") Long deviceId, @Param("metric") String metric, @Param("since") LocalDateTime since);

    /** 删掉 before 之前的遥测数据、返回条数（SensorDataCleanScheduler 每天 03:30 调） */
    @Delete("DELETE FROM sensor_data WHERE collect_time < #{before}")   // 【数据量】
    int deleteBefore(@Param("before") LocalDateTime before);
}
