package com.workstudy.agent.jobmatch.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐结果 VO（含可解释推荐理由）。
 */
@Data
public class RecommendationVO {
    private Long jobId;
    private String title;
    private String departmentName;
    private BigDecimal salary;
    private String location;
    private String workTime;
    private Double score;          // 精排分数 0-100
    private String reason;         // 推荐理由（LLM 生成）
    private List<String> strategies; // 命中的召回策略：语义匹配/同部门/相似学生申请
}
