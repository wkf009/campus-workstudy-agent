package com.workstudy.mapper;

import com.workstudy.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobMapper {
    @Select("SELECT * FROM job WHERE id = #{id}")
    Job selectById(Long id);

    @Select("SELECT * FROM job WHERE status = #{status}")
    List<Job> selectByStatus(Integer status);

    @Select("SELECT * FROM job WHERE department_id = #{departmentId}")
    List<Job> selectByDepartment(Long departmentId);

    @Select("SELECT * FROM job WHERE publisher_id = #{publisherId}")
    List<Job> selectByPublisher(Long publisherId);

    @Select("SELECT * FROM job WHERE status = 1 ORDER BY create_time DESC")
    List<Job> selectAllPublished();

    /**
     * 招聘中岗位分页查询：支持关键词搜索（标题/描述）、部门筛选，
     * 按"本部门优先 + 薪资降序 + 时间倒序"排序（SQL 层，配合 PageHelper 分页）。
     */
    @Select("SELECT * FROM job WHERE status = 1 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR title LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%')) " +
            "AND (#{departmentId} IS NULL OR department_id = #{departmentId}) " +
            "ORDER BY CASE WHEN department_id = #{deptId} THEN 0 ELSE 1 END, salary DESC, create_time DESC")
    List<Job> selectPublishedOrdered(@Param("deptId") Long deptId,
                                     @Param("keyword") String keyword,
                                     @Param("departmentId") Long departmentId);

    /** AI 搜索（M5 QueryAgent）：关键词 + 地点过滤，Top 10 */
    @Select("SELECT * FROM job WHERE status = 1 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR title LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR description LIKE CONCAT('%', #{keyword}, '%') OR requirements LIKE CONCAT('%', #{keyword}, '%')) " +
            "AND (#{location} IS NULL OR #{location} = '' OR location LIKE CONCAT('%', #{location}, '%')) " +
            "ORDER BY salary DESC, create_time DESC LIMIT 10")
    List<Job> selectPublishedByFilters(@Param("keyword") String keyword,
                                       @Param("location") String location);

    @Select("SELECT * FROM job ORDER BY create_time DESC")
    List<Job> selectAll();

    @Insert("INSERT INTO job (title, description, requirements, salary, location, quota, work_time, contact_person, contact_phone, department_id, department_name, publisher_id, status, remark) VALUES (#{title}, #{description}, #{requirements}, #{salary}, #{location}, #{quota}, #{workTime}, #{contactPerson}, #{contactPhone}, #{departmentId}, #{departmentName}, #{publisherId}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Job job);

    @Update("UPDATE job SET title = #{title}, description = #{description}, requirements = #{requirements}, salary = #{salary}, location = #{location}, quota = #{quota}, work_time = #{workTime}, contact_person = #{contactPerson}, contact_phone = #{contactPhone}, department_id = #{departmentId}, department_name = #{departmentName}, publisher_id = #{publisherId}, status = #{status}, remark = #{remark} WHERE id = #{id}")
    int update(Job job);

    /** 仅更新状态与备注（Agent 审核采纳/定时任务用，避免全字段覆盖） */
    @Update("UPDATE job SET status = #{status}, remark = #{remark} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("remark") String remark);

    @Delete("DELETE FROM job WHERE id = #{id}")
    int deleteById(Long id);
}