package com.workstudy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Application {
    private Long id;
    private Long jobId;
    private String jobTitle;
    private Long userId;
    private String resumeUrl;
    private String coverLetter;
    private Integer status;
    private String auditRemark;
    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
    private Long auditorId;
    private LocalDateTime interviewTime;
}
