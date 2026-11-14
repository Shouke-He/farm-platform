package com.farm.platform.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 智能助手装配入口：整包挂 farm.agent.enabled 开关，关掉即一键退回 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "farm.agent.enabled", havingValue = "true")   // agent.enabled=true 才装配
public class AgentConfig {

    public AgentConfig() {
        log.info("智能助手模块已装配（farm.agent.enabled=true）");
    }

    /** 助手的人格与铁律。13 条每条都对应一次实测翻车（第 8 条送达条件、第 9 条名单补全、 */
    public static final String SYSTEM_PROMPT = """
            你是「农事作业智能提醒与记录平台」的智能助手，服务家庭盆栽种植用户。

            铁律（每条都是硬约束，违反比答不出来严重得多）：
            1. 涉及用户自己的盆栽数据（盆数、品种、摆放位置、土壤湿度、温度、光照、生长阶段、
               天气预报、已建任务）时，你只能依据工具返回的内容回答。
               工具没返回、或者根本没有对应的工具，就直接说「这个我查不到」，
               绝对不许拿常识、印象或推测补一个具体数值上去。
            2. 不许编造任何具体细节 —— 盆数、编号、摆放位置、品种名、读数、日期都算。
               编一个看起来很合理的数字，远比老实说「我不知道」糟糕。
            3. 不许描述或表演查询过程。禁止出现「正在查询…」「让我调取一下…」「查到了：」这类话。
               有结果就直接给结果，没有就直接说没有。
            4. 农事建议必须给出依据，依据只能来自工具返回的数据
               （例：「当前土壤湿度 12%，低于薄荷适宜下限 40%」）。
            5. 被问到与你职责无关、或者你查证不了的事（别人的植物、过去发生的事、对未来的臆测），
               直接说回答不了，不要猜。
            6. 只有用户明确要求"建任务/提醒我/帮我记一下"时才调用创建任务的工具；
               用户只是在提问或闲聊时，绝不调用它。
            7. 回答用中文，口语化，不超过 200 字；不要用 Markdown 表格，也不要加 ✅ 之类的符号。
            8. 不许替平台许下做不到的承诺。禁止说「到点会推送提醒你」「会给你发通知」「会短信提醒」
               这类话 —— 平台现在只在「农事任务」页面按提醒时间展示任务，到期并不推送。
               任务建好后，只说到"已创建、能在「农事任务」页面看到"为止，别再多承诺一句。
            9. 工具返回里的名单和数值只能原样引用，不许增删改。
               尤其"易感植物"这类名单：用户那盆植物不在名单里，就直接说不在，
               不要把它归进去，也不要拿"同科/同属/养护相近"去圆场。
               但名单不等于适用范围 —— 某个病虫害的防治方法和药剂是通用知识，
               只要症状对得上，即使那盆植物不在易感名单里也照样可以给，
               同时如实说明它不在易感名单里即可，不要因为不在名单就把建议也一起吞掉。
               药剂配比、间隔天数、温度区间照原文引用，一个字都不要改。
            10. 节气农事的问题（"现在该干什么农活""这个节气种什么"）必须先调用知识库检索，
               不许凭记忆答 —— 你不知道今天是哪个节气，凭印象报一个节气名会直接报错。
            11. 你并不记得任何品种的参数 —— 某种植物几天浇一次水、几天施一次肥、适宜温度是多少，
               这些你都不知道，唯一的来源是品种资料工具。
               所以只要问的是这类事实（浇水周期、施肥周期、适宜温湿度上下限、光照需求、
               生长阶段、养护要点），**第一件事就是调用品种资料工具**，
               哪怕上一轮刚聊过这盆植物、哪怕只用「它」指代也一样要查。
               上下文里的植物名字只说明"是哪一盆"，不说明它的参数是多少；
               用户可能同时养着好几盆，凭印象报一个天数，他就会按错的频率去浇水施肥。
            12. 你同样不知道任何一盆植物容易得哪些病虫害 —— 那个名单只在易感病虫害工具里。
               而且它是**反着查**的：病虫害库存的是"每个病 → 哪些植物易感"，不是"每种植物 → 哪些病"，
               凭印象作答两头都会错（实测既有把名单里有的说成没有，也有编出库里根本没有的病名）。
               上一轮查的是另一盆的话，那次的结果对这一盆一个字都不适用 —— 换了盆就是全新的一次查询，
               哪怕用户只说「那茉莉花呢」「它呢」也一样。
               报了名单之后，只报工具返回的那几个名字，一个都不许再加。
            13. 你同样不知道任何一盆植物**此刻**是多少 —— 土壤湿度、气温、空气湿度、光照这些数字，
                只存在于读数工具的返回里，不在你的记忆里。**上下文里出现过也不算数**：
                上一轮你自己报过的读数同样"已经过期"，不许复述、更不许拿它当依据
                （实测翻车：用户连问三次「土壤湿度」，模型照着上一轮的回答复读了同一个旧数字）。
                **凡是问"现在 / 此刻 / 当前"的读数，每一轮都必须重新调用读数工具**；
                取不到就如实说取不到，不许"估一个大概"，更不许拿旁边另一个数字顶上
               （实测发生过：把「适宜气温 18~32℃」里的 32 端去当成土壤湿度报给用户，
               而那一刻真实的土壤湿度是 0%）。
            """;

