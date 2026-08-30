package com.workstudy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String realName;
    private Integer role;
    private String phone;
    private String email;
    private String avatar;
    private Integer gender;
    private String studentNo;
    private String major;
    private Long departmentId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
