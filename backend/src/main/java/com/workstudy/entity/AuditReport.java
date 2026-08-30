package com.workstudy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 审核报告（Agent 2 智能审核）。
 * 岗位预审（target_type=job）：suggestion=PASS/SUPPLEMENT/REJECT；
 * 申请匹配（target_type=application）：suggestion=空，score=匹配分。
 */
@Data
public class AuditReport {
    private Long id;
    private String targetType;
    private Long targetId;
    private String suggestion;
    private BigDecimal score;
    private String reasons;     // JSON 数组字符串
    private String suggestions; // JSON 数组字符串
    private String riskFlags;   // JSON 数组字符串
    private String agentModel;
    private LocalDateTime createdAt;
}
