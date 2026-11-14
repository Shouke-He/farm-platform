package com.farm.platform.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm.platform.entity.Plant;
import com.farm.platform.mapper.PlantMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** 智能助手会话层：管会话历史、拼调用、兜降级。  */
@Slf4j
@Service
@RequiredArgsConstructor
// 🔴 这一行不能省（2026-09-29 Task 8 实测抓到的）。本类注入 ChatClient，而 ChatClient 这个 bean 只在
@ConditionalOnProperty(name = "farm.agent.enabled", havingValue = "true")   // agent.enabled=true 才装配
public class AgentService {

    private final ChatClient agentChatClient;

    /** 由 AgentConfig 收集好的工具回调（反射解析只做一次）；每轮对话再各自包一层 TracedToolCallback */
    private final ToolCallback[] farmToolCallbacks;

    /** 读数闸门二次命中时的确定性兜底要用它 —— 直接查一次真实读数，去覆盖模型编出来的数字。 */
    private final FarmTools farmTools;

    /** 只为了拼「该用户名下有哪些盆栽」那段 system 事实。 */
    private final PlantMapper plantMapper;

    @Value("${farm.agent.timeout-seconds:30}")   // 超时（秒）
    private int timeoutSeconds;

    /** 读数闸门（{@link ReadingGuard}）开关：{@code off} / {@code dry-run} / {@code on}，默认 {@code dry-run}。 */
    @Value("${farm.agent.guard:dry-run}")   // 护栏模式
    private String guardMode;

    /** 单次对话保留的最大消息数（一问一答算 2 条），防止把上下文撑爆 */
    private static final int MAX_MESSAGES = 20;

    /** key = userId + ":" + conversationId，value = 交替的 user/assistant 消息（多轮的来源） */
    private final Map<String, List<Message>> history = new ConcurrentHashMap<>();   // 【会话记忆】

    /** 预置答案：问题原文 → 回答。见 loadFallback() 上方关于"只放通用问答"的红线说明 */
    private Map<String, String> fallback = Map.of();

    /** 缓存答案前面的标记：气泡里也要能看出这是离线预置的，"离线模式"角标滚动后可能不在视野里 */
    private static final String OFFLINE_PREFIX = "（离线预置回答）";

    /** 加载预置答案（三级降级里的第 ②级）。 */
    @PostConstruct
    void loadFallback() {
        try (var in = new ClassPathResource("agent-fallback.json").getInputStream()) {
            Map<String, String> loaded = new ObjectMapper()
                    .readValue(in, new TypeReference<Map<String, String>>() {});
            fallback = Map.copyOf(loaded);
            log.info("智能助手预置答案已加载：{} 条", fallback.size());
        } catch (Exception e) {
            // 预置答案只是锦上添花，读不到就退到第 ③级固定话术，绝不能因此让整个模块起不来
            log.warn("预置答案加载失败（不影响正常功能，失败时会话退到固定话术）：{}", e.getMessage());
        }
    }

