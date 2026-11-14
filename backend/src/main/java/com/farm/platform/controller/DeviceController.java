package com.farm.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.Device;
import com.farm.platform.entity.SensorData;
import com.farm.platform.mapper.DeviceMapper;
import com.farm.platform.service.DeviceService;
import com.farm.platform.service.IotTelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 物联网设备：设备列表、增删改、手动刷新在线状态、发现附近硬件、认领设备、最新指标与历史曲线。 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;

    // 私有小工具：判当前登录人是不是管理员（UserContext.getRoleId() == 1），下面多处用它分流
    private boolean isAdmin() {
        Long roleId = UserContext.getRoleId();
        return roleId != null && roleId == 1;
    }

    /** 能不能维护这台设备（改名 / 删除）—— 全项目唯一的设备归属判定口，update、delete 两处都调它。 */
    private boolean canMaintain(Device d) {
        if (d.getUserId() == null) {
            return isAdmin();                                   // 平台设备：管理员维护
        }
        return d.getUserId().equals(UserContext.getUserId());    // 用户设备：只认主人，管理员不在例外里
    }

    /** 设备列表：每条设备都补上了最新各指标（地块、作物名是 device 表自带的列）。 */
    @GetMapping
    public Result<List<Device>> list(@RequestParam(required = false) Long userId) {
        if (isAdmin()) {
            return userId != null ? Result.ok(deviceService.listByUser(userId)) : Result.ok(deviceService.listAll());
        }
        return Result.ok(deviceService.listByUser(UserContext.getUserId()));
    }

    /** 新增设备：校验设备编号非空且全库唯一，然后把 id 置空、状态先置 offline、创建时间取服务端，再插入。 */
    @PostMapping
    @OpLog(module = "设备", operation = "新增设备")
    public Result<Device> create(@RequestBody Device d) {
        if (d.getDeviceNo() == null || d.getDeviceNo().isBlank()) {
            return Result.error(400, "设备编号不能为空");
        }
        Long exists = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceNo, d.getDeviceNo()));
        if (exists != null && exists > 0) {
            return Result.error(400, "设备编号已存在");
        }
        d.setId(null);
        if (!isAdmin()) {
            d.setUserId(UserContext.getUserId());
        }
        d.setStatus("offline");
        d.setCreateTime(LocalDateTime.now());
        deviceMapper.insert(d);
        return Result.ok(d);
    }

    /** 修改设备：先按 id 查出库里那条旧记录，把请求体里的设备名和备注写回去再更新。 */
    @PutMapping
    @OpLog(module = "设备", operation = "修改设备")
    public Result<Void> update(@RequestBody Device d) {
        Device old = deviceMapper.selectById(d.getId());
        if (old == null) {
            return Result.error(404, "设备不存在");
        }
        if (!canMaintain(old)) {
            return Result.error(403, "无权操作该设备，用户设备请由设备主人自行修改");
        }
        old.setDeviceName(d.getDeviceName());
        old.setRemark(d.getRemark());
        deviceMapper.updateById(old);
        return Result.ok();
    }

    /** 删除设备：按路径上的 id 删掉 device 表里那一行（sensor_data 里的历史读数不跟着删）。 */
    @DeleteMapping("/{id}")
    @OpLog(module = "设备", operation = "删除设备")
    public Result<Void> delete(@PathVariable Long id) {
        Device old = deviceMapper.selectById(id);
        if (old == null) {
            return Result.error(404, "设备不存在");
        }
        if (!canMaintain(old)) {
            return Result.error(403, "无权操作该设备，用户设备请由设备主人自行删除");
        }
        deviceMapper.deleteById(id);
        return Result.ok();
    }

    /** 手动刷新一台设备：按在线窗口重算状态，并连同最新各指标一起返回。 */
    @PostMapping("/{id}/refresh")
    @OpLog(module = "设备", operation = "刷新设备状态")
    public Result<Map<String, Object>> refresh(@PathVariable Long id) {
        Device dev = deviceMapper.selectById(id);
        if (dev == null) {
            return Result.error(404, "设备不存在");
        }
        if (!isAdmin() && (dev.getUserId() == null || !dev.getUserId().equals(UserContext.getUserId()))) {
            return Result.error(403, "无权操作该设备");
        }
        // 只做"降级"判定：超过在线窗口未上报则置离线（兜底规则）。
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(IotTelemetryService.ONLINE_WINDOW_SECONDS);
        String status = dev.getStatus() == null ? "offline" : dev.getStatus();
        if (dev.getLastOnlineTime() == null || dev.getLastOnlineTime().isBefore(cutoff)) {
            status = "offline";
        }
        if (!status.equals(dev.getStatus())) {
            dev.setStatus(status);
            deviceMapper.updateById(dev);
        }
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", dev.getId());
        map.put("deviceNo", dev.getDeviceNo());
        map.put("deviceName", dev.getDeviceName());
        map.put("status", status);
        map.put("lastOnlineTime", dev.getLastOnlineTime());
        map.put("metrics", deviceMapper.selectLatestByDevice(id));
        return Result.ok(map);
    }

    /** 发现设备：列出最近 30 秒内有遥测上报的设备编号，按上报时间倒序 —— 含还没注册进库的真实硬件。 */
    @GetMapping("/discover")
    public Result<List<Map<String, Object>>> discover() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);
        List<Map<String, Object>> list = new ArrayList<>();
        IotTelemetryService.RECENT_DEVICES.forEach((no, time) -> {
            if (!time.isBefore(cutoff)) {
                Device dev = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getDeviceNo, no));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("deviceNo", no);
                m.put("lastSeen", time);
                m.put("registered", dev != null);
                if (dev != null) {
                    m.put("id", dev.getId());
                    m.put("mine", dev.getUserId() != null && dev.getUserId().equals(UserContext.getUserId()));
                    // 个人用户可认领：只限平台设备（user_id 为空）；他人设备不可认领（2026-10-02 收口）
                    m.put("claimable", !isAdmin() && dev.getUserId() == null);
                }
                list.add(m);
            }
        });
        list.sort((a, b) -> ((LocalDateTime) b.get("lastSeen")).compareTo((LocalDateTime) a.get("lastSeen")));
        return Result.ok(list);
    }

    /** 认领设备：把这条设备记录的归属改成当前登录人，之后它和它的告警就归自己看。 */
    @PostMapping("/{id}/claim")
    @OpLog(module = "设备", operation = "绑定设备到名下")
    public Result<Void> claim(@PathVariable Long id) {
        if (isAdmin()) {
            return Result.error(403, "管理员账号无需绑定设备");
        }
        Device dev = deviceMapper.selectById(id);
        if (dev == null) {
            return Result.error(404, "设备不存在");
        }
        if (dev.getUserId() != null && dev.getUserId().equals(UserContext.getUserId())) {
            return Result.error(400, "该设备已是你的设备");
        }
        if (dev.getUserId() != null) {
            return Result.error(403, "该设备已归属其他用户，不能认领");
        }
        dev.setUserId(UserContext.getUserId());
        deviceMapper.updateById(dev);
        return Result.ok();
    }

    /** 某设备最新各指标值：取 sensor_data 里每个指标最新的一条，拼成 {指标名: 值} 的 Map。 */
    @GetMapping("/{id}/latest")
    public Result<Map<String, Object>> latest(@PathVariable Long id) {
        List<SensorData> latest = deviceMapper.selectLatestByDevice(id);
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (SensorData sd : latest) {
            map.put(sd.getMetric(), sd.getValue());
        }
        return Result.ok(map);
    }

    /** 历史曲线数据：靠查询参数 metric 指定哪个指标、minutes 指定往前取多少分钟（默认 60）。 */
    @GetMapping("/{id}/history")
    public Result<List<SensorData>> history(@PathVariable Long id,
                                            @RequestParam String metric,
                                            @RequestParam(defaultValue = "60") int minutes) {   // 默认最近 60 分钟
        return Result.ok(deviceService.history(id, metric, minutes));
    }
}
