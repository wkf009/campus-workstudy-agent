package com.workstudy.agent.eval;

import com.workstudy.entity.Application;
import com.workstudy.entity.RecommendationLog;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.RecommendationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentEvalServiceTest {

    @Mock
    private RecommendationLogMapper logMapper;
    @Mock
    private ApplicationMapper applicationMapper;

    @InjectMocks
    private AgentEvalService agentEvalService;

    @Test
    void computeMetricsComputesHitRate() {
        RecommendationLog r1 = new RecommendationLog();
        r1.setUserId(3L);
        r1.setJobId(1L);
        r1.setScore(new BigDecimal("90"));
        RecommendationLog r2 = new RecommendationLog();
        r2.setUserId(3L);
        r2.setJobId(2L);
        r2.setScore(new BigDecimal("60"));
        when(logMapper.selectAll()).thenReturn(List.of(r1, r2));

        Application applied = new Application();
        applied.setUserId(3L);
        applied.setJobId(1L); // 申请了被推荐过的岗位 1
        when(applicationMapper.selectAll()).thenReturn(List.of(applied));

        Map<String, Object> metrics = agentEvalService.computeMetrics();

        assertEquals(2L, ((Number) metrics.get("totalRecommendations")).longValue());
        assertEquals(50.0, metrics.get("hitRate")); // 2 个推荐岗位中 1 个被申请
        assertEquals(1L, ((Number) metrics.get("hitJobCount")).longValue());
        assertEquals(2L, ((Number) metrics.get("uniqueJobs")).longValue());
    }

    @Test
    void computeMetricsHandlesEmptyData() {
        when(logMapper.selectAll()).thenReturn(List.of());
        when(applicationMapper.selectAll()).thenReturn(List.of());

        Map<String, Object> metrics = agentEvalService.computeMetrics();

        assertEquals(0, metrics.get("totalRecommendations"));
        assertEquals(0.0, metrics.get("hitRate"));
        assertEquals(0.0, metrics.get("avgRecommendPerStudent"));
    }
}
