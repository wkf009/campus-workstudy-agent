package com.workstudy.agent.jobmatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.Application;
import com.workstudy.entity.StudentProfile;
import com.workstudy.entity.User;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.StudentProfileMapper;
import com.workstudy.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学生画像服务（Agent 1）：
 * 从 sys_user（专业/年级）+ job_application（历史申请）中，
 * 由 LLM 抽取结构化求职画像（JSON），落库持久化并生成画像摘要文本（供向量化）。
 *
 * 画像的向量化由 RecommendationService 在推荐时完成（每次用最新画像，避免索引过期）。
 */
@Service
public class StudentProfileService {

    private final UserMapper userMapper;
    private final ApplicationMapper applicationMapper;
    private final StudentProfileMapper profileMapper;
    private final ChatService chatService;
    private final LlmJsonParser jsonParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StudentProfileService(UserMapper userMapper,
                                 ApplicationMapper applicationMapper,
                                 StudentProfileMapper profileMapper,
                                 ChatService chatService,
                                 LlmJsonParser jsonParser) {
        this.userMapper = userMapper;
        this.applicationMapper = applicationMapper;
        this.profileMapper = profileMapper;
        this.chatService = chatService;
        this.jsonParser = jsonParser;
    }

    /**
     * 获取画像：已有则返回，没有则构建（懒构建）。
     */
    public StudentProfile getOrBuildProfile(Long userId) {
        StudentProfile existing = profileMapper.selectByUserId(userId);
        return existing != null ? existing : buildProfile(userId);
    }

    /**
     * 重建画像：调用 LLM 抽取结构化画像并落库（幂等 upsert）。
     */
    public StudentProfile buildProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        List<Application> applications = applicationMapper.selectByUserId(userId);

        String input = buildInput(user, applications);
        String llmText = chatService.chat(PromptTemplates.STUDENT_PROFILE_EXTRACT, input);
        Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);

        StudentProfile profile = new StudentProfile();
        profile.setUserId(userId);
        profile.setMajor(LlmJsonParser.str(parsed, "major"));
        profile.setGrade(LlmJsonParser.str(parsed, "grade"));
        profile.setSalaryPref(LlmJsonParser.str(parsed, "salaryPref"));

        List<String> skills = LlmJsonParser.strList(parsed, "skills");
        List<String> timePref = LlmJsonParser.strList(parsed, "timePref");
        List<String> interests = LlmJsonParser.strList(parsed, "interestKeywords");
        try {
            profile.setSkillTags(objectMapper.writeValueAsString(skills));
            profile.setTimePref(objectMapper.writeValueAsString(timePref));
        } catch (Exception e) {
            profile.setSkillTags("[]");
            profile.setTimePref("[]");
        }
        profile.setProfileText(buildProfileText(profile, user, interests));

        upsert(profile);
        return profile;
    }

    private String buildInput(User user, List<Application> applications) {
        String major = user.getMajor() != null ? user.getMajor() : "未填写";
        String grade = user.getStudentNo() != null && user.getStudentNo().length() >= 4
                ? "20" + user.getStudentNo().substring(0, 2) + "级" : "未填写";
        String apps = applications.isEmpty() ? "无历史申请记录"
                : applications.stream()
                    .map(a -> a.getJobTitle() != null ? a.getJobTitle() : ("岗位#" + a.getJobId()))
                    .distinct()
                    .collect(Collectors.joining("、"));
        return "专业：" + major + "；年级：" + grade
                + "\n历史申请岗位：" + apps
                + "\n简历技能：暂无结构化简历（依据专业推断）";
    }

    /**
     * 生成画像摘要文本：供向量化检索与前端展示。
     */
    private String buildProfileText(StudentProfile profile, User user, List<String> interests) {
        StringBuilder sb = new StringBuilder();
        String major = profile.getMajor() != null && !profile.getMajor().isBlank()
                ? profile.getMajor() : (user.getMajor() != null ? user.getMajor() : "未知专业");
        sb.append(major);
        if (profile.getGrade() != null && !profile.getGrade().isBlank()) {
            sb.append(" ").append(profile.getGrade());
        }
        sb.append(" 学生；技能：");
        sb.append(formatList(profile.getSkillTags(), "无"));
        sb.append("；可工作时段：");
        sb.append(formatList(profile.getTimePref(), "不限"));
        if (profile.getSalaryPref() != null && !profile.getSalaryPref().isBlank()) {
            sb.append("；期望薪资：").append(profile.getSalaryPref());
        }
        if (!interests.isEmpty()) {
            sb.append("；兴趣岗位：").append(String.join("、", interests));
        }
        return sb.toString();
    }

    private String formatList(String json, String fallback) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return fallback;
        }
        try {
            List<String> list = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return list.isEmpty() ? fallback : String.join("、", list);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void upsert(StudentProfile profile) {
        StudentProfile existing = profileMapper.selectByUserId(profile.getUserId());
        if (existing != null) {
            profile.setId(existing.getId());
            profileMapper.update(profile);
        } else {
            profileMapper.insert(profile);
        }
    }
}