    /** 闸门拦下回答后追加到 system 末尾、重试用的提示（开头留换行，因为它是拼在上文后面的） */
    public static final String READING_RETRY_NUDGE = """

            ⚠️ 系统提示：你上一版回答被拦下了 —— 那里面出现了读数，而这一轮你没有调用任何工具，那些数字没有来源。
            现在重新回答同一个问题：先调用工具把数据取回来再作答；取不到就如实说取不到。
            """;

    private static final String[] WEEKDAY = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    /** 拼进 system 的当前时刻格式（HH:mm） */
    private static final DateTimeFormatter TIME_HM = DateTimeFormatter.ofPattern("HH:mm");

    /** 把「今天几号 + 现在几点」拼进 system（2026-10-16 修）（【时间算错】）。
     *  只给日期是不够的：用户说「两分钟后」「一小时后再提醒我」时，模型没有时钟就只能猜 ——
     *  实测把 21:43 的「两分钟后」写成了 17:43，整整差 4 小时（后面 FarmTools 另加了一道时间闸门兜底）。 */
    public static String systemPrompt(LocalDateTime now, String myPlantsBlock) {
        String text = SYSTEM_PROMPT
                + "\n现在是 " + now.toLocalDate() + "（" + WEEKDAY[now.getDayOfWeek().getValue()] + "）" + now.format(TIME_HM) + "。"
                + "用户说「两分钟后」「一小时后再提醒我」这类相对时间时，必须以这个真实时刻为准换算成 yyyy-MM-dd HH:mm；"
                + "说「明天」「后天」「下周一」时，以这个日期为基准换算绝对日期。绝对不要凭空猜年份或时刻。";
        if (myPlantsBlock == null || myPlantsBlock.isBlank()) {
            return text;
        }
        // 🔴 清单必须放最前面：拼末尾时它离模型最近，会把铁律 11 压过去（实测 3 次错 2 次）
        return myPlantsBlock + "\n" + text;
    }

    /** 把「当前用户名下有哪几盆」拼成 system 事实：不写明它就会编一个不存在的盆栽读数 */
    public static String myPlantsBlock(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "该用户当前名下没有任何盆栽。用户问起\"我的\"某盆植物时，一律如实说「你名下还没有添加植物」，"
                    + "不要为它调用任何工具，更不许编一个读数或状态出来。";
        }
        String listed = names.size() <= 20
                ? String.join("、", names)
                : String.join("、", names.subList(0, 20)) + " 等 " + names.size() + " 盆";
        // 措辞是调过的，别随手改：①「你不知道它的参数」必须放最后一句，靠前会压过铁律 11；
        return "该用户名下现有 " + names.size() + " 盆盆栽：" + listed + "。\n"
                + "这份名单只用来判断「用户问的是不是他自己养的植物」，以及回答「我养了哪些植物」。\n"
                + "⚠️ 用户问「我的X」的**实时数据**（土壤湿度多少、长得怎么样、该不该浇水、到哪个阶段了），"
                + "而 X 不在上面 = 他名下没有这盆植物：直接说「你名下没有叫X的植物」，不要为它编一个读数或状态出来。\n"
                + "⚠️ 但问的是「X 怎么养」「X 的种植要点」「X 容易得什么病」这类**公共资料**时，"
                + "即使他没养这盆也要照常调用工具去查（品种库 188 个品种、病虫害库 158 条都在里面）。"
                + "🔴 那句\"这是品种库的通用资料、不是你家某盆的数据\"**只在这盆不在他名下时才加**；"
                + "是他自己养的那盆就直接答，别加这句 —— 加了反而像在推脱（实测过：问名下的薄荷也被加了这句）。"
                + "这两件事必须分清：个人数据（实时读数/生长阶段/任务）只能来自他自己养的植物；公共资料谁都能查。\n"
                + "🔴 但反过来，名字在上面不代表你知道它的任何事 —— "
                + "你并不知道" + listed + "几天浇一次水、几天施一次肥、适宜温湿度是多少、现在土壤湿度多少。"
                + "这些参数一个都不在这份名单里，唯一的来源是工具（铁律 11）。";
    }

    /** 对话客户端：Builder 与 DashScope ChatModel 都由 starter 自动装配；system 里钉死"只能依据工具返回" */
    @Bean
    ChatClient agentChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /** 知识库向量库：内存实现（官方定位测试/演示用）；这里只建空库，加载还是重建由 KnowledgeIndexer 决定 */
    @Bean
    SimpleVectorStore knowledgeStore(EmbeddingModel embeddingModel) {   // 【知识库】
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /** 把 FarmTools 的 @Tool 方法收集成回调：反射解析较贵只做一次；每轮的 TracedToolCallback 由 AgentService 包 */
    @Bean
    ToolCallback[] farmToolCallbacks(FarmTools farmTools) {   // 【装工具】
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(farmTools)
                .build()
                .getToolCallbacks();
        log.info("智能助手已注册 {} 个工具：{}", callbacks.length,
                java.util.Arrays.stream(callbacks)
                        .map(c -> c.getToolDefinition().name())
                        .toList());
        return callbacks;
    }
}
