package com.workstudy.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Job {
    private Long id;
    private String title;
    private String description;
    private String requirements;
    private BigDecimal salary;
    private String location;
    private Integer quota;
    private String workTime;
    private String contactPerson;
    private String contactPhone;
    private Long departmentId;
    private String departmentName;
    private Long publisherId;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
