package com.workstudy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生画像（Agent 1 智能推荐用）。
 * skillTags / timePref 以 JSON 字符串存储，Service 层负责与 List 互转。
 */
@Data
public class StudentProfile {
    private Long id;
    private Long userId;
    private String major;
    private String grade;
    private String skillTags;    // JSON 数组字符串，如 ["Java","值班"]
    private String timePref;     // JSON 数组字符串，如 ["晚上","周末"]
    private String salaryPref;
    private String profileText;  // 画像摘要文本（供向量化与展示）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
