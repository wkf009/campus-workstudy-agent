param(
    [string]$Username = "student1",
    [string]$NewPassword = "password"
)

Add-Type -Path "C:\Users\wkf\Desktop\毕业设计\代码\backend\target\classes"

# Use MySQL to update password directly with a known BCrypt hash
$bcryptHash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'

$query = "UPDATE workstudy.sys_user SET password = '$bcryptHash' WHERE username = '$Username'; SELECT username, LENGTH(password) as pwd_len FROM workstudy.sys_user WHERE username = '$Username';"

& mysql -u root -proot -e $query
