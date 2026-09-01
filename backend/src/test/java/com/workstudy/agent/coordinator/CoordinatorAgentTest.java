package com.workstudy.agent.coordinator;

import com.workstudy.agent.audit.JobAuditAgent;
import com.workstudy.agent.jobmatch.JobWriterAgent;
import com.workstudy.entity.AgentTask;
import com.workstudy.entity.AuditReport;
import com.workstudy.mapper.AgentTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
