# 农事智能助手（Agent 模块）设计方案

> 创建：2026-09-28 · 状态：**已与用户确认范围（中档），待评审后转实施计划**
> 相关：`开发记录.md` 十.107（选型调研）、`docs/系统讲解/`（现有系统讲解）
> ⚠️ 本文档创建时用户处于「暂时不连接 git」状态，**未提交**。
>
> ### 📌 实施后的变更（2026-09-30 补注）
> 本文是**设计当时的记录**，正文里的「5 个工具」就是当时的范围，**正文不再回头改**（保留设计轨迹）。
> 实施后实际是 **6 个工具**：2026-09-29 晚按用户实测反馈新增了 `getSusceptibleDiseases`
> （易感病虫害**确定性反查**），另把「该用户名下盆栽清单」拼进了 system prompt。
> **现状一律以 `docs/接口文档.md` §13 和 `开发记录.md` 十.120 为准。**

---

## 一、背景与目标

用户收到外部建议（截图）：把农事平台从「纯规则引擎」升级为「带 LLM Agent 的问答系统」。经调研（见 十.107），确认**技术可行**，用户已拍板：

- **时机**：演示前做完，**演示现场演示**
- **演示时间**：2026-10-11 ~ 10-15
- **范围**：中档（5 个工具 + RAG + 多轮对话 + 三级降级）

**目标一句话**：让用户能用自然语言问系统问题，系统**调真实数据**回答，而不是让大模型凭空编。

**这个定位很重要**，它是演示和面试的核心答法：

> 不让 LLM 直接判断农事（它会编），而是让它**调用我已经写好、可验证的规则引擎和数据库**，它只负责理解意图和组织语言。

---

## 二、范围

### 做

| # | 内容 |
|---|---|
| 1 | 后端 Spring AI 接入百炼（DashScope），**5 个 Function Calling 工具** |
| 2 | **RAG 知识库**：158 条病虫害 + 24 条节气 → 向量检索 |
| 3 | 用户端（5173）右下角**浮窗对话**，支持多轮 |
| 4 | **三级降级**（见第七节），保证断网也能演 |
| 5 | `farm.agent.enabled` 总开关，false 时系统与今天**完全一致** |

### 不做（YAGNI）

| 不做 | 为什么 |
|---|---|
| 管理端入口 | 演示演不出价值，却要多改一个工程 |
| 对话历史落库 | 内存会话撑得住演示；落库要建表、要迁移，收益低 |
| 流式输出（打字机） | 观感好但复杂度翻倍。**列入「有余量再说」**，不进 baseline |
| 独立向量数据库（Redis/PGVector） | 182 条知识用内存库绰绰有余，**演示当天少一个能挂的东西** |

---

## 三、版本护栏（装错就起不来）

| 组件 | 版本 | 为什么是这个 |
|---|---|---|
| Spring AI | **1.1.8** | 最新 2.0.1 要 Boot 4.x，本项目 Boot 3.5.3 **装不上**；官方兼容表 `1.1.x → Boot 3.5.x` |
| `spring-ai-alibaba-starter-dashscope` | **1.1.2.x** | 2.0.0-M1.1 依赖 Boot 4.0.0，**不可用** |
| Maven 仓库 | **不需要额外仓库** | 两者都已在 Maven Central |
| Spring Boot | **3.5.3 不动** | Spring AI 1.1.x 与 Boot 3.5.x **无 Jackson/Reactor 分叉**，没必要抬版本 |

**不用 LangChain4j**：核心稳但 Spring 集成层全线 beta30，且其 `jackson-databind 2.22.1` 会被 Boot 3.5.3 的 `2.19.1` 降级（是否崩未验证）。

---

## 四、架构与组件

新代码全部落在**新包** `com.farm.platform.agent`，分五层，每层单一职责、可独立测试：

```
agent/
├── AgentConfig.java        配置层：@Value 读 farm.agent.*；装配全部 Bean
│                           （整个包 @ConditionalOnProperty("farm.agent.enabled")）
├── AgentController.java    接口层：POST /api/agent/chat、GET /api/agent/status
├── FarmTools.java          工具层：5 个 @Tool，只调现有 Service，不含 AI 逻辑
├── KnowledgeIndexer.java   知识层：读 158+24 条 → embed → 写入向量库
└── AgentService.java       会话层：拼 system prompt + 历史 → 调模型 → 处理降级
```

