package com.workstudy.agent.coordinator;

import com.workstudy.agent.audit.JobAuditAgent;
import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.agent.jobmatch.JobWriterAgent;
import com.workstudy.agent.jobmatch.RecommendationService;
import com.workstudy.agent.jobmatch.StudentProfileService;
import com.workstudy.agent.jobmatch.dto.RecommendationVO;
import com.workstudy.entity.AgentTask;
import com.workstudy.entity.Application;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.entity.StudentProfile;
import com.workstudy.agent.notify.NotificationAgent;
import com.workstudy.llm.ChatService;
import com.workstudy.mapper.AgentTaskMapper;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoordinatorAgentTest {

    @Mock
    private AgentTaskMapper taskMapper;
    @Mock
    private JobAuditAgent jobAuditAgent;
    @Mock
    private JobWriterAgent jobWriterAgent;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private StudentProfileService profileService;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;
    @Mock
    private NotificationAgent notificationAgent;

    @InjectMocks
    private CoordinatorAgent coordinatorAgent;

    @Test
    void onJobSubmittedTriggersAuditAndMarksSuccess() {
        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(null);
        when(taskMapper.insert(any())).thenReturn(1);
        AuditReport report = new AuditReport();
        report.setSuggestion("PASS");
        when(jobAuditAgent.generateJobReport(1L)).thenReturn(report);
        when(taskMapper.updateResult(any())).thenReturn(1);

        coordinatorAgent.onJobSubmitted(1L);

        verify(jobAuditAgent).generateJobReport(1L);
        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskMapper).updateResult(captor.capture());
        assertEquals(AgentTask.STATUS_SUCCESS, captor.getValue().getStatus());
        assertEquals("JOB_AUDIT", captor.getValue().getTaskType());
    }

    @Test
    void onJobSubmittedIdempotentWhenAlreadySuccess() {
        AgentTask success = new AgentTask();
        success.setStatus(AgentTask.STATUS_SUCCESS);
        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(success);

        coordinatorAgent.onJobSubmitted(1L);

        verify(jobAuditAgent, never()).generateJobReport(any());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void onJobSubmittedFailureIsolatedFromBusiness() {
        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(null);
        when(taskMapper.insert(any())).thenReturn(1);
        when(jobAuditAgent.generateJobReport(1L)).thenThrow(new RuntimeException("LLM 不可用"));
        when(taskMapper.updateResult(any())).thenReturn(1);

        // Agent 异常不允许向上抛出（否则会回滚岗位提交事务）
        assertDoesNotThrow(() -> coordinatorAgent.onJobSubmitted(1L));

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskMapper).updateResult(captor.capture());
        assertEquals(AgentTask.STATUS_FAILED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getErrorMsg());
    }

    @Test
    void onJobSubmittedRevisesSupplementRound() {
        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(null);
        when(taskMapper.insert(any())).thenReturn(1);

        // 第一轮 SUPPLEMENT → 修订 → 第二轮 PASS
        AuditReport supplement = new AuditReport();
        supplement.setSuggestion("SUPPLEMENT");
        AuditReport pass = new AuditReport();
        pass.setSuggestion("PASS");
        when(jobAuditAgent.generateJobReport(1L)).thenReturn(supplement, pass);
        when(taskMapper.updateResult(any())).thenReturn(1);

        coordinatorAgent.onJobSubmitted(1L);

        verify(jobWriterAgent).reviseJob(eq(1L), any(AuditReport.class)); // 自动修订 1 次
        verify(jobAuditAgent, times(2)).generateJobReport(1L);           // 预审 2 次
    }

    @Test
    void onJobSubmittedStopsAfterMaxRounds() {
        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(null);
        when(taskMapper.insert(any())).thenReturn(1);

        AuditReport supplement = new AuditReport();
        supplement.setSuggestion("SUPPLEMENT");
        // 一直 SUPPLEMENT
        when(jobAuditAgent.generateJobReport(1L)).thenReturn(supplement);
        when(taskMapper.updateResult(any())).thenReturn(1);

        coordinatorAgent.onJobSubmitted(1L);

        // 最多修订 2 轮（防死循环），然后转人工
        verify(jobWriterAgent, times(2)).reviseJob(any(), any());
    }

    @Test
    void onApplicationRejectedTriggersRematch() {
        Application app = new Application();
        app.setId(5L);
        app.setUserId(3L);
        app.setJobId(1L);
        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(null);
        when(taskMapper.insert(any())).thenReturn(1);
        when(applicationMapper.selectById(5L)).thenReturn(app);
        when(jobMapper.selectById(1L)).thenReturn(new Job());
        when(profileService.getOrBuildProfile(3L)).thenReturn(new StudentProfile());
        RecommendationVO vo = new RecommendationVO();
        vo.setJobId(2L);
        vo.setTitle("图书馆助理");
        when(recommendationService.recommend(3L, 2)).thenReturn(List.of(vo));
        when(taskMapper.updateResult(any())).thenReturn(1);

        coordinatorAgent.onApplicationProcessed(5L, 2);

        verify(notificationService).sendNotification(eq(3L), contains("替代岗位"), anyString(), eq(2), any());
        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskMapper).updateResult(captor.capture());
        assertEquals(AgentTask.STATUS_SUCCESS, captor.getValue().getStatus());
    }

    @Test
    void onApplicationAcceptedDoesNotRematch() {
        // result=1 录用：不触发撮合（M6 面试安排）
        coordinatorAgent.onApplicationProcessed(5L, 1);

        verify(taskMapper, never()).insert(any());
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any());
    }

    @Test
    void onJobLifecycleGeneratesAdviceAndNotifies() {
        Job job = new Job();
        job.setId(9L);
        job.setTitle("学生处助理");
        job.setDepartmentId(1L);
        job.setPublisherId(2L);
        job.setStatus(1);
        job.setSalary(new BigDecimal("20.00"));
        job.setUpdateTime(LocalDateTime.now().minusDays(30));

        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(null);
        when(taskMapper.insert(any())).thenReturn(1);
        when(jobMapper.selectById(9L)).thenReturn(job);
        when(jobMapper.selectByDepartment(1L)).thenReturn(List.of(job)); // 排除自身 → 均值 0
        when(applicationMapper.selectByJobId(9L)).thenReturn(List.of());
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "issue", "薪资偏低",
                "suggestion", "建议调整到 25 元/时",
                "reason", "同类岗位均值更高"
        ));
        when(taskMapper.updateResult(any())).thenReturn(1);

        coordinatorAgent.onJobLifecycle(9L);

        verify(notificationService).sendNotification(eq(2L), contains("调整建议"), anyString(), eq(1), eq(9L));
        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskMapper).updateResult(captor.capture());
        assertEquals(AgentTask.STATUS_SUCCESS, captor.getValue().getStatus());
    }

    @Test
    void onJobLifecycleSkipsNonPublishedJob() {
        Job job = new Job();
        job.setId(9L);
        job.setStatus(0); // 非招聘中
        when(taskMapper.selectLatest(anyString(), anyString(), any())).thenReturn(null);
        when(taskMapper.insert(any())).thenReturn(1);
        when(jobMapper.selectById(9L)).thenReturn(job);
        when(taskMapper.updateResult(any())).thenReturn(1);

        coordinatorAgent.onJobLifecycle(9L);

        verify(chatService, never()).chat(anyString(), anyString());
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any());
    }
}
