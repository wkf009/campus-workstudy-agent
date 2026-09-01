package com.workstudy.mapper;

import com.workstudy.entity.RecommendationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RecommendationLogMapper {

    @Insert("INSERT INTO recommendation_log (user_id, job_id, score, reason, strategies) " +
            "VALUES (#{userId}, #{jobId}, #{score}, #{reason}, #{strategies})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RecommendationLog log);

    @Select("SELECT * FROM recommendation_log WHERE user_id = #{userId} ORDER BY created_at DESC, score DESC")
    List<RecommendationLog> selectByUserId(Long userId);

    @Select("SELECT * FROM recommendation_log ORDER BY id DESC LIMIT 1000")
    List<RecommendationLog> selectAll();
}
