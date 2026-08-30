package com.workstudy.agent.audit;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.entity.User;
import com.workstudy.llm.ChatService;
import com.workstudy.mapper.AuditReportMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.service.NotificationService;
import com.workstudy.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobAuditAgentTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private AuditRuleEngine ruleEngine;
    @Mock
    private AuditReportMapper auditReportMapper;
    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private JobAuditAgent jobAuditAgent;

    private Job job;

    @BeforeEach
    void setUp() {
        job = new Job();
        job.setId(1L);
        job.setTitle("图书馆助理");
        job.setDescription("协助图书馆日常管理，整理图书上架");
        job.setRequirements("认真负责");
        job.setSalary(new BigDecimal("25.00"));
        job.setDepartmentName("图书馆");
        job.setContactPerson("张老师");
        job.setContactPhone("13800138000");
    }

    @Test
    void generateReportMergesRuleAndLlm() {
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(ruleEngine.check(job)).thenReturn(List.of("薪资未填写"));
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "suggestion", "SUPPLEMENT",
                "score", 66.0,
                "reasons", List.of("薪资需补充"),
                "suggestions", List.of("填写具体时薪"),
                "riskFlags", List.of("信息不全")
        ));
        when(auditReportMapper.selectByTarget("job", 1L)).thenReturn(null);
        when(auditReportMapper.insert(any())).thenReturn(1);
        when(userService.getUsersByRole(3)).thenReturn(List.of());

        AuditReport report = jobAuditAgent.generateJobReport(1L);

        assertEquals("SUPPLEMENT", report.getSuggestion());
        assertEquals(66.0, report.getScore().doubleValue());
        assertTrue(report.getRiskFlags().contains("薪资未填写")); // 规则并入
        verify(auditReportMapper).insert(any());
    }

    @Test
    void generateReportFallsBackWhenLlmEmpty() {
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(ruleEngine.check(job)).thenReturn(List.of("薪资未填写"));
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 不可用"));
        when(auditReportMapper.selectByTarget("job", 1L)).thenReturn(null);
        when(auditReportMapper.insert(any())).thenReturn(1);
        when(userService.getUsersByRole(3)).thenReturn(List.of());

        AuditReport report = jobAuditAgent.generateJobReport(1L);

        assertEquals("SUPPLEMENT", report.getSuggestion()); // 规则兜底
        assertFalse(report.getReasons().isEmpty());
    }

    @Test
    void adoptPassPublishesJob() {
        AuditReport report = new AuditReport();
        report.setSuggestion("PASS");
        when(auditReportMapper.selectByTarget("job", 1L)).thenReturn(report);
        when(jobMapper.updateStatus(eq(1L), eq(1), anyString())).thenReturn(1);

        String message = jobAuditAgent.adoptSuggestion(1L);

        assertTrue(message.contains("通过"));
        verify(jobMapper).updateStatus(eq(1L), eq(1), anyString());
    }

    @Test
    void adoptWithoutReportFails() {
        when(auditReportMapper.selectByTarget("job", 1L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> jobAuditAgent.adoptSuggestion(1L));
    }
}
