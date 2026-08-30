package com.workstudy.agent.audit;

import com.workstudy.entity.Job;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 审核规则引擎（Agent 2 的第一道闸）：纯规则、零成本、秒级返回。
 * 与 LLM 预审互补：规则负责"确定性问题"（缺失/越界/违禁词），LLM 负责"开放性问题"（质量/合规判断）。
 */
@Component
public class AuditRuleEngine {

    /** 敏感词黑名单（校园勤工俭学场景） */
    private static final List<String> SENSITIVE_WORDS = List.of(
            "刷单", "传销", "赌博", "诈骗", "色情", "违法", "贷款", "代考", "包过", "日结", "返利");

    /** 校园岗位时薪合理区间 */
    private static final BigDecimal MIN_SALARY = new BigDecimal("15");
    private static final BigDecimal MAX_SALARY = new BigDecimal("200");

    /** 描述最短长度 */
    private static final int MIN_DESC_LENGTH = 20;

    /**
     * 规则预检，返回问题列表（空列表 = 通过）。
     */
    public List<String> check(Job job) {
        List<String> issues = new ArrayList<>();

        // 必填项完整性
        if (blank(job.getTitle())) issues.add("岗位标题缺失");
        if (blank(job.getDescription())) issues.add("岗位描述缺失");
        if (blank(job.getRequirements())) issues.add("任职要求缺失");
        if (blank(job.getContactPerson())) issues.add("联系人缺失");
        if (blank(job.getContactPhone())) issues.add("联系电话缺失");

        // 薪资合理性
        if (job.getSalary() == null) {
            issues.add("薪资未填写");
        } else {
            if (job.getSalary().compareTo(MIN_SALARY) < 0) {
                issues.add("薪资 ¥" + job.getSalary() + "/时 低于校园最低标准 ¥" + MIN_SALARY + "/时");
            } else if (job.getSalary().compareTo(MAX_SALARY) > 0) {
                issues.add("薪资 ¥" + job.getSalary() + "/时 异常偏高，需核实");
            }
        }

        // 敏感词
        String text = nvl(job.getTitle()) + nvl(job.getDescription()) + nvl(job.getRequirements());
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                issues.add("命中敏感词：" + word);
            }
        }

        // 描述质量
        if (job.getDescription() != null && job.getDescription().length() < MIN_DESC_LENGTH) {
            issues.add("岗位描述过短（少于 " + MIN_DESC_LENGTH + " 字），信息可能不完整");
        }

        return issues;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