**依赖方向严格单向**：Controller → Service → Tools / Indexer。Tools 只依赖**现有** Service（`PlantService` / `DeviceService` / `GrowthStageService` / `WeatherService` / `ReminderService`）。

### 配置写法要跟着项目习惯走

`application.yml` 里明确写着「**项目里没有 `@ConfigurationProperties` 类**」，配置一律 `@Value` 逐项注入 —— 新模块照办：

```yaml
farm:
  agent:
    enabled: false              # 总开关：false 时整个 agent 包不装配，系统与今天完全一致
    api-key: ${DASHSCOPE_API_KEY:}   # 优先取环境变量；取不到则用冒号后的默认值（空串）
    model: qwen-plus            # 对话模型
    embed-model: text-embedding-v4   # 向量模型（1024 维）
    timeout-seconds: 30         # 单次对话超时
    knowledge-file: ./backend/agent-knowledge.json
                                # 向量索引落盘路径（见第六节），与 upload-dir / backup.dir 同风格用绝对路径
```

**api-key 的两种放法**（实施时二选一，推荐前者）：

1. **环境变量** —— 在 `relaunch_backend.ps1` 里 `$env:DASHSCOPE_API_KEY = "..."` 后再启动。**key 不进任何文件**，最干净；但重启脚本多一步，演示前要确认设了
2. **直接写在 yml 的这行**（替换掉 `${DASHSCOPE_API_KEY:}` 整个表达式）—— 与现有 `farm.weather.key` 同款做法，最省事；代价是 key 落在文件里（私有仓库 + 已有同类先例，可接受）

> 无论哪种，**我（AI）全程不读取、不打印、不复述这个 key**。

### 五个工具

| 工具签名 | 模型何时调 | 复用的现有方法 |
|---|---|---|
| `getSensorReading(String plantName)` | 「我茉莉花现在土湿多少」 | `DeviceMapper.selectLatestByDevice(deviceId)` |
| `getPlantProfile(String plantName)` | 「茉莉花适合什么环境」 | `PlantSpecies` 的 `soilMin/soilMax/tempMin/tempMax/lightLevel` + `GrowthStageService.calc(sp, plantDate)` |
| `getWeather(String city)` | 「明天要下雨吗」 | `WeatherService.forecast3d(cityId)` |
| `createFarmTask(String plantName, String title, String remindTime)` | 「帮我建个明天浇水的任务」 | `ReminderService.create(Reminder)`（**内含归属校验**） |
| `searchKnowledge(String query)` | 「叶子发黄怎么办」 | **RAG 向量检索**（第六节） |

> 🔧 **2026-09-30 深夜补注（实施后）**：`getPlantProfile` 最初只在**用户名下**的植物里找，被问「草莓的种植要点」（品种库里有、他名下没有）时会答“系统里查不到它的品种资料”。现已补**品种库回退**：`findSpeciesByName` 查到就照给参数，但返回里必须写明「该用户名下并没有这盆植物」。分界线是 **个人数据（实时读数/生长阶段/任务）只认自家植物；公共资料（怎么养/容易得什么病）谁都能查**。详见 `开发记录.md` 十.136。

**两个设计决定，演示会被问：**

1. **工具入参收「名字」不收「ID」。** 模型拿到的是自然语言「我茉莉花」，让它猜 `plantId=2` 必错。名字→ID 的解析在工具内部做（模糊匹配 + 找不到时返回候选列表让模型追问）。
2. **RAG 为什么不用 SQL `LIKE`。** 用户问「叶子发黄」，库里写「叶片褪绿」—— `LIKE` 匹配不上，向量检索能。**这是 RAG 在此站得住的理由**，不是硬凑技术。

### 🔴 安全红线：工具必须做归属校验

工具能查数据库，**绝不能让 A 用户问出 B 用户的数据**。两条硬要求：

1. 每个工具在查数据前必须按**当前登录用户**过滤（`plant.userId == 当前用户`）
2. **不能依赖 `ThreadLocal` 式的 `UserContext`** —— Spring AI 执行工具时的线程不保证与 HTTP 请求线程相同，ThreadLocal 可能读到别的请求的值或空值。**userId 必须显式传参**（走 Spring AI 的 ToolContext 机制，或由 AgentService 在调用前把 userId 绑定进会话上下文）

