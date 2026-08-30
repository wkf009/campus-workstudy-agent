package com.workstudy.service;

import com.workstudy.entity.User;
import com.workstudy.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    public Optional<User> findByUsername(String username) {
        // 从数据库中查询用户
        User user = userMapper.selectByUsername(username);
        return Optional.ofNullable(user);
    }
    
    public User register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(0); // 注册时状态设置为待审批
        userMapper.insert(user);
        return user;
    }
    
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        // 统一使用 BCrypt 校验，禁止明文兜底（明文兜底等于弱口令直通，属高危）
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    public User selectById(Long id) {
        return userMapper.selectById(id);
    }

    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }

    public List<User> getUsersByRole(Integer role) {
        return userMapper.selectByRole(role);
    }

    public List<User> getPendingUsers() {
        return userMapper.selectByStatus(0);
    }

    public void approveUser(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            if (status == 2) {
                // 拒绝时直接删除用户记录
                userMapper.deleteById(userId);
            } else {
                // 通过时更新状态为正常
                user.setStatus(status);
                userMapper.update(user);
            }
        }
    }
    
    public int insert(User user) {
        return userMapper.insert(user);
    }
    
    public int update(User user) {
        return userMapper.update(user);
    }
    
    public int deleteById(Long id) {
        return userMapper.deleteById(id);
    }
}
