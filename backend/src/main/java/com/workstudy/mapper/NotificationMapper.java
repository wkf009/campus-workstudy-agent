package com.workstudy.mapper;

import com.workstudy.entity.Notification;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notification (user_id, title, content, type, related_id, is_read, create_time) " +
            "VALUES (#{userId}, #{title}, #{content}, #{type}, #{relatedId}, #{isRead}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification notification);

    @Select("SELECT * FROM notification WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Notification> selectByUserId(Long userId);

    @Select("SELECT * FROM notification WHERE user_id = #{userId} AND is_read = 0 ORDER BY create_time DESC")
    List<Notification> selectUnreadByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND is_read = 0")
    int countUnread(Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE id = #{id}")
    int markAsRead(Long id);

    @Update("UPDATE notification SET is_read = 1 WHERE user_id = #{userId}")
    int markAllAsRead(Long userId);
}
