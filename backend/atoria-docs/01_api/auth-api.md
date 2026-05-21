# 🔐 Auth API 명세서

---

## 1. 닉네임 중복 확인
- method : `GET`
- url : `/auth/nickname/exists`

### Request
```
/auth/nickname/exists?nickname=닉네임
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "사용 가능한 닉네임입니다.",
  "data": {
    "available": true
  }
}
```

```json
{
  "success": false,
  "code": 409,
  "message": "이미 사용 중인 닉네임입니다.",
  "error": "NICKNAME_DUPLICATE"
}
```

---

## 2. 이메일 중복 확인
- method : `GET`
- url : `/auth/email/exists`

### Request
```
/auth/email/exists?email=user@example.com
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "사용 가능한 이메일입니다.",
  "data": {
    "available": true
  }
}
```

```json
{
  "success": false,
  "code": 409,
  "message": "이미 사용 중인 이메일입니다.",
  "error": "EMAIL_DUPLICATE"
}
```

---

## 3. 이메일 인증코드 발송
- method : `POST`  
- url : `/auth/email/send-code`  

### Request
```json
{
  "email": "user@example.com"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "인증코드가 발송되었습니다.",
  "data": null
}
```

---

## 4. 이메일 코드 확인
- method : `POST`  
- url : `/auth/email/verify-code`  

### Request
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "이메일 인증 완료",
  "data": {
    "verified": true
  }
}
```

---

## 5. 회원가입
- method : `POST`
- url : `/auth/signup`  

### Request
```json
{
  "nickname": "닉네임",
  "email": "user@example.com",
  "authCode": "123456",
  "password": "Password123!",
  "passwordConfirm": "Password123!"
}
```

### Response
```json
{
  "success": true,
  "code": 201,
  "message": "회원가입 완료",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "닉네임"
  }
}
```

---

## 6. 로그인
- method : `POST`  
- url : `/auth/login`  

### Request
```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "로그인 성공",
  "data": {
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "user": {
      "userId": 1,
      "nickname": "닉네임"
    }
  }
}
```

---

## 7. 로그아웃
- method : `POST`  
- url : `/auth/logout`  

### Request
```json
{
  "refreshToken": "jwt-refresh-token"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "로그아웃 완료",
  "data": null
}
```

---

## 8. 토큰 재발급
- method : `POST`  
- url : `/auth/token/refresh`  

### Request
```json
{
  "refreshToken": "jwt-refresh-token"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "토큰 재발급 완료",
  "data": {
    "accessToken": "new-access-token"
  }
}
```

---

## 9. 비밀번호 재설정 요청
- method : `POST`
- url : `/auth/password/reset/request`  

### Request
```json
{
  "email": "user@example.com"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "비밀번호 재설정 메일 발송",
  "data": null
}
```

---

## 10. 비밀번호 재설정 토큰 검증
- method : `GET`  
- url : `/auth/password/reset/validate`  
- query parameter : `token`

### Request
```
/auth/password/reset/validate?token=reset-token
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "토큰이 유효합니다.",
  "data": {
    "valid": true
  }
}
```

---

## 11. 새 비밀번호 설정
- method : `POST` 
- url : `/auth/password/reset/confirm`  

### Request
```json
{
  "token": "reset-token",
  "newPassword": "NewPassword123!",
  "newPasswordConfirm": "NewPassword123!"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "비밀번호 변경 완료",
  "data": null
}
```

---

## 12. OAuth 로그인 요청
- method : `GET`  
- url : `/oauth2/authorization/{provider}`  
- provider : google | kakao

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "OAuth 로그인 URL 생성 성공",
  "data": {
    "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?..."
  }
}
```

---

## 13. OAuth 콜백
- method : `GET`  
- url : `/oauth2/callback/{provider}`  

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "OAuth 로그인 성공",
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "user": {
      "userId": 1,
      "nickname": "닉네임"
    }
  }
}
```

---

## 🔥 인증 흐름

1. 이메일 중복 확인
2. 인증코드 발송
3. 인증코드 검증
4. 회원가입
5. 로그인 (JWT 발급)
6. API 요청 시 토큰 사용
7. 만료 시 refresh
8. 로그아웃
9. 비밀번호 재설정
10. OAuth 로그인

---

## 정책

- refreshToken은 서버(DB 또는 Redis)에 저장
- 로그아웃 시 refreshToken 무효화
