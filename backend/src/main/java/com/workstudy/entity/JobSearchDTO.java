package com.workstudy.entity;

import lombok.Data;

@Data
public class JobSearchDTO {
    private String keyword;
    private Long departmentId;
    private String location;
    private java.math.BigDecimal minSalary;
    private java.math.BigDecimal maxSalary;
    private Integer status;
    private Integer page = 1;
    private Integer pageSize = 10;
}
