package com.workstudy.mapper;

import com.workstudy.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    User selectById(Long id);

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    User selectByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE role = #{role}")
    List<User> selectByRole(Integer role);

    @Select("SELECT * FROM sys_user WHERE department_id = #{departmentId}")
    List<User> selectByDepartment(Long departmentId);

    @Select("SELECT * FROM sys_user WHERE status = #{status}")
    List<User> selectByStatus(Integer status);

    @Select("SELECT * FROM sys_user ORDER BY id")
    List<User> selectAll();

    @Insert("INSERT INTO sys_user (username, password, real_name, role, phone, email, avatar, gender, student_no, major, department_id, status) VALUES (#{username}, #{password}, #{realName}, #{role}, #{phone}, #{email}, #{avatar}, #{gender}, #{studentNo}, #{major}, #{departmentId}, #{status})")
    int insert(User user);

    @Update("UPDATE sys_user SET username = #{username}, password = #{password}, real_name = #{realName}, role = #{role}, phone = #{phone}, email = #{email}, avatar = #{avatar}, gender = #{gender}, student_no = #{studentNo}, major = #{major}, department_id = #{departmentId}, status = #{status} WHERE id = #{id}")
    int update(User user);

    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int deleteById(Long id);
}
