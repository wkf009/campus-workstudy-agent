package com.workstudy.mapper;

import com.workstudy.entity.OperationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OperationLogMapper {

    @Insert("INSERT INTO operation_log (user_id, username, operation, method, params, ip, create_time) " +
            "VALUES (#{userId}, #{username}, #{operation}, #{method}, #{params}, #{ip}, #{createTime})")
    int insert(OperationLog log);

    @Select("SELECT * FROM operation_log ORDER BY create_time DESC")
    List<OperationLog> selectAll();

    @Select("SELECT * FROM operation_log WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<OperationLog> selectByUserId(Long userId);
}
