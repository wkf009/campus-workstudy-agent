package com.workstudy.mapper;

import com.workstudy.entity.AuditReport;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditReportMapper {

    @Select("SELECT * FROM audit_report WHERE target_type = #{targetType} AND target_id = #{targetId}")
    AuditReport selectByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Insert("INSERT INTO audit_report (target_type, target_id, suggestion, score, reasons, suggestions, risk_flags, agent_model) " +
            "VALUES (#{targetType}, #{targetId}, #{suggestion}, #{score}, #{reasons}, #{suggestions}, #{riskFlags}, #{agentModel})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditReport report);

    @Update("UPDATE audit_report SET suggestion = #{suggestion}, score = #{score}, reasons = #{reasons}, " +
            "suggestions = #{suggestions}, risk_flags = #{riskFlags}, agent_model = #{agentModel} " +
            "WHERE target_type = #{targetType} AND target_id = #{targetId}")
    int updateByTarget(AuditReport report);
}
