UPDATE workstudy.sys_user SET password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' WHERE username = 'student1';
SELECT username, LENGTH(password) as pwd_len FROM workstudy.sys_user WHERE username = 'student1';
