package com.workstudy.service;

import com.workstudy.agent.coordinator.CoordinatorAgent;
import com.workstudy.entity.Job;
import com.workstudy.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobMapper jobMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CoordinatorAgent coordinatorAgent;

    @InjectMocks
    private JobService jobService;

    private Job testJob;

    @BeforeEach
    void setUp() {
        testJob = new Job();
        testJob.setId(1L);
        testJob.setTitle("图书馆助理");
        testJob.setDepartmentId(3L);
        testJob.setDepartmentName("图书馆");
        testJob.setSalary(new BigDecimal("25.00"));
        testJob.setQuota(3);
        testJob.setStatus(1);
        testJob.setPublisherId(2L);
    }

    @Test
    void testSubmitJob() {
        when(jobMapper.insert(any(Job.class))).thenReturn(1);
        Job result = jobService.submitJob(testJob);
        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(jobMapper).insert(any(Job.class));
    }

    @Test
    void testAuditJobApprove() {
        testJob.setStatus(0);
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobMapper.update(any(Job.class))).thenReturn(1);
        Job result = jobService.auditJob(1L, 1, "通过");
        assertEquals(1, result.getStatus());
    }

    @Test
    void testAuditJobReject() {
        testJob.setStatus(0);
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobMapper.update(any(Job.class))).thenReturn(1);
        Job result = jobService.auditJob(1L, 0, "拒绝");
        assertEquals(3, result.getStatus());
    }

    @Test
    void testAuditJobNotFound() {
        when(jobMapper.selectById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> jobService.auditJob(99L, 1, ""));
    }

    @Test
    void testGetAllPublishedJobsWithRecommendation() {
        when(jobMapper.selectAllPublished()).thenReturn(java.util.List.of(testJob));
        var jobs = jobService.getAllPublishedJobsWithRecommendation(3L);
        assertEquals(1, jobs.size());
        assertEquals("1", jobs.get(0).getRemark());
    }

    @Test
    void testDeleteFullJobSuccess() {
        testJob.setStatus(4);
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobMapper.deleteById(1L)).thenReturn(1);
        boolean result = jobService.deleteFullJob(1L, 2L, 2, 3L);
        assertTrue(result);
    }

    @Test
    void testDeleteFullJobNotFull() {
        testJob.setStatus(1);
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        assertThrows(RuntimeException.class,
                () -> jobService.deleteFullJob(1L, 2L, 2, 3L));
    }

    @Test
    void testDeleteFullJobWrongDepartment() {
        testJob.setStatus(4);
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        assertThrows(RuntimeException.class,
                () -> jobService.deleteFullJob(1L, 2L, 2, 99L));
    }

    /**
     * 修复后的语义：重新发布 → 状态为 1（招聘中）。
     * 注意：旧实现误设为 2（已结束），且旧测试把错误行为固化为断言，一并修正。
     */
    @Test
    void testPublishJob() {
        testJob.setStatus(2); // 从"已结束"重新发布
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobMapper.update(any(Job.class))).thenReturn(1);
        Job result = jobService.publishJob(1L);
        assertEquals(1, result.getStatus());
        verify(notificationService).sendNotification(eq(2L), anyString(), anyString(), eq(1), eq(1L));
    }

    @Test
    void testPublishJobNotFound() {
        when(jobMapper.selectById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> jobService.publishJob(99L));
    }

    @Test
    void testAuditJobSendsNotification() {
        testJob.setStatus(0);
        when(jobMapper.selectById(1L)).thenReturn(testJob);
        when(jobMapper.update(any(Job.class))).thenReturn(1);
        jobService.auditJob(1L, 1, "通过");
        verify(notificationService).sendNotification(eq(2L), eq("岗位审批通过"), anyString(), eq(1), eq(1L));
    }

    @Test
    void testGetPublishedJobsPage() {
        Job sameDept = new Job();
        sameDept.setId(2L);
        sameDept.setDepartmentId(3L);
        Job otherDept = new Job();
        otherDept.setId(3L);
        otherDept.setDepartmentId(1L);

        when(jobMapper.selectPublishedOrdered(eq(3L), isNull(), isNull()))
                .thenReturn(java.util.List.of(sameDept, otherDept));

        com.workstudy.common.PageResult<Job> result =
                jobService.getPublishedJobsPage(3L, null, null, 1, 10);

        assertEquals(2, result.getTotal());
        assertEquals("1", result.getRecords().get(0).getRemark()); // 本部门推荐
        assertEquals("0", result.getRecords().get(1).getRemark()); // 其他部门
    }
}
