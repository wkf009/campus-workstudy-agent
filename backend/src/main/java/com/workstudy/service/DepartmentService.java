package com.workstudy.service;

import com.workstudy.entity.Department;
import com.workstudy.mapper.DepartmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {
    @Autowired
    private DepartmentMapper departmentMapper;

    public Department selectById(Long id) {
        return departmentMapper.selectById(id);
    }

    public Department getDepartmentById(Long id) {
        return departmentMapper.selectById(id);
    }

    public List<Department> getAllDepartments() {
        return departmentMapper.selectAll();
    }

    public int insert(Department department) {
        return departmentMapper.insert(department);
    }

    @Transactional
    public int update(Department department) {
        System.out.println("更新部门信息: id=" + department.getId() + ", name=" + department.getName() + ", description=" + department.getDescription());
        int result = departmentMapper.update(department);
        System.out.println("更新结果: " + result);
        return result;
    }

    @Transactional
    public int deleteById(Long id) {
        return departmentMapper.deleteById(id);
    }
}
