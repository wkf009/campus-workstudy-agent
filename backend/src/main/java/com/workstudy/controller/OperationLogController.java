package com.workstudy.controller;

import com.workstudy.common.Result;
import com.workstudy.entity.OperationLog;
import com.workstudy.mapper.OperationLogMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class OperationLogController {

    private final OperationLogMapper operationLogMapper;

    public OperationLogController(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @GetMapping("/logs")
    public Result<List<OperationLog>> getAllLogs() {
        return Result.success(operationLogMapper.selectAll());
    }

    @GetMapping("/logs/user/{userId}")
    public Result<List<OperationLog>> getLogsByUser(@PathVariable Long userId) {
        return Result.success(operationLogMapper.selectByUserId(userId));
    }
}
