# 多 Agent 协作调研与设计

> 项目：校园智能求职与审核平台（campus-workstudy-agent）
> 定位：**Java 业务为主体 + Agent 深度融入**（Agent 占比目标 **~45%**，面向 **AI 应用开发** 岗位），
> Agent 嵌入"角色互动"与业务决策环节，形成多 Agent 协作。
> 版本：v1.1（按 45% 占比重新设计）　日期：2026-08-30

---

## 目录

1. [背景与定位](#1-背景与定位)
2. [同类项目调研](#2-同类项目调研)
3. [多 Agent 协作的 5 种经典模式](#3-多-agent-协作的-5-种经典模式)
4. [我们的现状盘点与占比基线](#4-我们的现状盘点与占比基线)
5. [总体设计：7 个领域 Agent + 1 个协调 Agent](#5-总体设计7-个领域-agent--1-个协调-agent)
6. [协作场景设计（角色互动 = Agent 互动）](#6-协作场景设计角色互动--agent-互动)
7. [Agent 间通信与 HITL 设计](#7-agent-间通信与-hitl-设计)
8. [技术选型对比](#8-技术选型对比)
9. [Agent 占比控制与量化](#9-agent-占比控制与量化)
10. [实施计划](#10-实施计划)
11. [面试要点与追问预案](#11-面试要点与追问预案)

---

## 1. 背景与定位

### 1.1 为什么做多 Agent 协作

现有项目已完成 P0-P4：安全加固、LLM 接入、智能推荐（RAG）、对话助手（Function Calling + SSE）、智能审核（HITL）。
但当前 AI 能力（推荐/对话/预审/匹配）**相互独立**，是"单点 Agent"：

- 岗位预审 Agent 与岗位发布流程**没有联动**（发布后要手动点"AI 预审"）
- 申请匹配度评估与推荐**没有联动**（低匹配学生没有被自动引导到其他岗位）
- 通知是硬编码模板、统计是裸数字、搜索靠关键词 LIKE —— **这些环节都可以智能升级**

下一步：把单点 Agent 升级为**多 Agent 协作**，让"角色之间的互动"与更多业务决策环节有 Agent 参与。

### 1.2 核心定位（三条原则）

1. **Agent 占比 ~45%（±5%）**：面向 AI 应用开发岗位，Agent 深度融入业务决策环节；
   但用户管理、权限、CRUD、数据表、部署等仍为 Java 业务主体（"java + agent"而非"纯 agent 项目"）
2. **Agent 嵌入角色互动与决策**：学生 ↔ 部门 ↔ 超管之间的互动，以及通知、统计、搜索等环节由 Agent 代理/增强
3. **Human-in-the-Loop**：关键决策（录用、发布、调薪建议）必须人确认，Agent 只做建议和流程推进

---

## 2. 同类项目调研

### 2.1 调研方式

通过 GitHub CLI（gh search repos）检索关键词：`multi agent spring ai`、`spring-ai-alibaba graph`、`multi agent java`、`hiring multi-agent`、`crewai recruitment`、`agent orchestration`、`校园 招聘` 等，并深入阅读代表项目 README。

### 2.2 参考项目清单

| 项目 | 技术栈 | 定位 | 参考价值 |
|---|---|---|---|
| [kundanscode/smartdesk](https://github.com/kundanscode/smartdesk) | **Spring AI（Java）** | 多 Agent 应用：对话助手 + orchestrator 委派专业 agents + RAG + 工具 + 监控 | **orchestrator 模式的 Java 实现范本** |
| [sanjaygupta45/JavaMate](https://github.com/sanjaygupta45/JavaMate) | **Spring AI（Java）** | 多 Agent 编程导师：supervisor 编排 + RAG + web search | supervisor 模式参考 |
| [AzureCosmosDB/multi-agent-spring-ai](https://github.com/AzureCosmosDB/multi-agent-spring-ai) | Spring AI + CosmosDB | Java 多 Agent 编排示例 | Java 编排骨架 |
| [zxuexingzhijie/MathAgent](https://github.com/zxuexingzhijie/MathAgent) | **Spring AI Alibaba Graph（Java）** | 数学建模自动化 Agent（多阶段流水线） | Java 官方 Graph 编排参考 |
| [SummerBreeze320/deerflow-java](https://github.com/SummerBreeze320/deerflow-java) | Spring AI Alibaba Graph | Java Agent 框架（Deep Research 等） | Graph 能力清单 |
| [Monish-Nallagondalla/crewai-recruitment-flow](https://github.com/Monish-Nallagondalla/crewai-recruitment-flow) | CrewAI（Python） | **招聘多 Agent 流水线**：简历解析 → 初筛 → 技术评分 → 排名 | 招聘场景多 Agent 流程设计参考 |
| [jorgegarciaai/crewai-recruitment-assistant](https://github.com/jorgegarciaai/crewai-recruitment-assistant) | CrewAI（Python） | 给 JD → 找候选人 → 逐个评估 → 排名短名单 + 理由 | 角色制 Agent（sourcer/screener）参考 |
| [openai/swarm](https://github.com/openai/swarm) | Python | 轻量多 Agent 编排，Agent 间 handoff 移交 | 移交（handoff）模式 |
| [cheng180/java-spring-ai-agent](https://github.com/cheng180/java-spring-ai-agent) | Spring AI（Java，1k★） | 销售客服 Agent：分层架构 + 混合检索 + 增量索引 + 可观测 | 生产级 Java Agent 工程参考 |

### 2.3 关键结论

- **Java 生态完全可以做多 Agent**（Spring AI / Spring AI Alibaba Graph），且与 Spring Boot 天然集成
- **招聘场景的多 Agent 在 Python（CrewAI）已成熟**（角色制 + 流水线），Java 侧稀缺——做出来就是差异化亮点
- 生产级做法：orchestrator 委派 + 工具调用 + 结构化输出 + HITL + 可观测（smartdesk / 卖好车）

---

## 3. 多 Agent 协作的 5 种经典模式

| 模式 | 描述 | 代表 | 我们是否采用 |
|---|---|---|---|
| **Orchestrator（编排）** | 协调 Agent 接收任务，委派给合适的专业 Agent，汇总结果 | smartdesk、JavaMate | ✅ 核心（CoordinatorAgent） |
| **Pipeline（流水线）** | Agent 输出作为下一个 Agent 输入，串行推进 | crewai-recruitment-flow、MathAgent | ✅ 场景 1/2（发布修订循环、申请撮合） |
| **Role-based（角色制）** | 每个 Agent 有明确 role/backstory/goal，各司其职 | CrewAI | ✅ 7 个领域 Agent 即角色制 |
| **Handoff（移交）** | Agent 间转移对话/任务控制权 | openai/swarm | ⭕ 简化版（服务调用串联，不引入会话移交） |
| **Debate（辩论/评审）** | 多个 Agent 对同一问题互相评审 | 学术类 | ❌ 本场景不需要（成本高） |

> 采用原则：**用最朴素的模式解决业务问题**。选 Orchestrator + Pipeline 组合。

---

## 4. 我们的现状盘点与占比基线

### 4.1 现状能力

| 能力 | 现状 | 在多 Agent 协作中的角色 |
|---|---|---|
| 智能推荐（RAG 画像/召回/精排） | ✅ P2 | 求职 Agent 的"找岗"能力 |
| 对话助手（Function Calling + SSE） | ✅ P3 | 求职 Agent 的"交互"能力 |
| 岗位预审（规则 + LLM 结构化） | ✅ P4 | 审核 Agent 的核心 |
| 申请匹配度评估 | ✅ P4 | 初筛 Agent 的核心 |
| 通知闭环 | ✅ P0-07（模板化文案） | **升级为 NotificationAgent 个性化生成** |
| 定时任务框架 | ✅ P0 | 生命周期协作的"触发器" |
| 统计看板 | ✅（裸数字） | **升级为 AnalystAgent 智能解读** |
| 岗位搜索 | ✅（SQL LIKE） | **升级为 QueryAgent 自然语言理解** |
| 岗位描述生成 | ❌ 待建 | 岗位助手 Agent（新建） |
| 协调 Agent | ❌ 待建 | 协作编排（新建） |

### 4.2 占比基线（实测，2026-08-30）

```
后端 Java 代码总量：  3782 行
agent 包：           1161 行（30.7%）
llm  包：            128 行（ 3.4%）
Agent 合计：         1289 行（34.1%）← 当前基线
业务代码：           2493 行（65.9%）
```

**目标：~45%（约 +600 行 Agent 代码）**，靠新增 3 个领域 Agent + 协调者 + 评测基础设施实现，业务代码基本不删减。

---

## 5. 总体设计：7 个领域 Agent + 1 个协调 Agent

```
                        ┌────────────────────────────────────────┐
                        │        CoordinatorAgent 协调者          │
                        │  事件驱动 / 分支决策 / 协作流编排 / 防呆限流  │
                        └──┬──────┬──────┬──────┬──────┬──────┬───┘
             委派/调度      │      │      │      │      │      │
        ┌─────────────────┘      │      │      │      │      └──────────────────┐
        ▼                        ▼      ▼      ▼      ▼      ▼                  ▼
┌──────────────┐      ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│JobWriterAgent│      │  AuditAgent  │  │JobSeekerAgent│  │ScreenerAgent │  │NotificationAgent│
│ 岗位助手(部门) │      │ 审核助手(超管) │  │ 求职助手(学生) │  │ 初筛助手(部门) │  │ 通知助手(全员)  │
│ 生成/修订初稿  │      │ 规则+LLM预审   │  │ 画像/推荐/对话 │  │ 匹配度评估     │  │ 个性化通知文案  │
└──────┬───────┘      └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                     │                  │                 │                 │
┌──────▼───────┐    ┌────────▼───────┐  ┌───────▼───────┐  ┌─────▼───────┐  ┌──────▼───────┐
│ QueryAgent   │    │  AnalystAgent  │  │InterviewAgent │  │             │  │             │
│ 搜索理解(学生) │    │ 分析助手(平台)  │  │ 面试安排(部门)  │  │             │  │             │
│ NL→结构化查询  │    │ 趋势解读/周报   │  │ 时间建议+出题   │  │             │  │             │
└──────────────┘    └────────────────┘  └───────────────┘  └─────────────┘  └─────────────┘
       │                     │                  │                 │                 │
       └──────────┬──────────┴────────┬─────────┴─────────────────┴─────────────────┘
                  ▼                    ▼
        ┌──────────────────┐  ┌────────────────────┐
        │  现有业务 Service  │  │ DB（Agent 间共享状态  │
        │  Job/Application  │◄─┤ "黑板"+通知/报告留痕) │
        └──────────────────┘  └────────────────────┘
```

### 5.1 各 Agent 职责

| Agent | 代表角色 | 输入 | 输出 | 复用/新增 |
|---|---|---|---|---|
| **JobWriterAgent** | 部门（发布方） | 关键词/要点 + 审核建议 | 岗位描述初稿/修订稿（JSON） | 新增 |
| **AuditAgent** | 超管（平台方） | 岗位信息 + 平台规则 | 预审报告（PASS/SUPPLEMENT/REJECT） | 复用 P4 |
| **JobSeekerAgent** | 学生 | 画像/意图/对话 | 推荐列表 / 对话回复 / 申请动作 | 复用 P2/P3 |
| **ScreenerAgent** | 部门（审核方） | 申请 + 岗位要求 | 匹配度报告 | 复用 P4 |
| **QueryAgent** | 学生（搜索） | 自然语言查询 | 结构化检索参数（关键词/时段/地点/薪资） | 新增 |
| **NotificationAgent** | 平台（全员） | 事件 + 业务上下文 | 个性化通知标题/正文 | 新增（升级 P0-07） |
| **AnalystAgent** | 平台（运营） | 统计数据 | 趋势解读 / 异常提示 / 运营周报 | 新增（升级 Stats） |
| **InterviewAgent** | 部门（面试安排） | 录用申请 + 画像时段 + 匹配报告差距点 | 面试时间建议 + 针对性面试题 + 准备指引 | 新增（复用 interviewTime/timePref/gapPoints） |
| **CoordinatorAgent** | 平台 | 业务事件 | 协作决策 + 流程编排 | 新增 |

### 5.2 Agent 占比控制（量化目标）

- 现有 34.1% → 新增 3 Agent（Query/Analyst/Notification ~350 行）+ ProfileInit（~60 行）+ Coordinator（~150 行）+ AgentEval 评测（~100 行）≈ **+660 行**
- 达成后 Agent ≈ 1949 行 / 总 ≈ 4442 行 ≈ **43.9%（~45%）**
- 占比 = `agent/ + llm/` 包代码行 / 全后端 Java 代码行；以 **docs/占比统计** 章节的脚本口径为准，后续每次提交可复核

---

## 6. 协作场景设计（角色互动 = Agent 互动）

### 场景 1：岗位发布协作流（JobWriterAgent ↔ AuditAgent 循环修订）★ 首期实施

```
部门管理员输入："机房值班，晚上，计算机学院"
  │
  ▼
[1] JobWriterAgent 生成岗位初稿（标题/描述/要求/薪资建议/时间）→ 部门确认提交
  │
  ▼
[2] AuditAgent 预审（规则引擎 + LLM）→ 生成预审报告
  │
  ├─ PASS       → NotificationAgent 通知超管 → 一键采纳发布（HITL）
  ├─ SUPPLEMENT → CoordinatorAgent 把"修改建议"回传 JobWriterAgent 自动修订
  │               → 生成 v2 初稿 → 重新预审（最多 2 轮，防死循环）
  │               → 仍 SUPPLEMENT → 转人工
  └─ REJECT     → NotificationAgent 通知部门拒绝原因（HITL 复核）
```

### 场景 2：申请撮合协作流（JobSeekerAgent ↔ ScreenerAgent）

```
学生通过 JobSeekerAgent（推荐/对话）提交申请
  │
  ▼
[1] ScreenerAgent 评估匹配度
  ├─ 高分（≥70）→ 标记"高匹配"，优先进入人工面试
  ├─ 中分（40-70）→ 匹配报告辅助部门人工审核
  └─ 低分（<40）→ ScreenerAgent 建议婉拒
        │
        ▼
[2] CoordinatorAgent 触发"替代推荐"：JobSeekerAgent 用画像重新检索相似岗位
    → NotificationAgent 推送"这个岗位可能更适合你" → 一键转投（写回申请）
```

### 场景 3：岗位生命周期协作（CoordinatorAgent + 定时任务）

```
定时任务发现岗位超 20 天未招满
  │
  ▼
[1] CoordinatorAgent 派 AuditAgent 分析原因（对比同类岗位的薪资/时间/要求）
  │
  ▼
[2] NotificationAgent 推送调整建议给部门："薪资 ¥20/时 低于同类均值 ¥24/时，建议 +2~4 元/时"（HITL 采纳/忽略）
  │
  ▼
[3] 部门采纳 → 岗位更新 → CoordinatorAgent 让 JobSeekerAgent 重新推荐该岗位
```

### 场景 4：通知个性化（NotificationAgent）★ 与场景 1/2 联动

```
原：模板化通知（"您发布的岗位《X》已通过审批"）
新：NotificationAgent 基于业务上下文生成个性化文案：
    - 录用通知：含岗位亮点 + 入职准备提示 + 联系人话术
    - 拒绝通知：含可执行建议（"建议补充相关经历后再次尝试"）
    - 审批通知：含预审报告摘要
```

### 场景 5：自然语言搜索（QueryAgent）★ 学生浏览岗位入口

```
学生输入"晚上和周末能做的、离图书馆近的兼职"
  │
  ▼
[1] QueryAgent 解析为结构化查询：{ keyword:"兼职", timePref:["晚上","周末"], location:"图书馆" }
  │
  ▼
[2] 落入现有检索（SQL 分页 + 向量语义），结果由 JobSeekerAgent 排序
```

### 场景 6：统计智能解读（AnalystAgent）+ 面试安排协作流（InterviewAgent）

```
统计页：AnalystAgent 解读申请趋势/部门分布（"本周申请量较上周 +35%，主要由岗位 X 拉动"）+ 异常提示
面试安排：部门录用学生后，CoordinatorAgent 触发 InterviewAgent：
    [1] 输入：学生画像时段(timePref) + 岗位时间(workTime) + ScreenerAgent 匹配报告的差距点(gapPoints)/面试建议(interviewHint)
    [2] 输出：候选面试时间（优先匹配双方空闲）+ 针对差距点的 3-5 个面试题 + 准备指引
    [3] 部门确认 → NotificationAgent 通知学生（时间/地点/准备建议）→ 学生确认 → 写回 application.interview_time
```

---

## 7. Agent 间通信与 HITL 设计

### 7.1 通信模式：黑板模式（DB 共享状态）+ 服务调用

- **不引入消息队列/事件总线**（对项目规模过重）
- Agent 间的"消息"= 业务数据落库（岗位版本、审核报告、推荐记录、通知、AgentTask 任务表）
- 协调顺序 = CoordinatorAgent 编排的服务调用链（同步/异步结合）
- 好处：可审计（DB 有全部中间产物）、可回滚（状态机）、面试好讲

### 7.2 HITL（人机协同）边界

| 动作 | 谁决定 |
|---|---|
| 生成/修订岗位初稿、预审报告、匹配报告、调整建议、通知文案、统计解读 | Agent（建议/生成） |
| 岗位发布、岗位拒绝、申请录用/拒绝、调薪建议采纳 | **人（一键采纳/否决）** |
| 低匹配替代推荐、自动修订 v2、注册画像初始化 | Agent 自动（留痕 + 可撤销） |

### 7.3 安全与防呆

- 修订循环最多 2 轮（防死循环烧钱）
- 所有 Agent 动作走 `@LogOperation` 操作日志（可审计）
- Agent 不直接改状态，只产生"建议/生成数据"，状态变更走现有业务 Service + 权限校验（复用 P0 安全体系）

---

## 8. 技术选型对比

| 维度 | 方案 A：自研协调器（推荐） | 方案 B：Spring AI Alibaba Graph |
|---|---|---|
| 实现 | CoordinatorAgent 用 ChatClient 做分支决策；领域 Agent 是 Service；服务调用串联 | 节点化定义协作图（条件/并行/HITL 节点） |
| 代码复用 | **现有代码 90% 复用**（@Tool/LlmJsonParser/P4 报告体系） | 需把现有 Agent 重写为 Graph 节点 |
| Agent 占比 | 天然可控（按需新增领域 Agent） | 框架代码占比上升 |
| 面试价值 | 能讲清楚"为什么自研"（需求朴素、控制力强、成本低） | 讲"用了官方 Graph 编排" |
| 风险 | 复杂编排需自己维护 | 学习成本高、版本迭代 |
| 适用 | **本项目定位**（Java 主体 + Agent 协作） | 深度多 Agent 图编排场景 |

**结论：方案 A**。Graph 作为后续加分项（面试可提"已调研，需要更复杂编排时再引入"）。

---

## 9. Agent 占比控制与量化

### 9.1 量化口径

```
占比 = (agent 包行数 + llm 包行数) / (后端全部 .java 行数)
```

### 9.2 目标达成路径

| 项目 | 行数 | 说明 |
|---|---|---|
| 当前 Agent 合计 | 1289 行（34.1%） | 实测基线 |
| + QueryAgent | ~100 行 | NL → 结构化查询 |
| + NotificationAgent | ~120 行 | 个性化通知文案 |
| + AnalystAgent | ~140 行 | 统计解读 + 周报 |
| + InterviewAgent | ~90 行 | 面试时间建议 + 针对性出题 |
| + CoordinatorAgent | ~150 行 | 事件编排 + 防呆 |
| + AgentEval 评测 | ~100 行 | 效果评估（AI 应用岗亮点） |
| **达成后** | **~1990 行 / ~4490 行 ≈ 44.3%** | 目标 ~45%（±5%） |

### 9.3 占比复核脚本

`docs/脚本/统计agent占比.ps1`（按包路径统计行数），每次提交前运行确认占比不回落。

---

## 10. 实施计划

| 阶段 | 内容 | 交付 |
|---|---|---|
| **M1** | CoordinatorAgent 骨架（事件→决策→调度）+ AgentTask 任务表 | 编排基础 |
| **M2** | 场景 1：JobWriterAgent + 发布-预审-修订协作流 | 全流程可演示 |
| **M3** | 场景 2：ScreenerAgent 低分 → 替代推荐 → 一键转投 | 撮合闭环 |
| **M4** | 场景 3：生命周期协作（定时诊断 + 建议 + 重新推荐） | 定时协作 |
| **M5** | 场景 4/5：NotificationAgent 个性化通知 + QueryAgent 自然语言搜索 | 通知/搜索智能 |
| **M6** | 场景 6：AnalystAgent 统计解读 + InterviewAgent 面试安排 | 分析/面试智能 |
| **M7** | AgentEval 评测脚手架 + 前端协作可视化 + 测试 + 记录 + 提交 | 完整版（占比复核 ~45%） |

> 每阶段可独立演示；复用现有基础设施，预计每个 M 1-2 天。

---

## 11. 面试要点与追问预案

### 11.1 一句话定位（简历/自我介绍）

> "基于 Spring AI 的校园智能求职与审核平台：以 Java 业务系统为主体（Agent 占比约 45%），在岗位发布、审核、申请撮合、面试安排、通知、搜索、统计分析 7 个环节嵌入 **8 个 Agent 的多 Agent 协作**（1 协调者 + 7 领域 Agent），关键决策全部 Human-in-the-Loop，并配套 AI 评测与降级策略。"

### 11.2 面向"AI 应用开发"岗位的技能覆盖

| 岗位要求技能 | 本项目对应 |
|---|---|
| 大模型应用开发（Prompt/API） | `llm/` 封装 + PromptTemplates 集中管理 |
| RAG / 向量检索 | P2 画像+岗位向量化 + 多路召回 + 精排 |
| Function Calling / 工具调用 | P3 JobTools + JobChatAgent |
| 多 Agent 编排 | CoordinatorAgent + 7 领域 Agent（本设计） |
| 结构化输出 | LlmJsonParser + JSON Schema 约束 |
| 评测与效果 | AgentEval（HitRate/nDCG）+ recommendation_log 留痕 |
| 降级与可用性 | LLM 失败规则兜底、修订限轮 |
| 安全与合规 | HITL、操作日志、Agent 不直接改状态 |

### 11.3 可讲的架构点

- **多 Agent 协作模式**：Orchestrator（协调者委派）+ Pipeline（发布-修订循环）组合
- **Agent 间通信**：黑板模式（DB 共享状态 + 服务调用），可审计、可回滚
- **占比工程化**：用脚本量化 Agent 占比并控制在 ~45%，说明"不是堆 Agent，而是按决策点引入"
- **HITL 边界**：Agent 给建议，人做决定，操作日志全留痕

### 11.4 追问预案

| 追问 | 应答 |
|---|---|
| 为什么不用 LangGraph/CrewAI？ | Java 技术栈 + 需求朴素；自研协调器复用 Spring AI 基础设施，控制力强、占比可控 |
| Agent 之间怎么通信？ | 黑板模式：业务状态落库（岗位版本/报告/任务表），CoordinatorAgent 按事件编排服务调用链 |
| 多 Agent 比单 Agent 好在哪？ | 职责单一、可独立演进；协作产生 1+1>2（低匹配自动撮合、SUPPLEMENT 自动修订）；代价是编排复杂度 |
| 怎么防 Agent 失控？ | 修订限 2 轮、关键决策 HITL、操作日志审计、LLM 降级兜底 |
| Agent 占比 45% 怎么来的？ | 脚本统计 agent+llm 包行数/总行数；新增的每个 Agent 都有明确业务决策点，非堆砌 |
| 为什么 45% 而不是更高？ | Java 业务主体（权限/CRUD/数据/部署）仍是系统的骨架与安全边界，Agent 负责"智能决策"层 |

---

*本文档为设计稿，确认后按 M1-M7 实施，每阶段复核 Agent 占比。*
