package com.farm.platform.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 读数闸门判据的边界测试。 */
class ReadingGuardTest {

    /** 用户截图那次的两轮对话上下文（第 1 轮答的是"没有这盆植物"，**里面一个数字都没有**） */
    private static final String CTX_NO_NUMBERS = """
            我茉莉现在的土壤湿度是多少？
            你名下没有叫茉莉的植物，只有「横县茉莉花」。需要查它的土壤湿度吗？
            对
            """;

    /** dry-run 实测里那 3 次误报的上下文（第 1 轮真调了工具，回答里带着 0%） */
    private static final String CTX_WITH_0 = """
            我茉莉现在的土壤湿度是多少？
            横县茉莉花当前土壤湿度是0%，远低于适宜范围45%~65%，急需浇水。
            对
            """;

    // ---------------------------------------------------------------- ① 真正的目标：编读数

    @Test
    @DisplayName("上下文里没这个数字 + 没调工具 → 命中（2026-09-30 用户截图那次）")
    void fabricatedNumberWithNoSourceIsCaught() {
        String reply = "横县茉莉花现在的土壤湿度是32%，低于它适宜的45%~65%范围，建议尽快浇水。";
        assertEquals(ReadingGuard.HIT, ReadingGuard.check(reply, List.of(), CTX_NO_NUMBERS));
        assertEquals(ReadingGuard.HIT, ReadingGuard.check(reply, null, CTX_NO_NUMBERS));
    }

    @Test
    @DisplayName("只要有一个读数找不到出处就命中（不用等全部无出处）")
    void oneSourcelessReadingIsEnough() {
        // 45 和 65 在上下文里有（第 1 轮答过），32 没有 —— 32 就是编的那个
        String reply = "还是 45%~65% 这个范围，但现在是 32%。";
        assertEquals(ReadingGuard.HIT, ReadingGuard.check(reply, List.of(), CTX_WITH_0));
    }

    // ---------------------------------------------------------------- ② 复述 ≠ 编造（dry-run 误报的回归）

    @Test
    @DisplayName("🔴 复述上一轮的数字 → 放行（这就是 dry-run 实测的 3/3 误报，第 2 版必须放它过去）")
    void restatingNumbersFromContextIsNotFabrication() {
        String reply = "已确认横县茉莉花土壤湿度为0%，严重偏低，建议立即浇水。";
        assertEquals(ReadingGuard.NO_TOOL, ReadingGuard.check(reply, List.of(), CTX_WITH_0));
    }

    @Test
    @DisplayName("复述品种区间（45%~65%）同样放行")
    void restatingRangeIsFine() {
        String reply = "刚才说过，适宜范围是 45%~65%。";
        assertEquals(ReadingGuard.NO_TOOL, ReadingGuard.check(reply, List.of(), CTX_WITH_0));
    }

    @Test
    @DisplayName("上下文为空时退化成第 1 版行为（任何读数都算无出处）")
    void emptyContextFallsBackToStrict() {
        assertEquals(ReadingGuard.HIT, ReadingGuard.check("现在是 32%。", List.of(), ""));
        assertEquals(ReadingGuard.HIT, ReadingGuard.check("现在是 32%。", List.of(), null));
    }

    // ---------------------------------------------------------------- ③ 正常路径

    @Test
    @DisplayName("调过工具 + 报读数 → 正常（工具给的数就有据可依，不必溯源）")
    void readingWithToolIsFine() {
        String reply = "横县茉莉花当前土壤湿度是0%，远低于适宜范围45%~65%。";
        assertEquals(ReadingGuard.OK, ReadingGuard.check(reply, List.of("getSensorReading"), ""));
    }

    @Test
    @DisplayName("没调工具但没报读数 → 只记 no-tool，不算错")
    void noToolWithoutReadingIsNotAnError() {
        assertEquals(ReadingGuard.NO_TOOL,
                ReadingGuard.check("你名下没有叫白茉莉的植物。", List.of(), CTX_NO_NUMBERS));
        assertEquals(ReadingGuard.NO_TOOL,
                ReadingGuard.check("这个我查不到。", List.of(), CTX_NO_NUMBERS));
        assertEquals(ReadingGuard.NO_TOOL,
                ReadingGuard.check("你好，有什么可以帮你的？", List.of(), CTX_NO_NUMBERS));
    }

    @Test
    @DisplayName("调过工具但没报读数 → 正常")
    void toolWithoutReadingIsFine() {
        assertEquals(ReadingGuard.OK,
                ReadingGuard.check("已经帮你建好任务了。", List.of("createFarmTask"), ""));
    }

    // ---------------------------------------------------------------- 单位 / 数字解析

