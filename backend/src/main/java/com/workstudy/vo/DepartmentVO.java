package com.workstudy.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 部门信息视图对象（包含关联的部门负责人信息）
 */
@Data
public class DepartmentVO {
    private Long id;                // 部门ID
    private String name;            // 部门名称
    private String description;     // 部门描述
    private LocalDateTime createTime;
    private Long managerId;         // 负责人ID（当前用户）
    private String managerName;     // 负责人姓名
    private String phone;           // 联系电话
}
