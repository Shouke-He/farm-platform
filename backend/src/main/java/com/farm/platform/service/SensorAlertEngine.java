package com.farm.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.farm.platform.entity.Device;
import com.farm.platform.entity.Plant;
import com.farm.platform.entity.PlantSpecies;
import com.farm.platform.entity.Reminder;
import com.farm.platform.mapper.PlantMapper;
import com.farm.platform.mapper.PlantSpeciesMapper;
import com.farm.platform.mapper.ReminderMapper;
import com.farm.platform.ws.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/** 传感器告警引擎：每条遥测到达时按阈值判定。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorAlertEngine {

    /** 阈值（后续可做成页面可配置的规则引擎） */
    private static final BigDecimal SOIL_DRY = new BigDecimal("25");          // 土壤干旱（建议灌溉）
    private static final BigDecimal SOIL_DRY_SEVERE = new BigDecimal("18");   // 严重干旱（立即灌溉）
    private static final BigDecimal SOIL_RECOVER = new BigDecimal("28");      // 回升至该值视为恢复
    private static final BigDecimal HOT_DRY_TEMP = new BigDecimal("35");      // 高温干旱：气温≥35℃ 且
    private static final BigDecimal HOT_DRY_HUM = new BigDecimal("30");       // 土壤湿度<30%（通用设备的"高温偏干"线）

    /** 品种折算比例（2026-10-02 新增，） */
    private static final BigDecimal DRY_RATIO = new BigDecimal("0.8");
    private static final BigDecimal SEVERE_RATIO = new BigDecimal("0.6");
    private static final BigDecimal RECOVER_GAP = new BigDecimal("3");

    private final ReminderMapper reminderMapper;
    private final NotificationService notificationService;
    private final PlantMapper plantMapper;              // 反查"这台设备绑了哪盆植物"（2026-10-02 十.152）
    private final PlantSpeciesMapper speciesMapper;     // 再按品种取土壤湿度下限

    /** 判一次告警：入参是「设备 + 本次遥测里通过净化的指标」。 */
    public void evaluate(Device device, JsonNode node) {
        BigDecimal sh = num(node, "soil_humidity");   // sh = soil humidity，本次上报的土壤湿度；没这个指标就是 null
        // 判定线：优先按「这台设备绑的那盆植物」的品种下限折算，没绑植物才用通用警戒线（读数为空时不查库）
        SoilLine line = sh == null ? null : soilLine(device);   // 【判告警】
        if (sh != null) {
            // 三条干旱相关规则共用同一个标题（设备名·土壤干旱）→ 它们互相构成"同一件事的不同程度"：
            String title = device.getDeviceName() + "·土壤干旱";
            if (sh.compareTo(line.severe()) < 0) {
                // 规则①：低于严重线，最高优先级
                fire(device, title, "严重干旱",
                        "土壤湿度 " + strip(sh) + "%，低于" + line.severeDesc() + "，请立即灌溉，防止作物萎蔫减产。", "urgent");
            } else if (sh.compareTo(line.dry()) < 0) {
                // 规则②：严重线 ~ 一般线，一般干旱
                fire(device, title, "土壤干旱",
                        "土壤湿度 " + strip(sh) + "%，低于" + line.dryDesc() + "，建议及时灌溉保墒。", "important");
            } else if (sh.compareTo(line.recover()) >= 0) {
                // 规则③：回到恢复线以上判恢复（一般线与恢复线之间落到这里之外，什么都不做——这就是缓冲带）
                recover(device, title, "土壤湿度已回升至 " + strip(sh) + "%，干旱解除。");
            }
        }
        BigDecimal at = num(node, "air_temp");   // at = air temperature
        if (at != null && sh != null) {
            // 规则④：高温 + 土壤偏干同时成立才告警（"又热又干"才是真正的风险场景）
            BigDecimal hotHum = line.speciesMin() != null ? line.speciesMin() : HOT_DRY_HUM;
            if (at.compareTo(HOT_DRY_TEMP) >= 0 && sh.compareTo(hotHum) < 0) {
                // 标题与「土壤干旱」不同 → 去重键不同，两条告警可以并存
                fire(device, device.getDeviceName() + "·高温干旱",
                        "高温干旱",
                        "气温 " + strip(at) + "℃且土壤湿度仅 " + strip(sh) + "%，蒸腾加剧，注意错峰灌溉并遮阳保墒。", "important");
            }
        }
    }

    /** 土壤湿度判定线。 */
    private record SoilLine(BigDecimal dry, BigDecimal severe, BigDecimal recover,
                            BigDecimal speciesMin, String dryDesc, String severeDesc) {
    }

    /** 由品种的土壤湿度下限折算出三条线（纯函数、无依赖，便于单测与反射探针直接验）。 */
    static BigDecimal[] linesOf(int soilMin) {
        BigDecimal base = BigDecimal.valueOf(soilMin);
        BigDecimal dry = base.multiply(DRY_RATIO).setScale(0, RoundingMode.HALF_UP);
        BigDecimal severe = base.multiply(SEVERE_RATIO).setScale(0, RoundingMode.HALF_UP);
        return new BigDecimal[]{ dry, severe, dry.add(RECOVER_GAP) };
    }

    /** 这台设备的土壤湿度判定线：先看设备绑了哪盆植物，取它们品种下限里**最小**的那个来折算。 */
    private SoilLine soilLine(Device device) {
        Integer min = null;
        String speciesName = null;
        List<Plant> plants = plantMapper.selectList(new LambdaQueryWrapper<Plant>().eq(Plant::getDeviceId, device.getId()));
        for (Plant p : plants) {
            if (p.getSpeciesId() == null) {
                continue;
            }
            PlantSpecies sp = speciesMapper.selectById(p.getSpeciesId());
            if (sp == null || sp.getSoilMin() == null) {
                continue;
            }
            if (min == null || sp.getSoilMin() < min) {
                min = sp.getSoilMin();
                speciesName = sp.getName();
            }
        }
        if (min == null) {
            return new SoilLine(SOIL_DRY, SOIL_DRY_SEVERE, SOIL_RECOVER, null, "适宜值 25%", "警戒线 18%");
        }
        BigDecimal[] l = linesOf(min);
        return new SoilLine(l[0], l[1], l[2], BigDecimal.valueOf(min),
                String.format("「%s」适宜下限 %d%% 的八成（%s%%）", speciesName, min, strip(l[0])),
                String.format("「%s」适宜下限 %d%% 的六成（%s%%）", speciesName, min, strip(l[1])));
    }

    /** 取数值字段：字段缺失、为 JSON null 或非数字时返回 null。 */
    private BigDecimal num(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && n.isNumber()) ? n.decimalValue() : null;
    }

    /** 告警等级权重，用于判断是否需要升级：urgent=3 最紧急，important=2，其余（normal 等）=1 */
    private static int rank(String priority) {
        if ("urgent".equals(priority)) {
            return 3;
        }
        if ("important".equals(priority)) {
            return 2;
        }
        return 1;
    }

    /** 触发告警：同设备同标题未处理告警存在时去重，但等级升高则原地升级（干旱 → 严重干旱）。 */
    private void fire(Device device, String title, String key, String content, String priority) {
        // 找该设备该告警是否已有「未处理」的记录（同标题 → 同设备同一种告警）
        Reminder pending = reminderMapper.selectOne(new LambdaQueryWrapper<Reminder>()   // 【告警去重】   // 【刷屏】
                .eq(Reminder::getType, "sensor")
                .eq(Reminder::getTitle, title)
                .eq(Reminder::getStatus, "pending")
                .orderByDesc(Reminder::getId)
                .last("LIMIT 1"));
        if (pending != null) {
            // 已有未处理告警 → 不新增记录，只在"等级变高"时原地升级
            if (rank(priority) > rank(pending.getPriority())) {
                // 升级：复用原来那条记录（改等级 + 换文案 + 刷新时间），不新增第二条
                String from = pending.getPriority();
                pending.setPriority(priority);
                pending.setContent(content);
                pending.setRemindTime(LocalDateTime.now());
                reminderMapper.updateById(pending);
                log.info("传感器告警升级: {} | {} → {}", title, from, priority);
                // 升级也要重新推一次——否则用户永远不会知道情况变严重了
                Long owner = device.getUserId();
                if (owner != null) {
                    notificationService.sendToUser(owner, "sensor", title, content);
                }
            }
            // 等级没升高的重复告警：直接返回，不写库也不推送（这就是"同标题去重不刷屏"）
            return;
        }
        // 没有未处理告警 → 新建一条 sensor 类型的提醒
        Reminder r = new Reminder();
        r.setTitle(title);
        r.setType("sensor");
        r.setContent(content);
        r.setRemindTime(LocalDateTime.now());
        r.setRepeatType("none");   // 告警是一次性事件，不参与每日重复重置
        r.setPriority(priority);
        r.setStatus("pending");
        r.setSource("物联网传感器");
        // 告警归属设备主人：个人设备告警只推给设备拥有者；admin 全局设备（user_id=NULL）仅管理员可见
        r.setUserId(device.getUserId());
        reminderMapper.insert(r);   // 【建提醒】
        log.info("传感器告警触发: {} | {}", title, key);
        // 定向推送给设备主人；admin 全局设备（user_id=NULL）不推送（管理端列表可见）
        Long ownerId = device.getUserId();
        if (ownerId != null) {
            notificationService.sendToUser(ownerId, "sensor", title, content);   // 【实时推】
        }
    }

    /** 恢复：把该设备同类未处理告警置 cancelled（已完成闭环：触发 → 处理 → 指标恢复）。 */
    private void recover(Device device, String title, String content) {
        Reminder pending = reminderMapper.selectOne(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getType, "sensor")
                .eq(Reminder::getTitle, title)
                .eq(Reminder::getStatus, "pending")
                .orderByDesc(Reminder::getId)
                .last("LIMIT 1"));
        if (pending != null) {
            pending.setStatus("cancelled");   // 注意是 cancelled（系统自动取消），不是 done（用户手动完成）
            reminderMapper.updateById(pending);
            log.info("传感器告警恢复: {}", title);
        }
    }

    /** 数值转展示文本：去掉多余的小数尾零（如 25.00 → "25"、18.50 → "18.5"），让短信文案更好看 */
    private String strip(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
