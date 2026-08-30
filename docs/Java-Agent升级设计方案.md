# 校园智能求职与审核平台 —— Java + Agent 升级设计方案

> 项目：基于 Spring Boot 和 Vue 的校园兼职信息发布与审核系统（代码目录：`代码/`）
> 目标：把现有"信息发布 + 人工审核"系统升级为 **Java + LLM Agent** 的智能平台，
> 服务于秋招求职 **AI 应用工程师** 方向。
> 版本：v1.0（设计阶段）　日期：2025

---

## 目录

1. [项目背景与升级目标](#1-项目背景与升级目标)
2. [现状分析：代码审查结论（优点 / 缺点）](#2-现状分析代码审查结论)
3. [技术选型详细对比](#3-技术选型详细对比)
4. [总体架构设计](#4-总体架构设计)
5. [Agent 1：智能求职推荐 Agent（学生侧，RAG）](#5-agent-1-智能求职推荐-agent学生侧rag)
6. [Agent 2：智能审核 Agent（管理侧，HITL）](#6-agent-2-智能审核-agent管理侧hitl)
7. [方向二：安全与工程质量加固清单](#7-方向二安全与工程质量加固清单)
8. [方向三：智能化深化（可选加分项）](#8-方向三智能化深化可选加分项)
9. [数据库变更设计](#9-数据库变更设计)
10. [开发排期与里程碑](#10-开发排期与里程碑)
11. [秋招简历与面试准备要点](#11-秋招简历与面试准备要点)
12. [风险与备选方案](#12-风险与备选方案)

---

## 1. 项目背景与升级目标

### 1.1 现状

现有系统是一个标准的 **Spring Boot 3.2 + Vue 3 + MySQL** 校园勤工俭学管理平台：

- 角色：学生(0)、企业导师(1)、部门管理员(2)、超级管理员(3)
- 核心流程 ①：部门发布岗位 → 超管审批 → 招聘中 → 招满/结束
- 核心流程 ②：学生浏览岗位 → 在线申请（简历+自荐信）→ 部门审核 → 录用/拒绝
- 配套：站内通知、统计看板、CSV 导出、文件上传、AOP 操作日志、定时任务、Docker 部署

### 1.2 升级目标

1. **学生侧**：从"列表翻页"升级为"**千人千面的智能推荐 + 对话式求职助手**"，解决岗位信息过载问题。
2. **管理侧**：从"纯人工审批"升级为"**AI 预审 + 人机协同决策**"，降低审核人力成本、提升审核一致性。
3. **工程侧**：修复现有安全漏洞与逻辑缺陷，把工程底线做扎实（AI 应用工程师同样考察基础工程能力）。
4. **求职侧**：项目可包装为"基于 Spring AI 的校园智能求职与审核平台"，简历亮点、面试可讲、答辩可演示。

---

## 2. 现状分析：代码审查结论

### 2.1 优点

| 维度 | 结论 |
|---|---|
| 技术栈 | Spring Boot 3.2 + Java 21 + MyBatis + MySQL + JWT + Vue3 + Ant Design Vue，主流且较新 |
| 分层 | Controller / Service / Mapper / Entity / VO 职责清晰，构造器注入，统一 `Result` 返回，全局异常处理 |
| 工程化 | AOP 操作日志、`@Scheduled` 定时任务、CSV 导出（BOM+转义）、UUID 文件重命名、Knife4j 文档、Docker Compose 一键部署 |
| 数据库 | 冗余字段（`job_title`/`department_name`）避免连表；关键列建索引；联合查询组装 VO |
| 业务 | 岗位/申请状态机完整；配额满自动关岗；注册审批流 |
| 毕设材料 | UML 建模全套、开题报告、PPT、基础测试类齐全 |

### 2.2 缺点（含代码位置，作为加固清单依据）

| 严重度 | 问题 | 位置 |
|---|---|---|
| 🔴 安全 | `anyRequest().permitAll()` 全放行，JWT 只解析不鉴权；`/admin/jobs/audit`、`/admin/jobs/publish` 无角色校验，任何登录用户可审批岗位 | `SecurityConfig.java:30`、`JobController.java:76,90` |
| 🔴 逻辑 | `publishJob()` 把 status 设为 2（已结束），与业务语义相反（应设为 1 招聘中） | `JobService.java:102` |
| 🟠 半成品 | `sendNotification()` 无任何调用，审核/申请全流程不发站内通知，前端未读数恒为 0 | `NotificationService.java:19` |
| 🟠 假推荐 | 学生岗位列表仅"本部门优先 + 薪资降序"，无个性化 | `JobService.java:36` |
| 🟠 调试代码 | `System.out.println` 遍布 Service/Filter，含打印 JWT 头与明文参数 | `JwtFilter.java:32`、`JobService.java` |
| 🟡 密码 | 明文兜底比较（长度 <60 直接 equals） | `UserService.java:34` |
| 🟡 文件 | `/api/files/{dateDir}/{filename}` 直接拼路径读文件，无类型白名单，路径穿越风险 | `FileController.java:56` |
| 🟡 性能 | 统计接口全表 load 内存计数；列表无后端分页；PageHelper 引入未用 | `StatsController.java` |
| 🟡 工程质量 | 新旧两份代码不同步；`catch(e){}` 静默吞异常；定时任务"清理通知"空实现 | `ScheduledTasks.java:40` |
| 🟡 前端权限 | 路由守卫只判断登录不按角色分流，学生可直达 `/admin` | `router/index.js:88` |

> 完整结论见交付文档配套的分析章节；其中 🔴 项为升级前必修项（方向二）。

---

## 3. 技术选型详细对比

### 3.1 三个候选方案

| | 方案 A：Spring AI Alibaba + 通义千问（DashScope） | 方案 B：LangChain4j + 通义千问 | 方案 C：Spring AI Alibaba + Ollama 本地模型 |
|---|---|---|---|
| **框架** | [Spring AI Alibaba 1.0](https://github.com/alibaba/spring-ai-alibaba)（阿里官方，已 GA） | [LangChain4j](https://github.com/langchain4j/langchain4j)（Red Hat 支持） | 同方案 A 框架 |
| **模型** | 通义千问（云端 API） | 通义千问（OpenAI 兼容端点） | Ollama 本地（如 qwen2.5:7b） |
| **联网** | 需要网络，国内直连 | 需要网络，国内直连 | 完全离线 |
| **成本** | 有免费额度，低 | 有免费额度，低 | 免费 |

### 3.2 方案 A：Spring AI Alibaba + 通义千问（推荐）

**优点**
- ✅ **阿里官方出品，中文文档齐全、社区活跃**，1.0 已 GA，版本稳定，秋招认可度高（阿里/蚂蚁等国内大厂生态）。
- ✅ **国内直连 DashScope**，无需代理，答辩/演示不翻车；有免费额度。
- ✅ **与 Spring Boot 原生集成**：自动配置、Starter 机制、`@Async`/虚拟线程、Servlet/WebFlux SSE 流式输出开箱即用。
- ✅ **三大件齐全**：Function Calling（工具调用）、Structured Output（结构化输出，JSON Schema）、VectorStore 抽象（pgvector / Milvus / Chroma / Redis / 内存 可插拔）。
- ✅ **模型抽象层**：Spring AI 统一 API，切换模型（千问 → 其他）只改配置。
- ✅ 有官方 Agent 编排示例（记忆 + 工具调用），落地成本低。

**缺点**
- ❌ 抽象层较重，与 Spring 生态绑定较深（换非 Spring 技术栈不适用）。
- ❌ 复杂多 Agent 编排（子 Agent、图编排）不如 LangChain4j 社区方案丰富，需要自己组装。
- ❌ 需注册阿里云账号获取 DashScope API Key（免费额度有限，超量需付费）。
- ❌ 默认绑定阿里模型生态，接其他厂商模型需要额外适配（虽然支持）。

### 3.3 方案 B：LangChain4j + 通义千问

**优点**
- ✅ 抽象贴近 LangChain（Chain / Agent / Tool / Memory / RAG 组件齐全），概念清晰。
- ✅ **Agent 能力最丰富**：`AiServices`、`ToolProvider`、多 Agent / 图编排（community 扩展），适合深度 Agent 场景。
- ✅ 模型无关设计，通义千问有 OpenAI 兼容端点，一套代码可换多家模型。
- ✅ RAG 组件开箱即用（EmbeddingStore、ContentRetriever、Reranker）。

**缺点**
- ❌ 社区规模小于 Spring 官方生态，**中文资料较少、部分文档滞后**，遇坑排查成本高。
- ❌ 与 Spring Boot 集成需手动装配（虽有 starter，但配置项多）。
- ❌ 版本迭代快，API 变动频繁，学习曲线略陡。
- ❌ 背书与"面试官熟悉度"低于阿里官方方案（国内面试官多数更熟 Spring AI 生态）。

### 3.4 方案 C：Spring AI Alibaba + Ollama 本地模型

**优点**
- ✅ 完全离线，**无网环境（答辩现场）也能演示**，不依赖 API Key。
- ✅ 数据不出本机，隐私安全；零成本。

**缺点**
- ❌ **小模型效果明显弱于云端大模型**：中文语义匹配、结构化输出稳定性、推荐解释质量都会打折扣——直接拉低两个 Agent 的演示效果。
- ❌ 需本地安装 Ollama + 拉取模型（7B 在笔记本上推理速度一般），演示环境要求高。
- ❌ 无法体现"生产级 LLM 应用"的工程感（真实生产是 API 调用 + 限流 + 成本控制 + 监控），面试减分。
- ❌ 部署复杂度增加（多维护一个 Ollama 服务）。

### 3.5 推荐结论

> **主方案 A + 备用 C（双模型降级）**：生产/演示用 **Spring AI Alibaba + 通义千问**（保证效果与面试认可度）；
> 同时利用 Spring AI 的模型抽象，**配置一键切换到 Ollama 本地模型**作为无网环境的兜底演示。
> 这一个设计同时吸收了三个方案的优点，还能在面试中讲"多模型适配与降级容灾"。

落地要点：
- 依赖：`spring-ai-alibaba-starter`（含 dashscope、common、向量存储 SPI）
- 模型配置：`spring.ai.dashscope.chat.options.model=qwen-plus`（对话/精排）、`spring.ai.dashscope.embedding.options.model=text-embedding-v3`（向量化）
- 备用：`spring.ai.ollama.base-url=http://localhost:11434`，通过 profile 切换

---

## 4. 总体架构设计

### 4.1 架构图

```
┌──────────────────────────── 前端 (Vue3 + Ant Design Vue) ────────────────────────────┐
│  学生端：岗位列表(推荐序) / 对话式求职助手(SSE) / 推荐理由卡片 / 申请                      │
│  管理端：审核工作台(AI 预审报告) / 候选人匹配度排序 / 一键采纳                            │
└───────────────┬──────────────────────────────────────────────┬───────────────────────┘
                │ REST + SSE                                    │ REST
┌───────────────▼──────────────────────────────────────────────▼───────────────────────┐
│                              Spring Boot 3.2 (backend)                                  │
│                                                                                        │
│  现有模块（保留）                       新增 agent 模块                                  │
│  ├─ auth / user / dept                 ├─ agent.jobmatch（Agent 1）                     │
│  ├─ job / application                  │   ├─ StudentProfileService（画像构建）          │
│  ├─ notification（接通）               │   ├─ JobVectorService（岗位向量化）              │
│  ├─ stats / export / file              │   ├─ RecommendationService（多路召回+LLM精排）   │
│  └─ aspect / task / common             │   └─ JobChatAgent（对话助手，Function Calling） │
│                                        ├─ agent.audit（Agent 2）                        │
│  Security（改注解式鉴权）               │   ├─ RuleEngine（规则预检）                     │
│                                        │   ├─ JobAuditAgent（岗位预审，结构化输出）       │
│                                        │   ├─ ApplicationMatchAgent（匹配度评估）        │
│                                        │   └─ AuditReportService（报告落库/查询）        │
│                                        └─ llm（LLM 封装：ChatClient / Embedding /        │
│                                              PromptTemplates / 降级切换）               │
└───────────────┬──────────────────────────────────────────────┬───────────────────────┘
                │ JDBC                                          │ VectorStore SPI
┌───────────────▼──────────────┐                 ┌──────────────▼───────────────────────┐
│  MySQL（业务库，保留）        │                 │  向量库：pgvector（新容器）             │
│  workstudy + 新增表           │                 │  job_embedding / profile_embedding   │
└──────────────────────────────┘                 └───────────────────────────────────────┘
        ▲ 调 用
┌───────┴───────────────────────────────────────────────────────────────────────────────┐
│  LLM：DashScope 通义千问（主） / Ollama（备，无网降级）                                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 模块划分（`com.workstudy` 包新增）

```
com.workstudy
├── agent
│   ├── jobmatch        # Agent 1：智能求职推荐
│   │   ├── StudentProfileService.java
│   │   ├── JobVectorService.java
│   │   ├── RecommendationService.java
│   │   ├── JobChatAgent.java          # Function Calling 对话助手
│   │   └── dto/                       # 推荐结果、对话消息等 DTO
│   ├── audit           # Agent 2：智能审核
│   │   ├── AuditRuleEngine.java       # 规则预检（敏感词/薪资/完整性）
│   │   ├── JobAuditAgent.java         # 岗位预审（LLM 结构化输出）
│   │   ├── ApplicationMatchAgent.java # 申请匹配度评估
│   │   ├── AuditReportService.java    # 报告落库 / 查询 / 采纳
│   │   └── dto/
│   └── common
│       ├── AgentTaskService.java      # 异步任务编排（@Async + 虚拟线程）
│       └── AgentException.java
├── llm
│   ├── LlmProperties.java             # 模型配置（主/备）
│   ├── ChatClientFactory.java         # ChatClient 封装与降级切换
│   ├── EmbeddingService.java          # 文本向量化封装
│   └── PromptTemplates.java           # 集中管理 Prompt（可配置）
└── controller
    ├── AgentRecommendController.java  # /api/agent/recommendations, /api/agent/chat(SSE)
    ├── AgentAuditController.java      # /api/agent/audit/*
    └── AgentProfileController.java    # /api/agent/profile/rebuild, /api/agent/index/rebuild
```

---

## 5. Agent 1：智能求职推荐 Agent（学生侧，RAG）

### 5.1 业务价值

- 替代现有"本部门优先 + 薪资降序"的假推荐，实现**语义级个性化推荐**；
- 学生可对话式求职："帮我找晚上和周末能做的、离图书馆近的兼职"；
- 每条推荐附 **"为什么推荐"** 的可解释文案，提升信任感与简历亮点。

### 5.2 推荐链路（多路召回 + LLM 精排）

```
学生登录
  │
  ├─ 画像构建（StudentProfileService）
  │    sys_user(专业/年级) + job_application(历史申请/录用) + 简历技能标签
  │    → LLM 抽取 → 结构化画像（JSON）：{ major, grade, skills[], timePref[], salaryPref }
  │    → 画像文本 Embedding → profile_embedding 入库
  │
  ├─ 多路召回（Recall）
  │    R1 向量召回：画像向量 × job_embedding 余弦相似度 Top 20      ← 语义匹配
  │    R2 规则召回：同部门 / 时间兼容 / 薪资区间 / 未申请过          ← 业务约束
  │    R3 协同召回：相似画像学生已申请/录用的岗位（基于画像相似度）  ← 行为协同
  │    → 合并去重 → 候选集（≤30）
  │
  ├─ LLM 精排（Rerank）
  │    候选岗位字段 + 学生画像 → LLM 逐条打分(0-100) 并给出 1 句推荐理由
  │    → 按分数排序 → Top-N(默认10)
  │
  └─ 输出（RecommendationService）
       [{ jobId, score, reason, strategies:["语义匹配","同部门","相似学生申请"] }]
       → 落库 recommendation_log（用于效果评估）→ 前端卡片展示
```

### 5.3 对话式求职助手（JobChatAgent）

- 协议：`POST /api/agent/chat`，**SSE 流式返回**；
- 采用 **ReAct 模式**：LLM 循环"思考 → 调用工具 → 观察结果 → 继续"，直至给出回答；
- 会话记忆：基于用户 id 的对话历史（Spring AI ChatMemory / 自实现 Redis 或内存滑动窗口）。

**工具定义（Function Calling）**

| 工具名 | 参数 | 说明 |
|---|---|---|
| `search_jobs` | `{keyword?, departmentId?, timePref?, salaryMin?, salaryMax?}` | 语义+条件检索岗位（调 RecommendationService / 现有 JobService） |
| `get_job_detail` | `{jobId}` | 岗位详情（调现有查询） |
| `get_my_applications` | `{}` | 我的申请记录（调现有接口） |
| `submit_application` | `{jobId, coverLetter?}` | 直接申请岗位（调现有 ApplicationService，二次确认后执行） |
| `get_profile` | `{}` | 获取我的画像（供 LLM 参考） |

**对话示例**

```
学生：帮我找晚上和周末能做的兼职，最好离图书馆近
Agent(思考)：调用 search_jobs(timePref="晚上/周末", location="图书馆")
Agent(观察)：返回 5 个岗位
Agent(回答)：为你找到 3 个合适的岗位：
 1. 图书馆助理（每周工作10小时，¥25/时）—— 与你专业匹配度高，且就在图书馆
 2. 机房值班（周末，¥22/时）—— 时间完全兼容你的课程表
 3. 实验室助手（周一至周五晚上，¥30/时）—— 与你的计算机专业强相关
 需要我帮你直接申请哪一个吗？
```

### 5.4 核心接口设计

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/agent/recommendations` | 我的推荐岗位列表（含 reason/score），`?limit=10` |
| POST | `/api/agent/chat` | 对话式求职助手（SSE 流式） |
| POST | `/api/agent/profile/rebuild` | 重建我的画像（注册/资料变更后调用） |
| POST | `/api/agent/index/rebuild` | 重建岗位向量索引（管理员/发布岗位后调用） |
| GET | `/api/agent/recommendations/log` | 我的推荐历史（效果回看，可作答辩素材） |

### 5.5 关键 Prompt 示例（预置在 `PromptTemplates`）

**画像抽取 Prompt**

```text
你是校园勤工俭学平台的画像分析师。根据学生的专业、年级、历史申请与录用记录、简历技能标签，
抽取结构化求职画像，只输出 JSON：
{
  "major": "专业",
  "grade": "年级",
  "skills": ["技能标签", ...],          // 最多 8 个
  "timePref": ["可工作时段", ...],
  "salaryPref": "期望薪资范围或空",
  "interestKeywords": ["兴趣相关岗位词", ...]
}
输入：
专业：{major}；年级：{grade}
历史申请：{applications}
简历技能：{skills}
```

**推荐精排 Prompt**

```text
你是校园岗位推荐官。根据学生画像和候选岗位列表，为每个岗位打分（0-100）并给出不超过 30 字的中文推荐理由。
要求：优先考虑技能匹配度，其次时间与地点兼容性，最后薪资；不要推荐学生已申请的岗位。
只输出 JSON 数组：[{"jobId":1,"score":92,"reason":"技能高度匹配且时间兼容"}]
学生画像：{profile}
候选岗位：{jobs}
```

### 5.6 前端改动

- `StudentDashboard.vue`：新增"为你推荐"区块（推荐理由卡片）；
- `StudentJobs.vue`：岗位列表改为按推荐分排序，标注推荐理由标签；
- 新增 `ChatAssistant.vue`：右下角悬浮对话窗（SSE 流式打字机效果 + 工具调用 loading 态）。

---

## 6. Agent 2：智能审核 Agent（管理侧，HITL）

### 6.1 业务价值

- 岗位发布审批：AI 预审生成结构化报告（建议通过/需补充/建议拒绝 + 理由 + 修改建议），超管一键采纳；
- 申请审核：LLM 评估学生自荐信/简历与岗位要求的**匹配度评分**，部门按分数排序候选人辅助决策；
- 全程 **Human-in-the-Loop**：AI 只给建议，最终决策权在人，安全合规。

### 6.2 岗位发布预审流程

```
部门提交岗位 (现有 /department/jobs POST)
  │
  ├─ 同步阶段：规则引擎快速预检（AuditRuleEngine，零成本，秒级）
  │     R1 敏感词/违规词（黑名单词库）
  │     R2 薪资合理性（低于下限/异常高值/为空）
  │     R3 必填项完整性（title/description/requirements/contact）
  │     R4 描述质量（过短、纯联系方式、疑似广告）
  │     → 输出 ruleReport { pass: bool, issues: [...] }
  │
  ├─ 异步阶段：LLM 预审（JobAuditAgent，@Async，几秒~十几秒）
  │     工具调用：get_department_info(部门) / get_similar_jobs(相似历史岗位) / get_policy(政策规则)
  │     → LLM 综合生成结构化审核报告（JSON Schema 强约束）
  │
  ├─ 报告落库（audit_report 表）→ 站内通知超管"新岗位待审，AI 报告已生成"
  │
  └─ 超管审核工作台（AdminJobs.vue 改造）
       展示：规则预检结果 + AI 预审报告（建议/理由/修改建议）
       [采纳 AI 建议] → 调现有 /admin/jobs/audit 接口完成审批（一键）
       [驳回并手动填写] → 走原人工流程
```

### 6.3 结构化输出定义（JSON Schema）

```json
{
  "type": "object",
  "properties": {
    "suggestion": { "enum": ["PASS", "SUPPLEMENT", "REJECT"], "description": "审核建议" },
    "score": { "type": "number", "description": "岗位质量分 0-100" },
    "reasons": {
      "type": "array",
      "items": { "type": "string" },
      "description": "给出建议的理由列表，每条 <=50 字"
    },
    "suggestions": {
      "type": "array",
      "items": { "type": "string" },
      "description": "修改建议，可为空"
    },
    "riskFlags": {
      "type": "array",
      "items": { "type": "string" },
      "description": "风险标记：敏感词/薪资异常/联系方式缺失等"
    }
  },
  "required": ["suggestion", "score", "reasons", "suggestions", "riskFlags"]
}
```

### 6.4 申请匹配度评估（ApplicationMatchAgent）

- 输入：岗位要求（requirements/description）+ 学生自荐信（cover_letter）+ 简历摘要（resume_url 指向的文件文本，可选 LLM 抽取）
- 输出：`{ matchScore: 0-100, matchedPoints: [...], gapPoints: [...], interviewHint: "面试建议提问点" }`
- 场景 ① 部门审核申请列表时**批量评估**（新岗位收到申请后异步触发，落库供排序）；
- 场景 ② 审核单条申请时实时评估（`POST /api/agent/audit/match`）。

### 6.5 核心接口设计

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/agent/audit/job/{jobId}/generate` | 异步生成岗位预审报告 |
| GET | `/api/agent/audit/report/{targetType}/{targetId}` | 查询审核报告（targetType: job/application） |
| POST | `/api/agent/audit/match` | 申请匹配度评估（单个/批量） |
| POST | `/api/agent/audit/job/{jobId}/adopt` | 采纳 AI 建议并执行审批（内部复用现有 audit 逻辑） |
| GET | `/api/agent/audit/rules` | 查看/配置规则引擎词库（管理端） |

> 说明：`adopt` 复用现有 `JobService.auditJob`，保证状态流转逻辑单一；Agent 只产生"建议"不直接改状态（安全边界）。

### 6.6 关键 Prompt 示例

**岗位预审 Prompt**

```text
你是校园勤工俭学平台的审核专家，负责对部门提交的岗位进行预审。
请结合以下信息给出审核建议：
1. 岗位信息：{job}
2. 发布部门：{department}
3. 平台规则：{policy}（如：时薪不得低于当地最低标准；必须提供联系人电话；不得出现"日结刷单"等违规内容）
4. 相似岗位参考：{similarJobs}

要求：
- 只输出符合给定 JSON Schema 的 JSON，不要输出多余内容；
- suggestion=PASS 表示可直接通过；SUPPLEMENT 表示信息需补充；REJECT 表示存在违规或重大缺陷；
- reasons 必须具体可执行，例如"薪资 ¥5/时 低于校园岗位最低标准 ¥15/时"。
```

### 6.7 前端改动

- `AdminJobs.vue` 改造为"审核工作台"：待审岗位卡片上展示 AI 预审报告折叠区 + [采纳建议] 按钮；
- `DepartmentApplications.vue`：申请列表按 matchScore 排序，展示匹配度进度条与匹配点；
- 新增"审核报告详情"抽屉（reason 列表、修改建议、风险标记）。

---

## 7. 方向二：安全与工程质量加固清单

> 与 Agent 开发并行（低风险、面试必考）。逐条对应 [2.2 缺点表](#22-缺点含代码位置作为加固清单依据)。

| # | 修复项 | 方案 | 对应位置 |
|---|---|---|---|
| 1 | 接口越权 | Security 改为按需放行 + 注解式鉴权：自定义 `@RequireRole({2,3})` + AOP 拦截（或 `@PreAuthorize`），`/admin/**` 仅角色 3，`/department/**` 仅 1/2，`/student/**` 仅 0 | `SecurityConfig`、新增 `aspect/RoleAspect` |
| 2 | `publishJob` 状态 bug | 改为 status=1（招聘中）；统一岗位状态枚举 `JobStatus` | `JobService.java:102` |
| 3 | 通知半成品 | 在审核通过/拒绝、申请提交、录用、岗位审批处调用 `sendNotification` | `ApplicationService`、`JobService` |
| 4 | 日志 | 全量替换 `System.out.println` → SLF4J；JWT 不打印 token | `JwtFilter`、`JobService` 等 |
| 5 | 明文密码兜底 | 删除 <60 长度明文比较分支，统一 BCrypt | `UserService.java:34` |
| 6 | 文件接口 | 类型白名单（pdf/doc/docx/jpg/png）+ 文件名安全校验（拒绝 `..`）+ Content-Type 校验 | `FileController` |
| 7 | 性能 | 岗位/申请列表后端分页（启用 PageHelper 或手写 LIMIT）；统计改为 SQL 聚合 | `StatsController`、新增分页参数 |
| 8 | 前端权限 | 路由守卫按角色分流（meta.roles），无权限跳转首页 | `router/index.js` |
| 9 | 清理定时任务 | 实现通知清理 SQL（`delete from notification where create_time < now()-30d and is_read=1`） | `ScheduledTasks.java` |
| 10 | 测试 | 为 Agent 服务补单元测试（Mock LLM）+ 现有服务测试补全 | `src/test` |

---

## 8. 方向三：智能化深化（可选加分项）

在 Agent 1/2 稳定后按需叠加，每个都是独立可讲的亮点：

1. **简历解析 Agent**：上传 PDF/图片简历 → OCR/LLM 抽取技能标签 → 自动补全画像（配合现有文件上传）；
2. **岗位描述生成助手**：部门只填关键词（"图书馆、助理、每周10小时"）→ LLM 生成完整岗位描述/要求初稿 → 人工微调后提交；
3. **运营智能体（定时）**：每天凌晨 Agent 扫描"即将过期/长期未招满"岗位，向管理员推送续期或调整薪资建议（复用现有 `@Scheduled` 框架）；
4. **LLM 运营周报**：基于 stats 数据 + LLM 生成中文周报（趋势解读、异常告警）。

---

## 9. 数据库变更设计

> 业务库保持 MySQL（`workstudy`）不动，新增表如下；向量数据独立存 pgvector 容器。

```sql
-- 学生画像表
CREATE TABLE student_profile (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL UNIQUE COMMENT '学生ID',
  major       VARCHAR(100) COMMENT '专业',
  grade       VARCHAR(20)  COMMENT '年级',
  skill_tags  JSON         COMMENT '技能标签数组',
  time_pref   JSON         COMMENT '可工作时段数组',
  salary_pref VARCHAR(50)  COMMENT '期望薪资',
  profile_text TEXT        COMMENT '画像文本(供向量化)',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 推荐记录表（效果评估/回看）
CREATE TABLE recommendation_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  job_id      BIGINT NOT NULL,
  score       DECIMAL(5,2),
  reason      VARCHAR(255),
  strategies  JSON COMMENT '命中的召回策略',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
);

-- 审核报告表
CREATE TABLE audit_report (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_type  VARCHAR(20)  NOT NULL COMMENT 'job/application',
  target_id    BIGINT       NOT NULL,
  suggestion   VARCHAR(20)  COMMENT 'PASS/SUPPLEMENT/REJECT 或 matchScore',
  score        DECIMAL(5,2) COMMENT '质量分/匹配分',
  reasons      JSON,
  suggestions  JSON,
  risk_flags   JSON,
  agent_model  VARCHAR(50) COMMENT '使用的模型',
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_target (target_type, target_id)
);

-- pgvector 容器内（docker-compose 新增服务）：
--   job_embedding:     (job_id, chunk_index, chunk_text, embedding vector(1024))
--   profile_embedding: (user_id, embedding vector(1024))
-- Spring AI VectorStore 抽象封装上述两张表，业务代码不直接碰 SQL。
```

`docker-compose.yml` 新增：

```yaml
  vector-db:
    image: pgvector/pgvector:pg16
    container_name: workstudy-vector
    environment:
      POSTGRES_USER: vector
      POSTGRES_PASSWORD: vector
      POSTGRES_DB: workstudy_vector
    ports: ["5433:5432"]
    volumes: [vector_data:/var/lib/postgresql/data]
volumes:
  vector_data:
```

---

## 10. 开发排期与里程碑

> 每阶段结束都可独立演示、可写进简历。

| 阶段 | 内容 | 产出/里程碑 | 预估 |
|---|---|---|---|
| **P0 工程加固** | 方向二 1-10 项 | 安全漏洞清零、状态机正确、日志规范、通知闭环、分页 | 2~3 天 |
| **P1 基础设施** | 引入 spring-ai-alibaba；LLM 封装（ChatClient/Embedding/PromptTemplates）；pgvector 容器；表结构 | 后端启动即可调通"LLM 对话 + 向量写入/检索"冒烟测试 | 2~3 天 |
| **P2 Agent 1** | 画像构建、岗位向量化、多路召回+LLM 精排、推荐接口、前端推荐区块 | 学生端看到"个性化推荐 + 推荐理由" | 4~5 天 |
| **P3 Agent 1 对话** | JobChatAgent（Function Calling + SSE）、ChatAssistant 前端 | 对话式求职助手可演示"找岗→看详情→申请" | 3~4 天 |
| **P4 Agent 2** | 规则引擎、岗位预审 Agent、申请匹配评估、审核工作台前端、通知接入 | 超管审核工作台展示 AI 报告并一键采纳 | 4~5 天 |
| **P5 收尾** | 测试补全、效果评估（见 11.4）、README/架构文档、答辩 Demo 脚本 | 完整可演示版本 + 简历素材 | 2~3 天 |

合计约 **3~4 周**（按每日 3~4 小时投入估算），与秋招投递节奏可并行推进。

---

## 11. 秋招简历与面试准备要点

### 11.1 项目定位（一句话）

> **基于 Spring AI 的校园智能求职与审核平台**：在校园勤工俭学系统中引入大模型 Agent，
> 实现学生侧 RAG 个性化岗位推荐与对话式求职助手、管理侧 AI 预审与候选人匹配度评估，审核效率显著提升。

### 11.2 简历亮点（两条，放项目栏顶部）

- `Spring AI Alibaba + DashScope`：岗位/画像向量化入库（pgvector），实现**多路召回 + LLM 精排**的个性化推荐，
  并生成可解释推荐理由；基于 **Function Calling** 实现对话式求职助手（SSE 流式），支持"找岗→看详情→直接申请"闭环。
- `Agent 智能审核`：规则引擎（敏感词/薪资/完整性）+ LLM **结构化输出**（JSON Schema）生成岗位预审报告，
  **Human-in-the-Loop** 人机协同审批；自荐信-岗位**匹配度评分**辅助候选人排序。

### 11.3 面试必讲四张图

1. **系统架构图**（见 4.1）：讲清楚"业务系统 ↔ Agent ↔ 向量库 ↔ LLM"的关系；
2. **RAG 检索链路**：画像/岗位如何向量化、相似度检索、为什么召回后还要 LLM 精排；
3. **Function Calling 时序**：画一次对话的 思考→调工具→观察→回答 循环；
4. **HITL 设计**：AI 建议如何结构化落库、人如何一键采纳/否决、安全边界（Agent 不直接改状态）。

### 11.4 效果证明（面试加分，避免"自嗨"）

- 离线评测：人工标注 50 条"学生-岗位"相关性，计算推荐 **HitRate@10 / NDCG**，与旧排序对比（预期 HitRate 提升 30%+）；
- 在线指标：上线后对比"推荐位点击率 / 申请转化率"（旧列表 vs 推荐列表），用 `recommendation_log` 统计；
- 审核侧：统计"AI 建议采纳率"与"单人审核耗时下降"（可在答辩 PPT 中放前后对比截图）。

### 11.5 常见追问预案

| 追问 | 应答要点 |
|---|---|
| 为什么用向量检索而不用 SQL LIKE？ | 语义匹配（"晚上能做的兼职" ↔ "工作时间：晚班"），LIKE 无法处理同义/近义 |
| 冷启动怎么办？ | 新学生无画像：用规则召回（同部门/热门）+ 默认画像；新岗位：向量照常入库，靠语义召回兜底 |
| LLM 幻觉怎么防？ | 推荐只基于向量检索结果（RAG 约束）；审核报告 JSON Schema 强约束；Agent 不直接写库，只给建议 |
| 为什么不用 Python？ | Java 团队技术栈统一、Spring AI 生态成熟；同时体现"AI 能力与后端工程一体化的落地能力" |
| 成本/性能？ | 画像与向量化异步批量；精排只对 Top-30 候选调用；SSE 流式降低首 token 延迟；小模型降级方案 |

---

## 12. 风险与备选方案

| 风险 | 影响 | 对策 |
|---|---|---|
| DashScope 免费额度有限 | Agent 调用成本 | 精排只对 Top-30；对话限流（每人每分钟 N 次）；预留 Ollama 降级 |
| 本地无网答辩 | 演示失败 | 双模型配置一键切换 Ollama（方案 C 兜底），答辩前演练降级路径 |
| 推荐效果"看起来不明显" | 面试说服力弱 | 按 11.4 做离线评测与前后对比截图，用数据说话 |
| 向量库运维成本 | 增加部署复杂度 | Spring AI VectorStore 抽象隔离；本地开发可用内存实现，生产用 pgvector 容器 |
| 时间不足 | 排期风险 | P0→P5 每阶段可独立交付；若时间紧，对话助手可后置（P3 砍掉不影响 P2/P4 主亮点） |
| 项目代码两份不同步 | 改错版本 | 以 `代码/` 目录为准，废弃旧副本；Agent 代码全部落在 `代码/backend` |

---

*本文档为设计阶段产物，技术选型结论：主 **Spring AI Alibaba + 通义千问**，备 **Ollama 本地降级**；两个 Agent 模块（智能求职推荐 / 智能审核）按第 5、6 章设计实施。*