    public ChatReply chat(Long userId, Long roleId, String conversationId, String message) {
        String key = userId + ":" + (conversationId == null || conversationId.isBlank() ? "default" : conversationId);
        List<Message> msgs = history.computeIfAbsent(key, k -> new ArrayList<>());   // 【多轮记忆】

        // 带上历史 → 这就是「多轮对话」的实现：把之前几轮一起发给模型
        List<Message> sending = new ArrayList<>(msgs);
        sending.add(new UserMessage(message));

        // 数字溯源的底账（见 ReadingGuard 第 2 版判据）：模型只可能从"这次发给它的东西"里学到数字。（【读数复读】）
        // 🔴 2026-10-16 修：**只收用户说过的话**。原来把助手自己的历史回答也算作"出处"，
        // 于是模型复述上一轮自己报的读数（如 24.5%）被判为"有出处"而放行 ——
        // 实测用户连问三次「土壤湿度」，三次都在复读同一个过期数字。
        StringBuilder ctx = new StringBuilder();
        for (Message m : sending) {
            if (m instanceof UserMessage && m.getText() != null) {
                ctx.append(m.getText()).append('\n');
            }
        }
        String contextText = ctx.toString();

        // 这一轮真实被执行的工具（TracedToolCallback 写入）。工具可能跑在别的线程上，所以用同步列表（【装工具】）
        List<String> used = Collections.synchronizedList(new ArrayList<>());
        ToolCallback[] callbacks = Arrays.stream(farmToolCallbacks)
                .map(cb -> (ToolCallback) new TracedToolCallback(cb, used))
                .toArray(ToolCallback[]::new);

        // 「该用户名下有哪几盆」在发起请求前拼好：它是一段确定性事实，每轮都要进 system。
        String myPlants = myPlantsBlock(userId);

        // 每轮的 system 文本（铁律 + 今天几号 + 该用户名下盆栽）。system 在请求级是整体替换，必须每次拼全
        String systemText = AgentConfig.systemPrompt(LocalDateTime.now(), myPlants);

        try {
            String reply = ask(userId, roleId, sending, callbacks, systemText);

            // 读数闸门（判据与理由见 ReadingGuard 类注释）。dry-run 只判定+记录，回答原样返回
            String guard = ReadingGuard.SKIP;   // 【读数闸门】
            if (!isGuardOff()) {
                guard = ReadingGuard.check(reply, used, contextText);
                if (ReadingGuard.NO_TOOL.equals(guard)) {
                    // 不是错误，是根因频次：这一轮它压根没查库。dry-run 要量的就是这个比例
                    log.info("读数闸门统计：本轮未调用任何工具。reply={}", brief(reply));
                } else if (ReadingGuard.HIT.equals(guard)) {
                    log.warn("读数闸门命中：本轮未调用任何工具，回答里却出现读数。mode={} toolsUsed={} reply={}",
                            guardMode, used, brief(reply));
                    if (isGuardBlocking()) {
                        // 清空 → 让 toolsUsed 反映重试那一轮，而不是把两次的工具混在一起报给前端
                        used.clear();
                        reply = ask(userId, roleId, sending, callbacks,
                                systemText + AgentConfig.READING_RETRY_NUDGE);
                        guard = ReadingGuard.check(reply, used, contextText);
                        if (ReadingGuard.HIT.equals(guard)) {
                            // 🔴 二次命中 = 它坚持不查库。原先这里"原样返回"，实测就是这条把编造的读数端给了用户
                            String fact = deterministicReading(userId, reply);
                            if (fact != null) {
                                log.warn("读数闸门二次命中 → 已用确定性读数替换模型数字（不采用它的数字）。原回答={}", brief(reply));
                                reply = fact;
                                guard = ReadingGuard.REPLACED;
                            } else {
                                log.warn("读数闸门重试后仍命中，且回答未指向该用户名下植物，按原样返回（不替换）。reply={}", brief(reply));
                            }
                        }
                    }
                }
            }

            msgs.add(new UserMessage(message));
            msgs.add(new AssistantMessage(reply));
            trim(msgs);
   // 【降级兜底】
            return new ChatReply(reply, List.copyOf(used), false, guard);
        } catch (Exception e) {
            log.warn("智能助手调用失败：{}", e.getMessage());
            return degrade(message);
        }
    }

