package com.workstudy.service;

import com.workstudy.entity.Application;
import com.workstudy.entity.Job;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private JobMapper jobMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ApplicationService applicationService;

    private Application testApp;
    private Job testJob;

    @BeforeEach
    void setUp() {
        testApp = new Application();
        testApp.setId(1L);
        testApp.setJobId(1L);
        testApp.setUserId(3L);
        testApp.setStatus(0);

        testJob = new Job();
        testJob.setId(1L);
        testJob.setQuota(2);
        testJob.setStatus(1);
        testJob.setTitle("测试岗位");
    }

    @Test
    void testSubmitApplication() {
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(applicationMapper.insert(any(Application.class))).thenReturn(1);
        Application result = applicationService.submitApplication(testApp);
        assertNotNull(result);
        assertEquals(0, result.getStatus());
        assertEquals("测试岗位", result.getJobTitle());
    }

    @Test
    void testProcessApplicationApprove() {
        when(applicationMapper.selectById(1L)).thenReturn(testApp);
        when(applicationMapper.update(any(Application.class))).thenReturn(1);
        // 录用后触发 checkAndMarkJobAsFull，需 stub 岗位信息（quota=2），否则提前 return 导致下方 stub 未被使用
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(applicationMapper.selectByJobId(1L)).thenReturn(java.util.List.of());

        Application result = applicationService.processApplication(1L, 1, "通过", 2L);
        assertEquals(1, result.getStatus());
        assertEquals("通过", result.getAuditRemark());
    }

    @Test
    void testProcessApplicationNotFound() {
        when(applicationMapper.selectById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class,
                () -> applicationService.processApplication(99L, 1, "", 2L));
    }

    @Test
    void testCheckAndMarkJobAsFull() {
        when(applicationMapper.selectById(1L)).thenReturn(testApp);
        when(applicationMapper.update(any(Application.class))).thenReturn(1);
        when(applicationMapper.selectByJobId(1L)).thenReturn(
                java.util.List.of(createApprovedApp(), createApprovedApp()));
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobMapper.update(any(Job.class))).thenReturn(1);

        applicationService.processApplication(1L, 1, "通过", 2L);
        assertEquals(4, testJob.getStatus());
    }

    private Application createApprovedApp() {
        Application app = new Application();
        app.setId(10L);
        app.setJobId(1L);
        app.setStatus(1);
        return app;
    }
}
