# 🔔 Notification API 명세서

---

## 1. 알림 구독 (SSE / Web Push 등)
- 클라이언트가 서버로부터 실시간 알림을 받기 위한 구독 요청
- method : `GET`
- url : `/notifications/subscribe`

### Header
```
Authorization: Bearer {accessToken}
```

### Response
```
- Content-Type: text/event-stream
- SSE 연결 유지 (스트림 응답)
```

### 예시 이벤트

```
id: 1
event: notification
data: {
    "message": "새로운 미션이 도착했습니다!"
}
```

---

## 2. 알림 목록 조회

* 사용자의 알림 목록을 조회
* method : `GET`
* url : `/notifications`

### Header

```
Authorization: Bearer {accessToken}
```

### Response

```json
{
  "success": true,
  "code": 200,
  "message": "알림 목록 조회 성공",
  "data": [
    {
      "notificationId": "uuid-1234",
      "type": "MISSION",
      "message": "새로운 미션이 도착했습니다!",
      "isRead": false,
      "createdAt": "2026-04-21T12:00:00"
    }
  ]
}
```
- type: MISSION | SYSTEM | EVENT

---

## 3. 알림 읽음 처리

* 특정 알림을 읽음 상태로 변경
* method : `PUT`
* url : `/notifications/{notificationId}/read`

### Header

```
Authorization: Bearer {accessToken}
```

### Response

```json
{
  "success": true,
  "code": 200,
  "message": "알림 읽음 처리 완료",
  "data": null
}
```

---

### 4. 전체 알림 읽음 처리

* method : `PUT`
* url : `/notifications/read-all`

### Response

```json
{
  "success": true,
  "code": 200,
  "message": "모든 알림 읽음 처리 완료",
  "data": null
}
```

---

### 5. 알림 삭제

* method : `DELETE`
* url : `/notifications/{notificationId}`

### Response

```json
{
  "success": true,
  "code": 200,
  "message": "알림 삭제 완료",
  "data": null
}
```

---

## 🔥 유의사항

* 모든 API는 JWT 인증 필요
* SSE 연결은 클라이언트에서 재연결 로직 필요
* 알림 ID는 UUID 사용 권장