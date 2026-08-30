package com.workstudy.agent.jobmatch;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.agent.jobmatch.dto.RecommendationVO;
import com.workstudy.entity.Application;
import com.workstudy.entity.Job;
import com.workstudy.entity.StudentProfile;
import com.workstudy.entity.User;
import com.workstudy.llm.ChatService;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.mapper.RecommendationLogMapper;
import com.workstudy.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private StudentProfileService profileService;
    @Mock
    private JobVectorService jobVectorService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private RecommendationLogMapper logMapper;
    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;

    @InjectMocks
    private RecommendationService recommendationService;

    private StudentProfile profile;
    private User user;
    private Job job1;
    private Job job2;

    @BeforeEach
    void setUp() {
        profile = new StudentProfile();
        profile.setUserId(3L);
        profile.setMajor("计算机");
        profile.setProfileText("计算机学院学生；技能：Java；可工作时段：晚上");

        user = new User();
        user.setId(3L);
        user.setMajor("计算机");
        user.setDepartmentId(1L);

        job1 = new Job();
        job1.setId(1L);
        job1.setTitle("图书馆助理");
        job1.setDepartmentId(3L);
        job1.setDepartmentName("图书馆");
        job1.setSalary(new BigDecimal("25.00"));
        job1.setStatus(1);

        job2 = new Job();
        job2.setId(2L);
        job2.setTitle("机房值班");
        job2.setDepartmentId(1L);
        job2.setDepartmentName("计算机学院");
        job2.setSalary(new BigDecimal("22.00"));
        job2.setStatus(1);
    }

    @Test
    void recommendWithLlmRerank() {
        when(profileService.getOrBuildProfile(3L)).thenReturn(profile);
        when(jobVectorService.isEmpty()).thenReturn(false);
        when(applicationMapper.selectByUserId(3L)).thenReturn(List.of());
        when(userMapper.selectById(3L)).thenReturn(user);

        // R1 语义召回命中 job1
        Document doc1 = Document.builder().id("job_1").text("岗位：图书馆助理").metadata(Map.of("jobId", 1L)).build();
        when(jobVectorService.search(anyString(), anyInt())).thenReturn(List.of(doc1));
        // R2 同部门命中 job2
        when(jobMapper.selectByStatus(1)).thenReturn(List.of(job1, job2));
        // R3 无相似学生
        when(userMapper.selectByRole(0)).thenReturn(List.of(user));

        // 候选 job1、job2 的详情
        when(jobMapper.selectById(1L)).thenReturn(job1);
        when(jobMapper.selectById(2L)).thenReturn(job2);

        // LLM 精排
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonArray(anyString())).thenReturn(List.of(
                Map.of("jobId", 1L, "score", 92.0, "reason", "技能高度匹配"),
                Map.of("jobId", 2L, "score", 78.0, "reason", "同部门方便"))
        );
        when(logMapper.insert(any())).thenReturn(1);

        List<RecommendationVO> result = recommendationService.recommend(3L, 5);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getJobId()); // score 92 优先
        assertEquals(92.0, result.get(0).getScore());
        assertEquals("技能高度匹配", result.get(0).getReason());
        assertTrue(result.get(0).getStrategies().contains("语义匹配"));
        assertTrue(result.get(1).getStrategies().contains("同部门"));
        verify(logMapper, times(2)).insert(any());
    }

    @Test
    void recommendExcludesAppliedJobs() {
        when(profileService.getOrBuildProfile(3L)).thenReturn(profile);
        when(jobVectorService.isEmpty()).thenReturn(false);

        Application applied = new Application();
        applied.setJobId(1L); // 已申请过 job1
        when(applicationMapper.selectByUserId(3L)).thenReturn(List.of(applied));
        when(userMapper.selectById(3L)).thenReturn(user);

        Document doc1 = Document.builder().id("job_1").text("岗位：图书馆助理").metadata(Map.of("jobId", 1L)).build();
        when(jobVectorService.search(anyString(), anyInt())).thenReturn(List.of(doc1));
        when(jobMapper.selectByStatus(1)).thenReturn(List.of(job1, job2));
        when(userMapper.selectByRole(0)).thenReturn(List.of(user));

        when(jobMapper.selectById(2L)).thenReturn(job2);
        when(chatService.chat(anyString(), anyString())).thenReturn("[]");
        when(jsonParser.parseJsonArray(anyString())).thenReturn(List.of(
                Map.of("jobId", 2L, "score", 80.0, "reason", "推荐")));
        when(logMapper.insert(any())).thenReturn(1);

        List<RecommendationVO> result = recommendationService.recommend(3L, 5);

        // 已申请的 job1 被排除
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getJobId());
    }

    @Test
    void recommendFallsBackWhenLlmFails() {
        when(profileService.getOrBuildProfile(3L)).thenReturn(profile);
        when(jobVectorService.isEmpty()).thenReturn(false);
        when(applicationMapper.selectByUserId(3L)).thenReturn(List.of());
        when(userMapper.selectById(3L)).thenReturn(user);

        Document doc1 = Document.builder().id("job_1").text("岗位：图书馆助理").metadata(Map.of("jobId", 1L)).build();
        when(jobVectorService.search(anyString(), anyInt())).thenReturn(List.of(doc1));
        when(jobMapper.selectByStatus(1)).thenReturn(List.of(job1, job2));
        when(userMapper.selectByRole(0)).thenReturn(List.of(user));
        when(jobMapper.selectById(1L)).thenReturn(job1);
        when(jobMapper.selectById(2L)).thenReturn(job2);

        // LLM 调用异常 → 降级规则打分
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 不可用"));
        when(logMapper.insert(any())).thenReturn(1);

        List<RecommendationVO> result = recommendationService.recommend(3L, 5);

        assertEquals(2, result.size());
        assertNotNull(result.get(0).getReason());
        assertTrue(result.get(0).getScore() > 0);
    }
}
