package com.workstudy.mapper;

import com.workstudy.entity.Application;
import com.workstudy.vo.ApplicationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;

import java.util.List;

@Mapper
public interface ApplicationMapper {
    @Select("SELECT * FROM job_application WHERE id = #{id}")
    Application selectById(Long id);

    @Select("SELECT * FROM job_application WHERE job_id = #{jobId}")
    List<Application> selectByJobId(Long jobId);

    @Select("SELECT * FROM job_application WHERE user_id = #{userId}")
    List<Application> selectByUserId(Long userId);

    @Select("SELECT * FROM job_application WHERE user_id = #{studentId}")
    List<Application> selectByStudentId(Long studentId);

    @Select("SELECT * FROM job_application WHERE status = #{status}")
    List<Application> selectByStatus(Integer status);

    @Select("SELECT * FROM job_application")
    List<Application> selectAll();

    /**
     * 查询学生的申请记录（包含岗位名称和学生姓名）
     * 优先使用job_application表中的job_title，为空时从job表获取
     */
    @Select("SELECT a.id, a.job_id AS jobId, IFNULL(a.job_title, IFNULL(j.title, '岗位已删除')) AS jobTitle, " +
            "a.user_id AS userId, u.real_name AS studentName, " +
            "a.status, a.audit_remark AS auditRemark, " +
            "a.apply_time AS applyTime, a.audit_time AS auditTime " +
            "FROM job_application a " +
            "LEFT JOIN job j ON a.job_id = j.id " +
            "LEFT JOIN sys_user u ON a.user_id = u.id " +
            "WHERE a.user_id = #{userId} " +
            "ORDER BY a.apply_time DESC")
    List<ApplicationVO> selectMyApplicationsWithDetails(Long userId);

    /**
     * 查询部门下所有岗位的申请（包含岗位名称和学生姓名）
     * 排除已删除岗位的申请记录
     */
    @Select("SELECT a.id, a.job_id AS jobId, IFNULL(a.job_title, j.title) AS jobTitle, " +
            "a.user_id AS userId, u.real_name AS studentName, " +
            "a.status, a.audit_remark AS auditRemark, " +
            "a.apply_time AS applyTime, a.audit_time AS auditTime " +
            "FROM job_application a " +
            "INNER JOIN job j ON a.job_id = j.id " +
            "LEFT JOIN sys_user u ON a.user_id = u.id " +
            "WHERE j.department_id = #{departmentId} " +
            "ORDER BY a.apply_time DESC")
    List<ApplicationVO> selectApplicationsByDepartment(Long departmentId);

    @Insert("INSERT INTO job_application (job_id, job_title, user_id, resume_url, cover_letter, status, audit_remark, apply_time) VALUES (#{jobId}, #{jobTitle}, #{userId}, #{resumeUrl}, #{coverLetter}, #{status}, #{auditRemark}, #{applyTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Application application);

    @Update("UPDATE job_application SET job_id = #{jobId}, job_title = #{jobTitle}, user_id = #{userId}, resume_url = #{resumeUrl}, cover_letter = #{coverLetter}, status = #{status}, audit_remark = #{auditRemark}, apply_time = #{applyTime}, audit_time = #{auditTime}, auditor_id = #{auditorId}, interview_time = #{interviewTime} WHERE id = #{id}")
    int update(Application application);

    @Delete("DELETE FROM job_application WHERE id = #{id}")
    int deleteById(Long id);
}