> 实施时若发现 1.1.8 的 ToolContext API 与预期不符，退化方案：**只读工具改为「AgentService 先查出该用户的盆栽清单，把清单写进 system prompt，工具只接受清单内的名字」**。

---

## 五、数据流

```
用户输入（浮窗）
   ↓ POST /api/agent/chat  { conversationId, message }
AgentController      ← JWT 拦截器已识别当前用户（沿用现有机制，无需新写鉴权）
   ↓ userId + conversationId + message
AgentService         ← 取该会话历史（内存 Map）；组装 system prompt（含当前用户与盆栽清单）
   ↓
ChatClient（Spring AI）→ 百炼 qwen
   ↓ 模型决定调工具（可能 0~N 次）
FarmTools            ← 带 userId 归属校验，调现有 Service
   ↓ 工具结果回模型
生成回答
   ↓ { reply, toolsUsed: [...] }
前端浮窗渲染
```

**前端两处细节：**

- **必须覆盖超时**：现有 `frontend/src/api/index.ts` 的 axios 实例是全局 `timeout: 15000`，而带工具调用的大模型请求**必然超过 15 秒** → 单次请求传 `{ timeout: 60000 }` 覆盖，**不动全局值**（动了会波及所有页面）
- **入口形态**：右下角浮动按钮 + 抽屉面板，**挂在 `Layout.vue`** —— 这样在任何页面都能打开演示，不用切页
- 新组件放 `frontend/src/components/AgentChat.vue`，**唯一改动的现有文件是 `Layout.vue`（挂载点，约 2 行）**

---

## 六、RAG 实现

**索引内容**（约 182 条）：

- `plant_disease` 158 条 → 「名称 + 症状 + 防治方法」拼成一段文本
- `solar_term` 24 条 → 「节气名 + 农事要点」拼成一段文本

**Embedding**：百炼 `text-embedding-v4`（1024 维，0.0005 元/千 token）—— **与对话共用同一个 `DASHSCOPE_API_KEY`**，不需要第二套凭据。

**向量库**：Spring AI `SimpleVectorStore`（内存）。

> 官方对它的定位原文是 *"not designed for production use... only for testing or demonstration purposes"* —— **我要如实说明**：对一个 182 条的演示它完全够用，而且**演示当天少一个能挂的外部依赖**（不上 Redis/PGVector 是刻意的工程取舍，不是偷懒）。

**索引落盘**：构建向量库需要 182 次 embedding 调用，**不希望每次重启都重跑**（启动变慢 + 依赖网络）。方案：构建后落盘 JSON，启动时若文件存在直接加载。
> ⚠️ **未验证**：Spring AI 1.1.8 的 `SimpleVectorStore` 是否提供 `save/load` API。若没有，退化方案 = **每次启动重建索引**（182 次调用约几秒、成本可忽略），不影响功能。

**不做本地 ONNX embedding**：Spring AI 的 transformers starter 会引入 onnxruntime + DJL 且**首次调用联网下载模型**；LangChain4j 的中文小模型已疑似停更。用云端 embedding 反而更干净。

---

## 七、错误处理：三级降级

**这是演示能否演成的关键。**

| 级别 | 触发 | 行为 | 界面 |
|---|---|---|---|
| **① 正常** | 模型正常返回 | 真实调用工具、真实回答 | 正常 |
| **② 离线模式** | 超时 / 网络失败 / 限流 / key 失效 | 查**预置答案缓存**（演示脚本里的问题，提前跑好存盘） | **显式打「离线模式」小标记** |
| **③ 不可用** | 预置也没有 | 固定话术「智能助手暂时不可用」 | 提示，**系统其余部分完全不受影响** |

**「离线模式」标记必须显示，不能藏。** 理由有二：

1. **诚实**：用缓存冒充实时结果是欺骗，演示被看出来是致命的
2. **加分**：降级策略本身就是工程能力。网络真挂了，可以指着说「看，降级生效了」—— **把风险变成亮点**

**演示脚本（约 6-8 问，覆盖全部 5 个工具 + 一轮追问）**，每问提前跑通并把答案写进预置缓存。

---

## 八、验收标准