    @Test
    @DisplayName("各种读数单位都能认出来（含全角百分号）")
    void recognisesUnitVariants() {
        assertTrue(ReadingGuard.looksLikeReading("土壤湿度 0%"));
        assertTrue(ReadingGuard.looksLikeReading("土壤湿度 0％"), "全角百分号也要认 —— 本项目被全角字符坑过");
        assertTrue(ReadingGuard.looksLikeReading("气温 28.6℃"));
        assertTrue(ReadingGuard.looksLikeReading("气温 28.6°C"));
        assertTrue(ReadingGuard.looksLikeReading("光照 10050 lux"));
        assertTrue(ReadingGuard.looksLikeReading("雨量 0.5mm"));
        assertTrue(ReadingGuard.looksLikeReading("大约 30 度"));
    }

    @Test
    @DisplayName("裸数字一律不认 —— 宁可漏掉，也不能把正常回答拦下来")
    void bareNumbersDoNotTrigger() {
        assertFalse(ReadingGuard.looksLikeReading("每 3 天浇一次水"), "天数不是读数");
        assertFalse(ReadingGuard.looksLikeReading("回答不超过 200 字"));
        assertFalse(ReadingGuard.looksLikeReading("第 2 步：把花盆搬到阳台"));
        assertFalse(ReadingGuard.looksLikeReading("你名下现有 2 盆盆栽：横县茉莉花、薄荷"));
    }

    @Test
    @DisplayName("空回复不触发（走降级时 reply 可能为空）")
    void blankReplyDoesNotTrigger() {
        assertEquals(ReadingGuard.NO_TOOL, ReadingGuard.check(null, List.of(), ""));
        assertEquals(ReadingGuard.NO_TOOL, ReadingGuard.check("   ", List.of(), ""));
        assertFalse(ReadingGuard.looksLikeReading(null));
    }

    @Test
    @DisplayName("千分位要当成一个数：10,050 lux 不能在上下文里被拆成 10 和 050")
    void thousandsSeparatorIsOneNumber() {
        assertEquals(List.of(10050.0), ReadingGuard.readingValues("光照 10,050 lux"));
        assertTrue(ReadingGuard.numbersIn("光照 10050.00lux").contains(10050.0));
        // 上下文写 10050、回答写 10,050 —— 两边都归一化过，能对上
        assertEquals(ReadingGuard.NO_TOOL,
                ReadingGuard.check("光照 10,050 lux", List.of(), "上一轮读数：光照 10050.00lux"));
    }

    @Test
    @DisplayName("0 的写法不同也算同源：上下文 0.00% / 回答 0%")
    void zeroNormalisation() {
        assertEquals(ReadingGuard.NO_TOOL,
                ReadingGuard.check("土壤湿度 0%", List.of(), "土壤湿度 0.00%"));
    }

    // ---------------------------------------------------------------- ⚠️ 已知的误报风险样本

    @Test
    @DisplayName("⚠️ 误报风险①：模型凭常识答通用知识（会命中，但未必该拦）")
    void knownFalsePositive_generalKnowledge() {
        // 这题本来该查品种库；但用户问的是"一般"，模型答常识也算说得过去。
        assertEquals(ReadingGuard.HIT,
                ReadingGuard.check("薄荷一般在 15~30℃ 长得最好。", List.of(), "薄荷喜欢什么温度？"));
    }

    @Test
    @DisplayName("⚠️ 误报风险②：四舍五入（判据只做字符串级比对，接不住这类改写）")
    void knownFalsePositive_rounding() {
        // 上下文是 10050 lux，模型说"大约 10000 lux" —— 数值对不上，会被判成无出处。
        assertEquals(ReadingGuard.HIT,
                ReadingGuard.check("光照大约 10000 lux", List.of(), "上一轮读数：光照 10050.00lux"));
    }

    @Test
    @DisplayName("⚠️ 误报风险③：中文量级词（万/千）不被识别为单位，整段直接漏判")
    void knownFalsePositive_chineseMagnitude() {
        // "1.0 万 lux" 里「万」把数字和单位隔开了，正则匹配不上 → 判定为"没有读数" → 放行。
        assertFalse(ReadingGuard.looksLikeReading("光照大约是 1.0 万 lux"));
    }

    @Test
    @DisplayName("🔴 通用养护常识被误伤：这条会被判 HIT，所以闸门命中后只重试、绝不替换成兜底话术")
    void knownFalsePositive_generalCareAdvice() {
        // 2026-09-30 实测样本。模型自己写明"这不是你某盆植物的具体参数"，
        String reply = "如果你只是想了解常见室内花卉的大致安全温度：一般15~25℃比较稳妥，低于10℃多数怕冷的植物就容易出问题。";
        assertEquals(ReadingGuard.HIT,
                ReadingGuard.check(reply, List.of(), "冬天室内养花温度要多少？"));
    }
}
