package com.workstudy.service;

import com.workstudy.entity.Notification;
import com.workstudy.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public void sendNotification(Long userId, String title, String content, Integer type, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    public List<Notification> getByUserId(Long userId) {
        return notificationMapper.selectByUserId(userId);
    }

    public List<Notification> getUnreadByUserId(Long userId) {
        return notificationMapper.selectUnreadByUserId(userId);
    }

    public int countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    public void markAsRead(Long id) {
        notificationMapper.markAsRead(id);
    }

    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }
}
