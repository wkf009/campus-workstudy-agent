package com.workstudy.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Notification {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private Integer type;
    private Long relatedId;
    private Boolean isRead;
    private LocalDateTime createTime;
}