    /** 发一次模型请求。 */
    private String ask(Long userId, Long roleId, List<Message> sending,
                       ToolCallback[] callbacks, String systemText) throws Exception {
        // 硬超时：模型挂住时不能让请求线程一直挂着（前端 60 秒就会断，用户看到的是"没反应"）
        return CompletableFuture
                .supplyAsync(() -> agentChatClient.prompt()
                        // system 在请求级是整体替换，所以传进来的是「铁律 + 今天几号 + 用户名下盆栽」的完整文本：
                        .system(systemText)
                        .messages(sending)
                        .toolCallbacks(callbacks)   // 【身份来自令牌】
                        // 🔴 归属校验的唯一来源。userId 给只读工具按用户过滤用；
                        .toolContext(Map.of("userId", userId, "roleId", roleId == null ? 2L : roleId))
                        .call()
                        .content())
                .get(timeoutSeconds, TimeUnit.SECONDS);
    }
   // 【编数字】
    /** 二次命中时的确定性兜底：只有当这段回答里出现了**该用户名下真实存在的**植物名，才拿真实读数覆盖它。 */
    private String deterministicReading(Long userId, String reply) {
        try {
            Plant p = farmTools.plantMentionedIn(userId, reply);
            if (p == null) {
                return null;
            }
            String fact = farmTools.sensorReadingText(userId, p.getName());
            // 末尾补一句"这不是模型说的"：这条路径上 toolsUsed 是空的，
            return fact + "（这条读数由系统直接读取设备最新上报，不是模型生成的。）";
        } catch (Exception e) {
            log.warn("确定性读数兜底失败（保持原回答）：{}", e.getMessage());
            return null;
        }
    }

    /** 读数闸门是否整个关掉（off = 连判定都不判） */
    private boolean isGuardOff() {
        // "false" 也算关：YAML 1.1 会把裸写的 off 解析成布尔 false，注入 String 字段就成了 "false"
        String v = guardValue();
        return "off".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
    }

    /** 读数闸门是否真拦（只有 on 才拦；其余值一律当 dry-run，只判定不改行为） */
    private boolean isGuardBlocking() {
        // 🔴 "true" 必须一起认（2026-09-30 深夜修）：application.yml 里写的是 `guard: on`，
        String v = guardValue();
        return "on".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v);
    }

    /** 配置取值容错：写错（"ON "、"true"、留空）都不该让对话挂掉，一律退化成 dry-run */
    private String guardValue() {
        return guardMode == null ? "" : guardMode.trim();
    }

    /** 日志里截断长回答：整段回答进日志会把真正有用的上下文冲掉 */
    private static String brief(String s) {
        if (s == null) {
            return "null";
        }
        String t = s.replaceAll("[\\r\\n]+", " ").trim();
        return t.length() <= 120 ? t : t.substring(0, 120) + "…";
    }

    /** 拼「该用户名下有哪些盆栽」这段 system 事实。 */
    private String myPlantsBlock(Long userId) {
        try {
            if (userId == null || userId < 0) {
                return null;
            }
            List<Plant> mine = plantMapper.selectList(
                    new LambdaQueryWrapper<Plant>()
                            .eq(Plant::getUserId, userId)
                            .orderByAsc(Plant::getId));      // 定序：同一用户每次拼出来必须一样
            List<String> names = new ArrayList<>();
            for (Plant p : mine) {
                String n = sanitize(p.getName());
                if (!n.isEmpty()) {
                    names.add(n);
                }
            }
            return AgentConfig.myPlantsBlock(names);
        } catch (Exception e) {
            log.warn("盆栽清单查询失败（本轮不带清单，不影响对话本身）：{}", e.getMessage());
            return null;
        }
    }

    /** 植物名是用户自己填的自由文本，而它马上就要被拼进 system prompt —— */
    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("[\\r\\n\\t]", " ").trim();
        return s.length() <= 20 ? s : s.substring(0, 20) + "…";
    }

    /** 只保留最近 MAX_MESSAGES 条（去掉最老的，保证最近几轮永远在上下文里） */
    private void trim(List<Message> msgs) {
        while (msgs.size() > MAX_MESSAGES) {
            msgs.remove(0);
        }
    }

    /** 三级降级（设计文档第七节）： */
    private ChatReply degrade(String message) {
        String cached = message == null ? null : fallback.get(message.trim());
        if (cached != null) {
            // guard 记 skip 而不是 ok：这一轮根本没走模型，闸门没有参与判断 ——
            return new ChatReply(OFFLINE_PREFIX + cached, List.of("fallback-cache"), true, ReadingGuard.SKIP);
        }
        return new ChatReply("智能助手暂时不可用，请稍后再试。系统其它功能不受影响。", List.of(), true, ReadingGuard.SKIP);
    }
}
