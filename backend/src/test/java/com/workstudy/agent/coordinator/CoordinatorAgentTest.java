package com.workstudy.agent.coordinator;

import com.workstudy.agent.audit.JobAuditAgent;
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
}
