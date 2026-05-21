# User API 명세서

---

## 1. 내 정보 조회
- method : `GET`
- url : `/users/me`

### Header
```http
Authorization: Bearer {accessToken}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "사용자 정보 조회 성공",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "사용자닉네임"
  }
}
```

---

## 2. 내 정보 수정
- 수정 가능 필드: nickname
- method : `PUT`
- url : `/users/me`

### Header
```http
Authorization: Bearer {accessToken}
```

### Request
```json
{
  "nickname": "새닉네임"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "사용자 정보 수정 완료",
  "data": null
}
```

---

## 3. 비밀번호 변경
- method : `PUT`
- url : `/users/me/password`

### Header
```http
Authorization: Bearer {accessToken}
```

### Request
```json
{
  "currentPassword": "OldPassword123!",
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

## 4. 회원 탈퇴
- 회원 탈퇴는 Soft Delete 방식으로 처리한다.
- method : `DELETE`
- url : `/users/me`

### Header
```http
Authorization: Bearer {accessToken}
```

### Request
```json
{
  "password": "Password123!"
}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "회원 탈퇴 완료",
  "data": null
}
```

---

## 공통 실패 응답 예시
```json
{
  "success": false,
  "code": 400,
  "message": "요청 값이 올바르지 않습니다.",
  "error": "ERROR_CODE"
}
```

## 참고 사항
- `/users/me` API는 JWT 인증이 필요하다.
- Authorization 헤더에 Bearer Token을 포함해야 한다.
