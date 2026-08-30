package com.workstudy.service;

import com.workstudy.entity.User;
import com.workstudy.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("123456");
        testUser.setRealName("测试用户");
        testUser.setRole(0);
        testUser.setStatus(0);
    }

    @Test
    void testRegister() {
        when(userMapper.insert(any(User.class))).thenReturn(1);
        User result = userService.register(testUser);
        assertNotNull(result);
        assertEquals(0, result.getStatus());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void testFindByUsername() {
        when(userMapper.selectByUsername("testuser")).thenReturn(testUser);
        var result = userService.findByUsername("testuser");
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
    }

    @Test
    void testFindByUsernameNotFound() {
        when(userMapper.selectByUsername("nouser")).thenReturn(null);
        var result = userService.findByUsername("nouser");
        assertFalse(result.isPresent());
    }

    @Test
    void testCheckPassword() {
        String encoded = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";
        assertTrue(userService.checkPassword("123456", encoded));
    }

    @Test
    void testCheckPasswordWrong() {
        String encoded = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";
        assertFalse(userService.checkPassword("wrong", encoded));
    }

    @Test
    void testGetAllUsers() {
        when(userMapper.selectAll()).thenReturn(java.util.List.of(testUser));
        var users = userService.getAllUsers();
        assertEquals(1, users.size());
    }

    @Test
    void testApproveUser() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        userService.approveUser(1L, 1);
        assertEquals(1, testUser.getStatus());
        verify(userMapper).update(testUser);
    }

    @Test
    void testApproveUserReject() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        userService.approveUser(1L, 2);
        verify(userMapper).deleteById(1L);
    }

    @Test
    void testDeleteById() {
        when(userMapper.deleteById(1L)).thenReturn(1);
        int result = userService.deleteById(1L);
        assertEquals(1, result);
    }
}
