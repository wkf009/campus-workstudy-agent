package com.workstudy.controller;

import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.AccessDeniedException;
import com.workstudy.common.Result;
import com.workstudy.entity.Department;
import com.workstudy.entity.User;
import com.workstudy.service.DepartmentService;
import com.workstudy.service.UserService;
import com.workstudy.utils.JwtUtils;
import com.workstudy.vo.DepartmentVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final UserService userService;

    public DepartmentController(DepartmentService departmentService, UserService userService) {
        this.departmentService = departmentService;
        this.userService = userService;
    }

    @GetMapping("/departments")
    public Result<List<Department>> getAllDepartments() {
        return Result.success(departmentService.getAllDepartments());
    }

    @GetMapping("/departments/{id}")
    public Result<DepartmentVO> getDepartmentById(@PathVariable Long id, HttpServletRequest request) {
        Department dept = departmentService.getDepartmentById(id);
        if (dept == null) {
            return Result.error("部门不存在");
        }
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        DepartmentVO vo = new DepartmentVO();
        vo.setId(dept.getId());
        vo.setName(dept.getName());
        vo.setDescription(dept.getDescription());
        vo.setCreateTime(dept.getCreateTime());
        if (currentUser != null) {
            vo.setManagerId(currentUser.getId());
            vo.setManagerName(currentUser.getRealName() != null ? currentUser.getRealName() : currentUser.getUsername());
            vo.setPhone(currentUser.getPhone());
        }
        return Result.success(vo);
    }

    @LogOperation
    @RequireRole({3})
    @PostMapping("/departments")
    public Result<Department> createDepartment(@RequestBody Department department) {
        departmentService.insert(department);
        return Result.success("创建成功", department);
    }

    /**
     * 更新部门：超管可改任意部门；部门管理员只能改本部门。
     */
    @LogOperation
    @RequireRole({2, 3})
    @PutMapping("/departments/{id}")
    public Result<Department> updateDepartment(@PathVariable Long id, @RequestBody Department department, HttpServletRequest request) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        if (currentUser != null && currentUser.getRole() == 2 && !id.equals(currentUser.getDepartmentId())) {
            throw new AccessDeniedException("只能修改本部门信息");
        }
        department.setId(id);
        int result = departmentService.update(department);
        if (result > 0) {
            return Result.success("更新成功", departmentService.getDepartmentById(id));
        }
        return Result.error("更新失败");
    }

    @LogOperation
    @PutMapping("/departments/{id}/manager")
    public Result<User> updateDepartmentManager(@PathVariable Long id, HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        if (currentUser == null) {
            return Result.error("用户不存在");
        }
        if (body.containsKey("managerName")) {
            currentUser.setRealName((String) body.get("managerName"));
        }
        if (body.containsKey("phone")) {
            currentUser.setPhone((String) body.get("phone"));
        }
        userService.update(currentUser);
        return Result.success("更新成功", currentUser);
    }

    @LogOperation
    @RequireRole({3})
    @DeleteMapping("/departments/{id}")
    public Result<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteById(id);
        return Result.success();
    }
}
