package com.workstudy.agent.interview;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.Application;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.entity.StudentProfile;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.AuditReportMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.mapper.StudentProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 面试安排 Agent（M6）：录用后生成面试时间建议 + 针对性面试题 + 准备指引。
 * 输入复用：学生画像时段(timePref) + 岗位时间/地点 + 匹配报告的差距点(gapPoints)/面试建议(interviewHint)。
 */
@Service
public class InterviewAgent {

    private static final Logger log = LoggerFactory.getLogger(InterviewAgent.class);

    private final ChatService chatService;
    private final LlmJsonParser jsonParser;
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final StudentProfileMapper profileMapper;
    private final AuditReportMapper auditReportMapper;

    public InterviewAgent(ChatService chatService,
                          LlmJsonParser jsonParser,
                          ApplicationMapper applicationMapper,
                          JobMapper jobMapper,
                          StudentProfileMapper profileMapper,
                          AuditReportMapper auditReportMapper) {
        this.chatService = chatService;
        this.jsonParser = jsonParser;
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.profileMapper = profileMapper;
        this.auditReportMapper = auditReportMapper;
    }

    /**
     * 为录用申请生成面试安排建议。失败返回 null（调用方降级）。
     */
    public InterviewPlan plan(Long applicationId) {
        try {
            Application app = applicationMapper.selectById(applicationId);
            if (app == null || app.getUserId() == null) {
                return null;
            }
            Job job = app.getJobId() != null ? jobMapper.selectById(app.getJobId()) : null;
            if (job == null) {
                return null;
            }
            StudentProfile profile = profileMapper.selectByUserId(app.getUserId());
            AuditReport match = auditReportMapper.selectByTarget("application", applicationId);

            String timePref = profile != null && profile.getTimePref() != null ? profile.getTimePref() : "[]";
            String gaps = match != null && match.getSuggestions() != null ? match.getSuggestions() : "[]";
            String hints = match != null && match.getRiskFlags() != null ? match.getRiskFlags() : "[]";

            String input = "学生可工作时段：" + timePref
                    + "\n岗位工作时间：" + nvl(job.getWorkTime())
                    + "\n岗位地点：" + nvl(job.getLocation())
                    + "\n学生与岗位的差距点：" + gaps
                    + "\n匹配报告面试建议：" + hints;
            String llmText = chatService.chat(PromptTemplates.INTERVIEW_PLAN, input);
            Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);

            InterviewPlan plan = new InterviewPlan();
            plan.setSuggestedTimes(parseTimes(parsed.get("suggestedTimes")));
            plan.setQuestions(LlmJsonParser.strList(parsed, "questions"));
            plan.setTips(LlmJsonParser.str(parsed, "tips"));
            return plan;
        } catch (Exception e) {
            log.warn("InterviewAgent 生成面试安排失败（申请 {}）: {}", applicationId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<InterviewPlan.TimeSlot> parseTimes(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> m = (Map<String, Object>) item;
                    InterviewPlan.TimeSlot slot = new InterviewPlan.TimeSlot();
                    slot.setSlot(LlmJsonParser.str(m, "slot"));
                    slot.setReason(LlmJsonParser.str(m, "reason"));
                    return slot;
                })
                .toList();
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "未说明" : s;
    }

    /** 面试安排 DTO */
    public static class InterviewPlan {
        private List<TimeSlot> suggestedTimes;
        private List<String> questions;
        private String tips;

        public List<TimeSlot> getSuggestedTimes() { return suggestedTimes == null ? List.of() : suggestedTimes; }
        public void setSuggestedTimes(List<TimeSlot> suggestedTimes) { this.suggestedTimes = suggestedTimes; }
        public List<String> getQuestions() { return questions == null ? List.of() : questions; }
        public void setQuestions(List<String> questions) { this.questions = questions; }
        public String getTips() { return tips; }
        public void setTips(String tips) { this.tips = tips; }

        public static class TimeSlot {
            private String slot;
            private String reason;

            public String getSlot() { return slot; }
            public void setSlot(String slot) { this.slot = slot; }
            public String getReason() { return reason; }
            public void setReason(String reason) { this.reason = reason; }
        }
    }
}
