package com.workstudy.agent.jobmatch;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.llm.ChatService;
import com.workstudy.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobWriterAgentTest {

    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;
    @Mock
    private JobMapper jobMapper;

    @InjectMocks
    private JobWriterAgent jobWriterAgent;

    private Job job;

    @BeforeEach
    void setUp() {
        job = new Job();
        job.setId(1L);
        job.setTitle("机房值班");
        job.setDescription("负责机房值班，设备开关机");
        job.setRequirements("熟悉计算机");
        job.setWorkTime("晚上");
        job.setStatus(0);
    }

    @Test
    void generateDraftParsesStructuredOutput() {
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "title", "机房值班助理",
                "description", "负责机房日常值班、设备开关机与登记管理",
                "requirements", "熟悉计算机基础操作；认真负责",
                "workTime", "周一至周五 18:00-21:00",
                "salarySuggest", 22.0
        ));

        JobWriterAgent.JobDraft draft = jobWriterAgent.generateDraft("机房值班，晚上");

        assertEquals("机房值班助理", draft.getTitle());
        assertEquals(new BigDecimal("22.0"), draft.getSalarySuggest());
        assertNotNull(draft.getDescription());
    }

    @Test
    void generateDraftThrowsWhenTitleMissing() {
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of("description", "无标题"));

        assertThrows(RuntimeException.class, () -> jobWriterAgent.generateDraft("测试"));
    }

    @Test
    void reviseJobAppliesChangesAndPersists() {
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "title", "机房值班助理（修订）",
                "description", "负责机房日常值班、设备开关机与登记管理，需按时到岗",
                "requirements", "熟悉计算机基础操作；认真负责；有值班经验优先",
                "workTime", "周一至周五 18:00-21:00"
        ));

        AuditReport audit = new AuditReport();
        audit.setReasons("[\"岗位描述过短\"]");

        Job revised = jobWriterAgent.reviseJob(1L, audit);

        assertEquals("机房值班助理（修订）", revised.getTitle());
        assertTrue(revised.getDescription().contains("按时到岗"));
        verify(jobMapper).update(job);
    }

    @Test
    void reviseJobKeepsOriginalWhenLlmFails() {
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 不可用"));

        AuditReport audit = new AuditReport();
        audit.setReasons("[\"薪资未填写\"]");

        Job revised = jobWriterAgent.reviseJob(1L, audit);

        assertEquals("机房值班", revised.getTitle()); // 保持原文
        verify(jobMapper, never()).update(any());
    }
}
