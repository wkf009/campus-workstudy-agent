package com.workstudy.agent.jobmatch;

import com.workstudy.entity.Application;
import com.workstudy.entity.Job;
import com.workstudy.service.ApplicationService;
import com.workstudy.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobToolsTest {

    @Mock
    private JobService jobService;
    @Mock
    private ApplicationService applicationService;

    private Job job;

    @BeforeEach
    void setUp() {
        job = new Job();
        job.setId(1L);
        job.setTitle("图书馆助理");
        job.setDepartmentName("图书馆");
        job.setDescription("协助图书馆日常管理");
        job.setSalary(new BigDecimal("25.00"));
        job.setStatus(1);
    }

    @Test
    void searchJobsByKeyword() {
        when(jobService.getAllPublishedJobs()).thenReturn(List.of(job));
        JobTools tools = new JobTools(3L, jobService, applicationService);
        String result = tools.searchJobs("图书馆");
        assertTrue(result.contains("图书馆助理"));
    }

    @Test
    void searchJobsNoMatch() {
        when(jobService.getAllPublishedJobs()).thenReturn(List.of(job));
        JobTools tools = new JobTools(3L, jobService, applicationService);
        String result = tools.searchJobs("不存在的关键词");
        assertEquals("[]", result);
    }

    @Test
    void getJobDetail() {
        when(jobService.selectById(1L)).thenReturn(job);
        JobTools tools = new JobTools(3L, jobService, applicationService);
        String result = tools.getJobDetail(1L);
        assertTrue(result.contains("图书馆助理"));
        assertTrue(result.contains("25.00"));
    }

    @Test
    void getJobDetailNotFound() {
        when(jobService.selectById(99L)).thenReturn(null);
        JobTools tools = new JobTools(3L, jobService, applicationService);
        assertTrue(tools.getJobDetail(99L).contains("岗位不存在"));
    }

    @Test
    void submitApplicationSuccess() {
        when(jobService.selectById(1L)).thenReturn(job);
        when(applicationService.getApplicationsByStudent(3L)).thenReturn(List.of());
        Application saved = new Application();
        saved.setId(10L);
        when(applicationService.submitApplication(any(Application.class))).thenReturn(saved);

        JobTools tools = new JobTools(3L, jobService, applicationService);
        String result = tools.submitApplication(1L);

        assertTrue(result.contains("成功申请"));
        verify(applicationService).submitApplication(any(Application.class));
    }

    @Test
    void submitApplicationDuplicated() {
        when(jobService.selectById(1L)).thenReturn(job);
        Application applied = new Application();
        applied.setJobId(1L);
        when(applicationService.getApplicationsByStudent(3L)).thenReturn(List.of(applied));

        JobTools tools = new JobTools(3L, jobService, applicationService);
        String result = tools.submitApplication(1L);

        assertTrue(result.contains("已申请过"));
        verify(applicationService, never()).submitApplication(any());
    }
}
