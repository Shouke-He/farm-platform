package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.farm.platform.entity.Device;
import com.farm.platform.entity.GrowRecord;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.entity.Reminder;
import com.farm.platform.mapper.DeviceMapper;
import com.farm.platform.mapper.GrowRecordMapper;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import com.farm.platform.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统计看板服务：管理端「统计看板」页面的几块数字，全部是现算的聚合查询， */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final PlantMapper plantMapper;
    private final DeviceMapper deviceMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final GrowRecordMapper growRecordMapper;
    private final ReminderMapper reminderMapper;
    private final ReminderService reminderService;

    /** 看板总入口：把四块统计装进一个 Map 回给前端，前端按 key 取用。 */
    public Map<String, Object> overview() {   // 【四块一起算】
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reminderStats", reminderService.reminderStats(null)); // 统计看板为全局口径（admin）
        out.put("deviceStats", deviceStats());
        out.put("speciesStats", speciesStats());
        out.put("regionStats", regionWeatherStats()); // 各区域天气预警统计
        return out;
    }

    /** 各区域天气预警统计：数字来自 reminder 表 —— 筛出 type = 'weather' 的行，按 city 列分组数条数， */
    private List<Map<String, Object>> regionWeatherStats() {
        // selectMaps：不映射成实体，直接拿"列名 -> 值"的 Map，适合这种分组计数的结果
        List<Map<String, Object>> rows = reminderMapper.selectMaps(new QueryWrapper<Reminder>()
                // 查询列：只要城市，以及对每组的计数（SQL 里的 COUNT(*) AS cnt）
                .select("city", "COUNT(*) AS cnt")
                // 只看天气预警这一类提醒
                .eq("type", "weather")
                // 同一个城市的记录归成一组
                .groupBy("city"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cityId", row.get("city"));
            // 城市 ID 转中文名；不在内置城市表里的会显示"南宁市"（WeatherService 里的兜底）
            m.put("cityName", WeatherService.cityName((String) row.get("city")));
            // COUNT(*) 在 MySQL 里是 Bigint，统一转成 int 给前端
            m.put("count", ((Number) row.get("cnt")).intValue());
            out.add(m);
        }
        // 按条数倒序：预警多的城市排前面
        out.sort((a, b) -> (int) b.get("count") - (int) a.get("count"));
        return out;
    }

    /** 设备统计：数字全部来自 device 表全量数据（不区分归属用户）。 */
    private Map<String, Object> deviceStats() {
        List<Device> devices = deviceMapper.selectList(null);   // 【在线率看状态】
        // 在线口径：device.status 字段值为 "online"
        long online = devices.stream().filter(d -> "online".equals(d.getStatus())).count();   // 【追问·在线率】
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", devices.size());
        m.put("online", online);
        m.put("offline", devices.size() - online);
        // 先乘 1000 四舍五入、再除 10 = 百分比保留 1 位小数；没有设备时给 0.0，避免除零
        m.put("rate", devices.isEmpty() ? 0.0 : Math.round(online * 1000.0 / devices.size()) / 10.0);
        return m;
    }

    /** 品种分布统计：以 plant 表为准 —— 先全量取出盆栽，按 species_id 分组数出每个品种种了多少盆， */
    private List<Map<String, Object>> speciesStats() {
        // 品种 id -> 种植数
        Map<Long, Integer> cnt = new HashMap<>();
        List<Plant> plants = plantMapper.selectList(null);   // 【按品种数盆】
        for (Plant p : plants) {
            if (p.getSpeciesId() == null) continue;
            // 这个品种第一次出现就记 1，之后再出现就累加 1
            cnt.merge(p.getSpeciesId(), 1, Integer::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : cnt.entrySet()) {
            PlantSpecies sp = speciesMapper.selectById(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("speciesId", e.getKey());
            // 品种若已被删除，查不到就显示"未知品种"
            m.put("speciesName", sp == null ? "未知品种" : sp.getName());
            m.put("emoji", sp == null ? "❓" : sp.getEmoji());
            m.put("count", e.getValue());
            m.put("health", healthOf(e.getKey()));
            list.add(m);
        }
        // 按种植数倒序
        list.sort((a, b) -> (int) b.get("count") - (int) a.get("count"));
        return list;
    }

    /** 某品种的健康度分布：先查出这个品种下的全部盆栽（plant 表按 species_id 筛）， */
    private Map<String, Object> healthOf(Long speciesId) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("normal", 0);
        h.put("lower", 0);
        h.put("upper", 0);
        List<Plant> ofSpecies = plantMapper.selectList(new LambdaQueryWrapper<Plant>()   // 【健康度看记录】
                .eq(Plant::getSpeciesId, speciesId));
        for (Plant p : ofSpecies) {
            GrowRecord last = growRecordMapper.selectOne(new LambdaQueryWrapper<GrowRecord>()
                    .eq(GrowRecord::getPlantId, p.getId())
                    .orderByDesc(GrowRecord::getRecordTime)
                    // 在 SQL 末尾拼上 LIMIT 1，只取最近的那一条
                    .last("LIMIT 1"));
            // 没登记过记录、或那条记录没结论 → 这盆不计入任何一档
            if (last == null || last.getAssessment() == null) continue;
            String a = last.getAssessment();
            if ("低于预期".equals(a)) h.put("lower", (int) h.get("lower") + 1);
            else if ("优于预期".equals(a)) h.put("upper", (int) h.get("upper") + 1);
            else h.put("normal", (int) h.get("normal") + 1);
        }
        return h;
    }
}
