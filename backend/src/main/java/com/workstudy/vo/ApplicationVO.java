package com.workstudy.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 申请记录视图对象（包含关联的岗位和学生信息）
 */
@Data
public class ApplicationVO {
    private Long id;                    // 申请ID
    private Long jobId;                 // 岗位ID
    private String jobTitle;            // 岗位标题
    private Long userId;                // 申请人ID
    private String studentName;         // 学生姓名
    private Integer status;             // 申请状态: 0-待审, 1-通过, 2-拒绝
    private String auditRemark;         // 审核备注
    private LocalDateTime applyTime;    // 申请时间
    private LocalDateTime auditTime;    // 审核时间
}