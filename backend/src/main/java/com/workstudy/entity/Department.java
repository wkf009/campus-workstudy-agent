package com.workstudy.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Department {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createTime;
    
    // 非数据库字段，用于展示
    private String managerName;
    private String phone;
}
