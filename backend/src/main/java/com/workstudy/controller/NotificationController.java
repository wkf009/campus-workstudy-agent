package com.workstudy.controller;

import com.workstudy.common.Result;
import com.workstudy.entity.Notification;
import com.workstudy.service.NotificationService;
import com.workstudy.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public Result<List<Notification>> getNotifications(HttpServletRequest request) {
        Long userId = JwtUtils.getUserIdFromRequest(request);
        return Result.success(notificationService.getByUserId(userId));
    }

    @GetMapping("/notifications/unread")
    public Result<List<Notification>> getUnreadNotifications(HttpServletRequest request) {
        Long userId = JwtUtils.getUserIdFromRequest(request);
        return Result.success(notificationService.getUnreadByUserId(userId));
    }

    @GetMapping("/notifications/unread/count")
    public Result<Integer> getUnreadCount(HttpServletRequest request) {
        Long userId = JwtUtils.getUserIdFromRequest(request);
        return Result.success(notificationService.countUnread(userId));
    }

    @PutMapping("/notifications/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success();
    }

    @PutMapping("/notifications/read-all")
    public Result<Void> markAllAsRead(HttpServletRequest request) {
        Long userId = JwtUtils.getUserIdFromRequest(request);
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}
