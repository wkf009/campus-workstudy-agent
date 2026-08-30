UPDATE workstudy.sys_user SET password = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWGjigy' WHERE username = 'student1';
SELECT username, LENGTH(password) as pwd_len, SUBSTRING(password, 1, 10) as preview FROM workstudy.sys_user WHERE username = 'student1';
