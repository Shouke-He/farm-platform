package com.farm.platform.controller;

import com.farm.platform.annotation.OpLog;
import com.farm.platform.common.Result;
import com.farm.platform.common.UserContext;
import com.farm.platform.entity.SysUser;
import com.farm.platform.mapper.SysUserMapper;
import com.farm.platform.service.WeatherService;
import com.farm.platform.service.WeatherWarnScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 天气：按当前登录用户所在城市的未来 3 天预报，以及管理员手动刷新天气预警。 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    /** 默认区域：南宁（与 application.yml farm.weather.location 一致） */
    private static final String DEFAULT_CITY = "101300101";

    private final WeatherService weatherService;
    private final WeatherWarnScheduler weatherWarnScheduler;
    private final SysUserMapper sysUserMapper;

    /** 30 分钟内存缓存（按城市分键，避免频繁刷新消耗 API 配额） */
    private final Map<String, List<WeatherService.DayWeather>> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTime = new ConcurrentHashMap<>();
    private static final long CACHE_MS = 30 * 60_000L;

    /** 未来 3 天预报：城市取当前登录人的 sys_user.city，为空则兜底默认城市（南宁，和风城市 ID）。 */
    @GetMapping("/my")
    public Result<Map<String, Object>> my() {
        SysUser me = sysUserMapper.selectById(UserContext.getUserId());
        String city = (me != null && me.getCity() != null && !me.getCity().isBlank()) ? me.getCity() : DEFAULT_CITY;
        long now = System.currentTimeMillis();
        Long last = cacheTime.get(city);
        if (last == null || now - last > CACHE_MS) {   // 【30分钟缓存】
            cache.put(city, weatherService.forecast3d(city));
            cacheTime.put(city, now);
        }
        List<Map<String, Object>> days = new ArrayList<>();
        for (WeatherService.DayWeather d : cache.get(city)) {
            days.add(Map.of(
                    "date", d.fxDate(),
                    "text", d.textDay(),
                    "tempMax", d.tempMax(),
                    "tempMin", d.tempMin(),
                    "wind", d.windScaleDay()));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cityId", city);
        map.put("cityName", WeatherService.cityName(city));
        map.put("days", days);
        return Result.ok(map);
    }

    /** 天气预报（兼容旧调用方）：内部直接调本类的 my()，把返回结果里的 days 数组单独抽出来返回， */
    @GetMapping("/forecast")
    public Result<List<Map<String, Object>>> forecast() {
        Map<String, Object> data = my().getData();
        @SuppressWarnings("unchecked")   // 忽略泛型未检查警告
        List<Map<String, Object>> days = (List<Map<String, Object>>) data.get("days");
        return Result.ok(days);
    }

    /** 手动刷新天气预警：立刻让 WeatherWarnScheduler 跑一遍扫描（命中规则就写提醒并推送给对应城市的用户）。 */   // 【2小时一轮】
    @PostMapping("/scan")
    @OpLog(module = "天气", operation = "手动刷新天气预警")
    public Result<Void> scan() {
        if (UserContext.getRoleId() != 1) {
            return Result.error(403, "仅管理员可手动刷新天气预警");
        }
        weatherWarnScheduler.scan();
        return Result.ok();
    }
}
