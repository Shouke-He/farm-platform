package com.farm.platform.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读数闸门：这一轮是不是凭空报了个读数 —— 防的不是答错，是压根没查（演进与实测见 13 号册 §3.2） */
public final class ReadingGuard {

    private ReadingGuard() {
    }

    /** 调过工具，有据可依 */
    public static final String OK = "ok";

    /** 没调工具，但也没编读数（打招呼 / 如实说查不到 / 复述**用户自己说过**的数字）。
     *  ⚠️ 2026-10-16 起：出处只认"用户说过的话" —— 助手自己历史回答里的读数不算（那是自我引用）。 */
    public static final String NO_TOOL = "no-tool";

    /** 没调工具，且回答里的读数在上下文里找不到任何出处 */
    public static final String HIT = "hit";

    /** 重试仍命中，但那段回答指向名下真实存在的植物 —— 已换成代码直接查出的读数 */
    public static final String REPLACED = "replaced";

    /** 闸门没参与判断（降级 / 离线那条路径） */
    public static final String SKIP = "skip";

    /** 「数字 + 读数单位」；全角百分号也收（本项目被全角字符坑过一次） */
    private static final Pattern READINGS = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:%|％|℃|°C|°c|摄氏度|lux|lx|mm|毫米|度)");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** 千分位：「10,050」要当成 10050，否则会被拆成 10 和 050 */
    private static final Pattern THOUSANDS = Pattern.compile("(?<=\\d),(?=\\d)");

    /** 判据：调过工具 → OK；回答里没读数 → NO_TOOL；有读数就逐个溯源（出处只算**用户说过的话**），
     *  有一个找不到出处 → HIT。⚠️ contextText 由 AgentService 只拼用户消息（2026-10-16 修）。 */
    public static String check(String reply, List<String> toolsUsed, String contextText) {   // 【读数闸门】
        if (toolsUsed != null && !toolsUsed.isEmpty()) {
            return OK;
        }
        if (!looksLikeReading(reply)) {
            return NO_TOOL;
        }
        // 有读数又没查库 —— 逐个查出处，只要有一个数在上下文里找不到，就是编的
        Set<Double> known = numbersIn(contextText);
        for (Double v : readingValues(reply)) {
            if (!known.contains(v)) {
                return HIT;
            }
        }
        return NO_TOOL;
    }

    /** 回答里有没有「数字+读数单位」（public 是为了单测能直接打边界样本） */
    public static boolean looksLikeReading(String reply) {
        return reply != null && !reply.isBlank() && READINGS.matcher(reply).find();
    }

    /** 回答里所有读数的数值（逐个溯源用） */
    static List<Double> readingValues(String reply) {
        List<Double> out = new ArrayList<>();
        if (reply == null) {
            return out;
        }
        Matcher m = READINGS.matcher(THOUSANDS.matcher(reply).replaceAll(""));
        while (m.find()) {
            Matcher n = NUMBER.matcher(m.group());
            if (n.find()) {
                out.add(Double.parseDouble(n.group()));
            }
        }
        return out;
    }

    /** 一段文本里出现过的所有数值 —— 这就是"这个数字有没有出处"的底账 */
    static Set<Double> numbersIn(String text) {
        Set<Double> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        Matcher m = NUMBER.matcher(THOUSANDS.matcher(text).replaceAll(""));
        while (m.find()) {
            out.add(Double.parseDouble(m.group()));
        }
        return out;
    }

}
