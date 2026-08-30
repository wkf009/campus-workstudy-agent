package com.workstudy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推荐记录（Agent 1 智能推荐留痕，用于效果评估与面试展示）。
 */
@Data
public class RecommendationLog {
    private Long id;
    private Long userId;
    private Long jobId;
    private BigDecimal score;
    private String reason;
    private String strategies;   // JSON 数组字符串，如 ["语义匹配","同部门"]
    private LocalDateTime createdAt;
}
