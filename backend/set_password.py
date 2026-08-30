import bcrypt

password = "password"
hashed = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt(rounds=10))
print(f"UPDATE workstudy.sys_user SET password = '{hashed.decode()}' WHERE username = 'student1';")
