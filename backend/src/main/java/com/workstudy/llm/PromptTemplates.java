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
