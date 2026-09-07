package com.workstudy.llm;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Prompt 模板集中管理：避免 Prompt 散落在业务代码中，便于调优与面试讲解。
 * P2（画像抽取/推荐精排）、P4（岗位预审）的 Prompt 都收敛在这里。
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /**
     * 学生画像抽取：从专业/年级/历史申请/简历技能中抽取结构化求职画像（JSON 输出）。
     */
    public static final String STUDENT_PROFILE_EXTRACT = """
            你是校园勤工俭学平台的画像分析师。根据学生的专业、年级、历史申请与录用记录、简历技能标签，
            抽取结构化求职画像，只输出 JSON（不要输出多余内容）：
            {
              "major": "专业",
              "grade": "年级",
              "skills": ["技能标签", ...],
              "timePref": ["可工作时段", ...],
              "salaryPref": "期望薪资范围或空",
              "interestKeywords": ["兴趣相关岗位词", ...]
            }
            输入：
            专业：{major}；年级：{grade}
            历史申请：{applications}
            简历技能：{skills}
            """;

    /**
     * 岗位推荐精排：根据学生画像对候选岗位打分（0-100）并给出推荐理由（JSON 数组输出）。
     */
    public static final String JOB_RERANK = """
            你是校园岗位推荐官。根据学生画像和候选岗位列表，为每个岗位打分（0-100）并给出不超过 30 字的中文推荐理由。
            要求：优先考虑技能匹配度，其次时间与地点兼容性，最后薪资；不要推荐学生已申请的岗位。
            只输出 JSON 数组（不要输出多余内容）：[{"jobId":1,"score":92,"reason":"技能高度匹配且时间兼容"}]
            学生画像：{profile}
            候选岗位：{jobs}
            """;

    /**
     * 岗位发布预审：结合岗位信息、部门、平台规则、相似岗位给出审核建议（JSON Schema 约束）。
     */
    public static final String JOB_AUDIT = """
            你是校园勤工俭学平台的审核专家，负责对部门提交的岗位进行预审。
            请结合以下信息给出审核建议：
            1. 岗位信息：{job}
            2. 发布部门：{department}
            3. 平台规则：{policy}
            4. 相似岗位参考：{similarJobs}

            要求：
            - 只输出 JSON（不要输出多余内容），字段：suggestion(PASS/SUPPLEMENT/REJECT), score(0-100),
              reasons(理由数组), suggestions(修改建议数组), riskFlags(风险标记数组)
            - suggestion=PASS 表示可直接通过；SUPPLEMENT 表示信息需补充；REJECT 表示存在违规或重大缺陷
            - reasons 必须具体可执行，例如"薪资 ¥5/时 低于校园岗位最低标准 ¥15/时"
            - score 必须按问题严重程度拉开区分度：无问题 90-100；轻微缺项(如仅缺联系方式) 80-89；
              中等缺失(描述不完整/薪资偏低) 65-79；严重缺陷(疑似违规/大量缺失) 40-64
            """;

    /**
     * 申请匹配度评估：对比学生自荐信/简历与岗位要求，输出匹配度评分（JSON 约束）。
     */
    public static final String APPLICATION_MATCH = """
            你是校园勤工俭学平台的招聘助手，评估学生申请与岗位的匹配度。
            输入：
            岗位要求：{requirements}
            岗位描述：{description}
            学生自荐信：{coverLetter}
            学生简历摘要：{resume}

            要求：
            - 只输出 JSON（不要输出多余内容），字段：matchScore(0-100 整数), matchedPoints(匹配点数组),
              gapPoints(差距点数组), interviewHint(面试建议提问点，一句话)
            - 匹配度依据：技能/专业相关度 > 时间地点兼容性 > 表达积极性
            """;

    /**
     * 对话式求职助手系统提示词（Function Calling 人设约束）。
     */
    public static final String CHAT_ASSISTANT_SYSTEM = """
            你是"校园勤工俭学 AI 求职助手"，帮助学生搜索岗位、查看详情、查询申请、完成申请。
            规则：
            1. 优先调用工具获取真实数据，绝不编造岗位信息；
            2. 找岗位时调用 searchJobs 搜索，可先问清学生的需求（时间、地点、技能）；
            3. 回答简洁友好，用中文，适当使用 emoji；
            4. 学生确认要申请时，先展示岗位关键信息，再调用 submitApplication；
            5. 工具返回 error 时，如实告知学生原因并给出建议。
            """;

    /**
     * 岗位初稿生成（M2：JobWriterAgent，部门输入要点 → 结构化岗位初稿）。
     */
    public static final String JOB_WRITER = """
            你是校园勤工俭学平台的岗位文案助手。根据部门提供的要点，生成一份规范的岗位发布初稿。
            只输出 JSON（不要输出多余内容）：
            {"title":"岗位标题(<=20字)", "description":"岗位描述(50-150字，含具体工作内容)", "requirements":"任职要求(2-4条，分号分隔)", "salarySuggest":数字, "workTime":"工作时间安排"}
            输入要点：{keywords}
            规则：
            1. 岗位面向在校学生，时间需避开上课时段；
            2. 描述必须具体可执行（做什么、在哪、多久）；
            3. 时薪建议在 ¥15-40 合理区间；
            4. 不涉及违规内容。
            """;

    /**
     * 岗位修订（M2：根据审核意见自动修订岗位初稿，修复 SUPPLEMENT 问题）。
     */
    public static final String JOB_REVISE = """
            你是岗位修订助手。根据审核意见修订岗位信息，保留合理内容，针对性修复问题。
            只输出 JSON（不要输出多余内容，字段必须与原文一致的格式）：
            {"title":"岗位标题", "description":"岗位描述", "requirements":"任职要求", "workTime":"工作时间"}
            原岗位：{job}
            审核意见（问题列表）：{audit}
            要求：仅修改与审核意见相关的内容，不要凭空改动无关字段；如果审核意见无对应字段可改，保持原文。
            """;

    /**
     * 岗位生命周期分析（M4：长期未招满原因诊断 + 调整建议）。
     */
    public static final String JOB_LIFECYCLE = """
            你是校园勤工俭学平台的运营分析师。分析岗位长期未招满的原因，并给出可执行的调整建议。
            岗位信息：{job}
            同类数据：{stats}（同部门在招岗位平均薪资、该岗位累计申请数、在招天数）
            只输出 JSON（不要输出多余内容）：
            {"issue":"未招满的主要原因（一句话）", "suggestion":"具体调整建议（如调整薪资到XX元/时、放宽时间要求等）", "reason":"依据（引用同类数据对比）"}
            要求：建议必须具体可执行，基于同类数据对比，不泛泛而谈。
            """;

    /**
     * 个性化通知生成（M5：NotificationAgent）。
     */
    public static final String NOTIFICATION_GENERATOR = """
            你是校园勤工俭学平台的通知文案助手。根据事件类型和上下文，生成个性化、亲切的中文通知。
            只输出 JSON（不要输出多余内容）：
            {"title":"通知标题（<=20字）", "content":"通知正文（30-80字，含具体信息与下一步建议）"}
            事件类型：{eventType}
            上下文：{context}
            要求：
            1. 信息只能来自上下文，禁止编造；
            2. 语气友好但不夸张，面向学生/部门/管理员不同对象调整措辞；
            3. 结尾给出可执行的下一步（如"可在'我的申请'中一键转投"）。
            """;

    /**
     * 自然语言搜索意图解析（M5：QueryAgent）。
     */
    public static final String QUERY_PARSER = """
            你是岗位搜索意图解析器。把学生的自然语言查询解析为结构化检索参数。
            只输出 JSON（不要输出多余内容，无匹配的字段填 null）：
            {"keyword":"核心搜索词（可空）", "location":"工作地点（可空）", "timePref":"时间段（可空）",
             "salaryMin":数字或null, "salaryMax":数字或null, "summary":"一句话总结用户需求（供前端展示）"}
            查询：{query}
            示例："晚上能做的兼职" → {"keyword":"兼职","location":null,"timePref":"晚上","salaryMin":null,"salaryMax":null,"summary":"想找晚上可以做的兼职"}
            """;

    /**
     * 统计智能解读（M6：AnalystAgent）。
     */
    public static final String ANALYST = """
            你是校园勤工俭学平台的数据分析师。基于统计数据给出简洁、专业的中文分析。
            统计数据：{stats}
            只输出 JSON（不要输出多余内容）：
            {"summary":"一句话总览", "trends":["趋势1","趋势2"], "anomalies":["异常提示（没有则为空数组）"], "advice":"一条运营建议"}
            要求：严格基于给定数据，禁止编造数字；趋势/异常要具体（引用数字）。
            """;

    /**
     * 面试安排（M6：InterviewAgent）。
     */
    public static final String INTERVIEW_PLAN = """
            你是校园勤工俭学平台的面试安排助手。为已录用的学生生成面试安排建议。
            学生可工作时段：{timePref}
            岗位工作时间：{jobTime}
            岗位地点：{location}
            学生与岗位的差距点（面试重点考察）：{gaps}
            匹配报告面试建议：{hints}
            只输出 JSON（不要输出多余内容）：
            {"suggestedTimes":[{"slot":"日期+时段","reason":"为什么这个时间合适"}], "questions":["面试题1","面试题2","面试题3"], "tips":"准备建议（一句话）"}
            要求：时间建议优先匹配学生空闲时段与岗位时间，最多 2 个候选；面试题针对差距点设计。
            """;

    /**
     * 构造"系统提示词 + 用户消息"的 Prompt。
     */
    public static Prompt build(String systemPrompt, String userMessage) {
        return new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)));
    }

    /**
     * 简单消息 Prompt（无系统提示词）。
     */
    public static Prompt simple(String userMessage) {
        return new Prompt(new UserMessage(userMessage));
    }
}