| # | 判据 |
|---|---|
| 1 | **9/30 晚上**：对着电脑问「我茉莉花该浇水吗」，能答一句 —— **最小闭环达成** |
| 2 | 5 个工具全部能被正确触发（问对应问题 → 日志/返回体显示调了哪个工具） |
| 3 | RAG：问「叶子发黄怎么办」能召回相关病虫害条目 |
| 4 | 多轮：追问「那要打什么药」能理解上文 |
| 5 | **归属校验**：用 worker1 的账号问不出其他用户的盆栽数据 |
| 6 | **降级**：手动把 key 改错 → 界面出「离线模式」+ 预置答案（**必须实测这一条**） |
| 7 | **回归**：`farm.agent.enabled=false` 重启 → 逐项点一遍 **① 登录 ② 环境监控（曲线/告警） ③ 我的种植（阶段/抽屉） ④ 农事任务（确认处理） ⑤ WebSocket 推送**，全部与今天一致 |
| 8 | 完整排练 ≥ 2 次 |

---

## 九、改动边界（铁律：只加不改）

| 类型 | 文件 | 说明 |
|---|---|---|
| **新增** | `backend/.../agent/` 整包（5 个类） | 全新 |
| **新增** | `frontend/src/components/AgentChat.vue` | 全新 |
| **新增** | `docs/Agent模块设计方案.md`（本文档） | 全新 |
| **改现有** | `backend/pom.xml` | 加 2 个依赖 |
| **改现有** | `backend/.../application.yml` | 加 `farm.agent` 配置块 |
| **改现有** | `frontend/src/views/Layout.vue` | 挂载点，约 2 行 |
| **零改动** | 管理端 `frontend-admin/` 全部 | — |
| **零改动** | 演示数据、数据库结构 | **不建表、不改表** |

**一键关闭**：`farm.agent.enabled=false` → 整个 agent 包不装配，系统回到 2026-09-28 这个已验证状态。

---

## 十、工期与里程碑

| 日期 | 交付 | 出口判据 |
|---|---|---|
| **9/29（Day 1）** | 依赖 + 配置 + `ChatClient` 打通 + `/api/agent/chat` 返回纯文本 | curl 能拿到模型回话 |
| **9/30（Day 2）** | 前端浮窗 + 端到端 | **能问一句、答一句** ← 最小可演示达成 |
| **10/1–10/7** | 5 个工具 + RAG 索引 + 多轮 | 验收 2/3/4/5 过 |
| **10/8–10/10** | 三级降级 + 演示脚本 + 排练 | 验收 6/7/8 过 |

**前置依赖（用户侧）**：阿里云百炼账户**充值 + 新建 API key**。用户现有 key 是 vision skill 的千问 VL，**已欠费停用**。

---

## 十一、风险与未决项

| 风险 | 影响 | 应对 |
|---|---|---|
| 百炼账户充不上值 | **阻塞** | 改走 DeepSeek（OpenAI 兼容，改 base-url 即用） |
| 演示现场断网/限流 | 演示翻车 | 三级降级 + 预置缓存 + 排练 |
| 工具线程与 `UserContext` 不兼容 | 归属校验失效 | userId 显式传参；退化方案见第四节 |
| 中文工具描述不被模型正确理解 | 工具调不动 | 官方未验证 → **Day 1 就实测**，早发现早改英文描述 |

**未验证项（实施时优先实测）**：Spring AI 的 HTTP 超时默认值、中文工具描述支持、`SimpleVectorStore` 的 `save/load` API、ToolContext 的确切用法。

---

## 十二、实施后需同步的文档

项目有「文档必须与代码一致」的纪律，模块做完后**必须同步**（否则又是「文档对不上系统」）：

| 文档 | 同步什么 |
|---|---|
| `开发记录.md` | 新增十.NNN 条目：实施过程、踩的坑、实测结果 |
| `docs/系统讲解/00-总览与阅读指南.md` | 若它声称「N 份文档覆盖全系统」，需补上 Agent 模块这一块 |
| `docs/系统讲解/01-系统功能流程详解.md` | 补一条**链路的真实说明**（用户提问 → 模型 → 工具 → 回答），含「哪些是真调、哪些会降级」 |
| `docs/接口文档.md` | 补 `POST /api/agent/chat`、`GET /api/agent/status` |
| `docs/系统讲解/13-代码自查发现的问题清单.md` | 补演示 Q&A（降级、归属校验、为什么不让 LLM 直接判断） |
| `临时待办.md` / `开发记录-总览.md` | 状态收尾 |


---

## 十三、这份方案不碰的东西

- **管理端** —— 零改动
- **数据库结构** —— 不建表、不改表
- **现有任何业务逻辑** —— 现有代码只被**调用**，不被修改
