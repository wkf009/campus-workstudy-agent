package com.workstudy.agent.interview;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.Application;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.entity.StudentProfile;
import com.workstudy.llm.ChatService;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.AuditReportMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.mapper.StudentProfileMapper;
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
class InterviewAgentTest {

    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private StudentProfileMapper profileMapper;
    @Mock
    private AuditReportMapper auditReportMapper;

    @InjectMocks
    private InterviewAgent interviewAgent;

    @Test
    void planGeneratesTimeAndQuestions() {
        Application app = new Application();
        app.setId(5L);
        app.setUserId(3L);
        app.setJobId(1L);
        when(applicationMapper.selectById(5L)).thenReturn(app);
        Job job = new Job();
        job.setId(1L);
        job.setWorkTime("周一至周五 18:00-21:00");
        job.setLocation("信息楼机房");
        when(jobMapper.selectById(1L)).thenReturn(job);

        StudentProfile profile = new StudentProfile();
        profile.setTimePref("[\"晚上\",\"周末\"]");
        when(profileMapper.selectByUserId(3L)).thenReturn(profile);

        AuditReport match = new AuditReport();
        match.setSuggestions("[\"无值班经验\"]");
        match.setRiskFlags("[\"可询问其时间安排\"]");
        when(auditReportMapper.selectByTarget("application", 5L)).thenReturn(match);

        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "suggestedTimes", List.of(
                        Map.of("slot", "周二 18:00", "reason", "学生晚上空闲且岗位此时段值班"),
                        Map.of("slot", "周六 10:00", "reason", "周末时间充裕")
                ),
                "questions", List.of("你如何处理设备故障？", "你能保证每周至少 3 晚值班吗？"),
                "tips", "提前熟悉机房设备开关机流程"
        ));

        InterviewAgent.InterviewPlan plan = interviewAgent.plan(5L);

        assertEquals(2, plan.getSuggestedTimes().size());
        assertTrue(plan.getSuggestedTimes().get(0).getSlot().contains("周二"));
        assertEquals(2, plan.getQuestions().size());
        assertNotNull(plan.getTips());
    }

    @Test
    void planReturnsNullWhenJobMissing() {
        Application app = new Application();
        app.setId(5L);
        app.setUserId(3L);
        app.setJobId(99L);
        when(applicationMapper.selectById(5L)).thenReturn(app);
        when(jobMapper.selectById(99L)).thenReturn(null);

        assertNull(interviewAgent.plan(5L));
    }
}
