package com.workstudy.agent.audit;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.Application;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.llm.ChatService;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.AuditReportMapper;
import com.workstudy.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationMatchAgentTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private AuditReportMapper auditReportMapper;
    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;

    @InjectMocks
    private ApplicationMatchAgent applicationMatchAgent;

    private Application app;
    private Job job;

    @BeforeEach
    void setUp() {
        app = new Application();
        app.setId(10L);
        app.setJobId(1L);
        app.setCoverLetter("我对图书馆工作很感兴趣，有耐心且细心");
        job = new Job();
        job.setId(1L);
        job.setRequirements("认真负责，有图书管理经验优先");
        job.setDescription("图书馆日常管理");
    }

    @Test
    void evaluateApplicationProducesReport() {
        when(applicationMapper.selectById(10L)).thenReturn(app);
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "matchScore", 88.0,
                "matchedPoints", List.of("有耐心细心", "对图书馆岗位有意愿"),
                "gapPoints", List.of("无图书管理经验"),
                "interviewHint", "可询问其可值班时段"
        ));
        when(auditReportMapper.selectByTarget("application", 10L)).thenReturn(null);
        when(auditReportMapper.insert(any())).thenReturn(1);

        AuditReport report = applicationMatchAgent.evaluateApplication(10L);

        assertEquals(88.0, report.getScore().doubleValue());
        assertEquals("88", report.getSuggestion());
        assertTrue(report.getReasons().contains("有耐心细心"));
        verify(auditReportMapper).insert(any());
    }

    @Test
    void evaluateApplicationFallsBackWhenLlmFails() {
        when(applicationMapper.selectById(10L)).thenReturn(app);
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 不可用"));
        when(auditReportMapper.selectByTarget("application", 10L)).thenReturn(null);
        when(auditReportMapper.insert(any())).thenReturn(1);

        AuditReport report = applicationMatchAgent.evaluateApplication(10L);

        assertEquals(60.0, report.getScore().doubleValue()); // 兜底分
        verify(auditReportMapper).insert(any());
    }
}
