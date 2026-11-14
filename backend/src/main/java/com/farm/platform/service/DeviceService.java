package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Device;
import com.farm.platform.entity.SensorData;
import com.farm.platform.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 设备服务：设备列表 + 历史曲线（对应数据库 device 表和 sensor_data 表）。 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    // 曲线最多返回 300 个点（前端渲染性能考虑）
    private static final int MAX_POINTS = 300;

    private final DeviceMapper deviceMapper;

    /** 全平台设备列表（管理员端用），逐条补上最新指标。 */
    public List<Device> listAll() {
        List<Device> list = deviceMapper.selectList(null);
        list.forEach(this::fill);
        return list;
    }

    /** 某个用户名下的设备列表（个人用户页面用），按 id 倒序（新绑定的排前面）。 */
    public List<Device> listByUser(Long userId) {
        List<Device> list = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getUserId, userId).orderByDesc(Device::getId));
        list.forEach(this::fill);
        return list;
    }

    /** 补全最新指标 */
    private void fill(Device d) {
        // sensor_data 里每个 metric 取最近一条，拼成 {soil_humidity: 21.3, ...} 挂在设备上
        List<SensorData> latest = deviceMapper.selectLatestByDevice(d.getId());
        Map<String, Object> map = new LinkedHashMap<>();
        for (SensorData sd : latest) {
            map.put(sd.getMetric(), sd.getValue());
        }
        d.setLatest(map);
    }

    /** 历史曲线数据：取 sensor_data 里某台设备某个指标「最近 minutes 分钟」的记录，时间升序。 */
    public List<SensorData> history(Long deviceId, String metric, int minutes) {
        // 起点 = 当前时间往前推 minutes 分钟；至少推 1 分钟，防止前端传 0 或负数把范围取空
        LocalDateTime since = LocalDateTime.now().minusMinutes(Math.max(1, minutes));
        List<SensorData> list = deviceMapper.selectHistory(deviceId, metric, since);
        if (list.size() <= MAX_POINTS) {
            return list;
        }
        // 抽样步长 = 总点数 / 300 再 +1，保证抽完的点数不超过 300
        int step = list.size() / MAX_POINTS + 1;
        List<SensorData> sampled = new ArrayList<>();
        for (int i = 0; i < list.size(); i += step) {
            sampled.add(list.get(i));
        }
        // 补上最后一条，保证曲线末端接得上最新时刻
        sampled.add(list.get(list.size() - 1));
        return sampled;
    }
}
