package com.workstudy.mapper;

import com.workstudy.entity.AgentTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentTaskMapper {

    @Insert("INSERT INTO agent_task (task_type, target_type, target_id, status, input_json, result_json, error_msg, attempt) " +
            "VALUES (#{taskType}, #{targetType}, #{targetId}, #{status}, #{inputJson}, #{resultJson}, #{errorMsg}, #{attempt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentTask task);

    /** 查询同目标同类型最近一条任务（幂等/防呆判断用） */
    @Select("SELECT * FROM agent_task WHERE task_type = #{taskType} AND target_type = #{targetType} AND target_id = #{targetId} " +
            "ORDER BY id DESC LIMIT 1")
    AgentTask selectLatest(@Param("taskType") String taskType,
                           @Param("targetType") String targetType,
                           @Param("targetId") Long targetId);

    @Select("SELECT * FROM agent_task WHERE status = 'PENDING' ORDER BY id ASC LIMIT 100")
    List<AgentTask> selectPending();

    @Update("UPDATE agent_task SET status = #{status}, result_json = #{resultJson}, error_msg = #{errorMsg}, " +
            "finished_at = NOW() WHERE id = #{id}")
    int updateResult(AgentTask task);

    /** 删除某目标某类型的任务（rework 重新提交时清旧预审任务，允许重新预审） */
    @Delete("DELETE FROM agent_task WHERE task_type = #{taskType} AND target_type = #{targetType} AND target_id = #{targetId}")
    int deleteByTarget(@Param("taskType") String taskType,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId);
}
