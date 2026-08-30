package com.workstudy.mapper;

import com.workstudy.entity.StudentProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StudentProfileMapper {

    @Select("SELECT * FROM student_profile WHERE user_id = #{userId}")
    StudentProfile selectByUserId(Long userId);

    @Insert("INSERT INTO student_profile (user_id, major, grade, skill_tags, time_pref, salary_pref, profile_text) " +
            "VALUES (#{userId}, #{major}, #{grade}, #{skillTags}, #{timePref}, #{salaryPref}, #{profileText})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StudentProfile profile);

    @Update("UPDATE student_profile SET major = #{major}, grade = #{grade}, skill_tags = #{skillTags}, " +
            "time_pref = #{timePref}, salary_pref = #{salaryPref}, profile_text = #{profileText} WHERE user_id = #{userId}")
    int update(StudentProfile profile);

    @Delete("DELETE FROM student_profile WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
