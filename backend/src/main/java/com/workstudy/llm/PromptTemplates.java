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
