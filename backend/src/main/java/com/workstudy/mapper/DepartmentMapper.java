package com.workstudy.mapper;

import com.workstudy.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface DepartmentMapper {
    @Select("SELECT * FROM department WHERE id = #{id}")
    Department selectById(Long id);

    @Select("SELECT * FROM department ORDER BY id")
    List<Department> selectAll();

    @Insert("INSERT INTO department (name, description) VALUES (#{name}, #{description})")
    int insert(Department department);

    @Update("UPDATE department SET name = #{name}, description = #{description} WHERE id = #{id}")
    int update(Department department);

    @Delete("DELETE FROM department WHERE id = #{id}")
    int deleteById(Long id);
}
