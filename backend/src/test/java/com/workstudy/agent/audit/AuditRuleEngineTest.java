package com.workstudy.agent.audit;

import com.workstudy.entity.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditRuleEngineTest {

    private final AuditRuleEngine engine = new AuditRuleEngine();
    private Job job;

    @BeforeEach
    void setUp() {
        job = new Job();
        job.setTitle("图书馆助理");
        job.setDescription("协助图书馆日常管理，整理图书上架，维护阅览室秩序");
        job.setRequirements("认真负责，有耐心");
        job.setSalary(new BigDecimal("25.00"));
        job.setContactPerson("张老师");
        job.setContactPhone("13800138000");
    }

    @Test
    void validJobPasses() {
        assertTrue(engine.check(job).isEmpty());
    }

    @Test
    void missingRequiredFields() {
        job.setTitle(null);
        job.setContactPhone(null);
        List<String> issues = engine.check(job);
        assertTrue(issues.stream().anyMatch(i -> i.contains("标题缺失")));
        assertTrue(issues.stream().anyMatch(i -> i.contains("联系电话缺失")));
    }

    @Test
    void lowSalaryFlagged() {
        job.setSalary(new BigDecimal("5.00"));
        assertTrue(engine.check(job).stream().anyMatch(i -> i.contains("低于校园最低标准")));
    }

    @Test
    void sensitiveWordFlagged() {
        job.setDescription("日结工资，刷单返利，快来");
        assertTrue(engine.check(job).stream().anyMatch(i -> i.contains("敏感词")));
    }

    @Test
    void shortDescriptionFlagged() {
        job.setDescription("好");
        assertTrue(engine.check(job).stream().anyMatch(i -> i.contains("描述过短")));
    }
}
