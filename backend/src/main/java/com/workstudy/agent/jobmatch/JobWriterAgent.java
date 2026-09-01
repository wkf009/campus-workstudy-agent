package com.workstudy.agent.jobmatch;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import com.workstudy.mapper.JobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 岗位助手 Agent（多 Agent 协作场景 1）：
 * - generateDraft：部门输入要点 → LLM 生成结构化岗位初稿（供确认）
 * - reviseJob：根据审核意见自动修订岗位（SUPPLEMENT → 修订 → 重新预审的循环引擎）
 */
@Service
public class JobWriterAgent {

    private static final Logger log = LoggerFactory.getLogger(JobWriterAgent.class);

    private final ChatService chatService;
    private final LlmJsonParser jsonParser;
    private final JobMapper jobMapper;

    public JobWriterAgent(ChatService chatService, LlmJsonParser jsonParser, JobMapper jobMapper) {
        this.chatService = chatService;
        this.jsonParser = jsonParser;
        this.jobMapper = jobMapper;
    }

    /**
     * 生成岗位初稿（返回结构化字段，由调用方决定是否落库）。
     */
    public JobDraft generateDraft(String keywords) {
        String llmText = chatService.chat(PromptTemplates.JOB_WRITER, keywords);
        Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);

        JobDraft draft = new JobDraft();
        draft.setTitle(LlmJsonParser.str(parsed, "title"));
        draft.setDescription(LlmJsonParser.str(parsed, "description"));
        draft.setRequirements(LlmJsonParser.str(parsed, "requirements"));
        draft.setWorkTime(LlmJsonParser.str(parsed, "workTime"));
        try {
            Object s = parsed.get("salarySuggest");
            if (s != null) {
                draft.setSalarySuggest(new BigDecimal(s.toString()));
            }
        } catch (Exception ignored) {
        }
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            throw new RuntimeException("AI 生成岗位初稿失败，请重试或手动填写");
        }
        return draft;
    }

    /**
     * 根据审核意见修订岗位并落库（仅修订文本/薪资字段，不触碰状态）。
     * 返回修订后的岗位。
     */
    public Job reviseJob(Long jobId, AuditReport audit) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("岗位不存在");
        }
        String auditText = audit.getReasons() == null ? "无具体意见" : audit.getReasons();
        String input = "原岗位：{标题=" + nvl(job.getTitle())
                + "；描述=" + nvl(job.getDescription())
                + "；要求=" + nvl(job.getRequirements())
                + "；时间=" + nvl(job.getWorkTime())
                + "}\n审核意见（问题列表）：" + auditText;
        String llmText;
        try {
            llmText = chatService.chat(PromptTemplates.JOB_REVISE, input);
        } catch (Exception e) {
            log.warn("AI 修订调用失败，岗位 {} 保持原文: {}", jobId, e.getMessage());
            return job;
        }
        Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);

        String title = LlmJsonParser.str(parsed, "title");
        String description = LlmJsonParser.str(parsed, "description");
        String requirements = LlmJsonParser.str(parsed, "requirements");
        String workTime = LlmJsonParser.str(parsed, "workTime");

        boolean changed = false;
        if (isValid(title)) { job.setTitle(title); changed = true; }
        if (isValid(description)) { job.setDescription(description); changed = true; }
        if (isValid(requirements)) { job.setRequirements(requirements); changed = true; }
        if (isValid(workTime)) { job.setWorkTime(workTime); changed = true; }

        if (changed) {
            job.setRemark("AI 已根据审核意见自动修订（第" + (audit.getSuggestions() == null ? "1" : "1") + "轮）");
            jobMapper.update(job);
            log.info("岗位 {} 已由 AI 修订", jobId);
        }
        return job;
    }

    private boolean isValid(String s) {
        return s != null && !s.isBlank() && !"无".equals(s.trim());
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "无" : s;
    }

    /** 岗位初稿 DTO（结构化输出） */
    public static class JobDraft {
        private String title;
        private String description;
        private String requirements;
        private String workTime;
        private BigDecimal salarySuggest;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getRequirements() { return requirements; }
        public void setRequirements(String requirements) { this.requirements = requirements; }
        public String getWorkTime() { return workTime; }
        public void setWorkTime(String workTime) { this.workTime = workTime; }
        public BigDecimal getSalarySuggest() { return salarySuggest; }
        public void setSalarySuggest(BigDecimal salarySuggest) { this.salarySuggest = salarySuggest; }
    }
}
