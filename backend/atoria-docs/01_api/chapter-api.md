# 👤 Course & Mission API 명세서

---

## 1. 사전 코스 목록 조회
- method : `GET`
- url : `/courses`

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "코스 목록 조회 성공",
  "data": [
    {
      "courseId": 1,
      "title": "경주 역사 탐방 코스",
      "description": "첨성대, 불국사, 석굴암을 따라가는 코스",
      "places": [
        { "placeId": 101, "title": "첨성대" },
        { "placeId": 102, "title": "불국사" },
        { "placeId": 103, "title": "석굴암" }
      ]
    }
  ]
}
```

---

## 2. 사전 코스 상세 조회
- method : `GET`
- url : `/courses/{courseId}`

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "코스 상세 조회 성공",
  "data": {
    "courseId": 1,
    "title": "경주 역사 탐방 코스",
    "description": "첨성대, 불국사, 석굴암을 따라가는 코스",
    "places": [
      {
        "placeId": 101,
        "title": "첨성대",
        "latitude": 35.8347,
        "longitude": 128.9769
      },
      {
        "placeId": 102,
        "title": "불국사",
        "latitude": 35.7900,
        "longitude": 129.3300
      },
      {
        "placeId": 103,
        "title": "석굴암",
        "latitude": 35.8240,
        "longitude": 129.3450
      }
    ]
  }
}
```

---

## 3. 장소 목록 조회
- method : `GET`
- url : `/places`

### Query Parameter
- `category` (선택)
- `keyword` (선택)
- `page` (선택)
- `size` (선택)

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "장소 목록 조회 성공",
  "data": [
    {
      "placeId": 101,
      "title": "첨성대",
      "latitude": 35.8347,
      "longitude": 128.9769,
      "thumbnailUrl": "https://cdn-url/thumb.png"
    },
    {
      "placeId": 102,
      "title": "석굴암",
      "latitude": 35.8347,
      "longitude": 128.1234,
      "thumbnailUrl": "https://cdn-url/thumb2.png"
    }
  ],
  "pageInfo": {
    "page": 0,
    "size": 10,
    "totalElements": 100,
    "totalPages": 10
  }
}
```

---

## 4. 장소 상세 조회
- method : `GET`
- url : `/places/{placeId}`

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "장소 상세 조회 성공",
  "data": {
    "placeId": 101,
    "title": "첨성대",
    "latitude": 35.8347,
    "longitude": 128.9769,
    "description": "신라 시대의 천문 관측대",
    "address": "경상북도 경주시 인왕동 839-1",
    "category": "heritage",
    "thumbnailUrl": "https://cdn-url/thumb.png"
  }
}
```

---

## 5. 스토리 생성 요청 (FE → BE)
- method : `POST`
- url : `/stories`

### Header
```
Authorization: Bearer {accessToken}
```

### Request
```json
{
  "courseId": 1,
  "protagonists": [
    {
        "name": "민준",
        "age": 5,
        "tendency": "모험적"
    },
    {
        "name": "성준",
        "age": 6,
        "tendency": "호기심 많음"
    }
    ]
}
```

### Response
```json
{
  "success": true,
  "code": 201,
  "message": "스토리 생성 성공",
  "data": {
    "storyId": 7001,
    "courseId": 1,
    "title": "민준이의 경주 시간여행",
    "status": "IN_PROGRESS",
    "intro": "민준이와 성준이는 경주에서 신비한 시간여행을 시작했다...",
    "chapters": [
      {
        "chapterId": 9001,
        "sequence": 1,
        "placeId": 101,
        "placeTitle": "첨성대",
        "isCompleted": false
      },
      {
        "chapterId": 9002,
        "sequence": 2,
        "placeId": 102,
        "placeTitle": "불국사",
        "isCompleted": false
      }
    ],
    "outro": null,
    "createdAt": "2026-04-23T10:00:00"
  }
}
```

---

## 6. 내 스토리 목록 조회
- method : `GET`
- url : `/stories`

### Header
```
Authorization: Bearer {accessToken}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "스토리 목록 조회 성공",
  "data": [
    {
      "storyId": 7001,
      "courseId": 1,
      "title": "민준이의 경주 시간여행",
      "status": "IN_PROGRESS",
      "thumbnailUrl": "https://cdn-url/thumb.png",
      "completedCount": 2,
      "totalCount": 3,
      "createdAt": "2026-04-23T10:00:00"
    }
  ],
  "pageInfo": {
    "page": 0,
    "size": 10,
    "totalElements": 5,
    "totalPages": 1
  }
}
```

---

## 7. 스토리 상세 조회
- method : `GET`
- url : `/stories/{storyId}`

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "스토리 상세 조회 성공",
  "data": {
    "storyId": 7001,
    "courseId": 1,
    "title": "민준이의 경주 시간여행",
    "status": "IN_PROGRESS",
    "intro": "민준이와 성준이는 경주에서 신비한 시간여행을 시작했다...",
    "outro": null,
    "protagonists": [
      {
        "name": "민준",
        "age": 5,
        "tendency": "모험적"
      }
    ],
    "chapters": [
      {
        "chapterId": 9001,
        "sequence": 1,
        "placeId": 101,
        "placeTitle": "첨성대",
        "storyContent": "Story content for chapter 1.",
        "isCompleted": true
      },
      {
        "chapterId": 9002,
        "sequence": 2,
        "placeId": 102,
        "storyContent": "Story content for chapter 2.",
        "placeTitle": "불국사",
        "isCompleted": false
      }
    ],
    "createdAt": "2026-04-23T10:00:00"
  }
}
```

---

## 8. 챕터 상세 조회
- method : `GET`
- url : `/stories/{storyId}/chapters/{chapterId}`

### Header

```jsx
Authorization: Bearer {accessToken}
```

## Response
```json
{
  "success": true,
  "code": 200,
  "message": "챕터 상세 조회 성공",
  "data": {
    "chapterId": 9001,
    "sequence": 1,
    "isCompleted": false,
    "place": {
      "placeId": 101,
      "title": "첨성대",
      "latitude": 35.83,
      "longitude": 129.22
    },
    "story": {
      "content": null
    },
    "mission": {
      "title": "첨성대 전경 사진",
      "description": "첨성대 전체가 보이도록 촬영",
      "verificationHint": "사진에 보여야 하는 완료 판정 조건",
      "type": "PHOTO",
      "progress": {
        "isCompleted": false,
        "fileUrl": null,
        "locationVerificationStatus": null,
        "completedAt": null
      }
    }
  }
}
```

---

## 9. 스토리 진행률 조회
- method : `GET`
- url : `/stories/{storyId}/progress`

### Header

```jsx
Authorization: Bearer {accessToken}
```

### Response
```json
{
  "success": true,
  "code": 200,
  "message": "스토리 진행률 조회 성공",
  "data": {
    "storyId": 7001,
    "totalCount": 5,
    "completedCount": 3,
    "progressRate": 0.6,
    "chapters": [
      { "chapterId": 9001, "placeTitle": "천마총", "isCompleted": true, "locationVerificationStatus": "CURRENT_GPS" },
      { "chapterId": 9002, "placeTitle": "대릉원", "isCompleted": true, "locationVerificationStatus": "PHOTO_EXIF_PLACE" },
      { "chapterId": 9003, "placeTitle": "석굴암", "isCompleted": true, "locationVerificationStatus": "PHOTO_EXIF_AREA" },
      { "chapterId": 9004, "placeTitle": "불국사", "isCompleted": false, "locationVerificationStatus": null },
      { "chapterId": 9005, "placeTitle": "첨성대", "isCompleted": false, "locationVerificationStatus": null }
    ]
  }
}
```

---

## 10. 미션 결과 제출 (스토리 진행용)
- method : `POST`
- url : `/stories/{storyId}/chapters/{chapterId}/submit`

### Header

```jsx
Authorization: Bearer {accessToken}
```

### Request
```json
{
  "result": {
    "isCompleted": true
  }
}
```

### Response
```json
{
  "success": true,
  "code": 201,
  "message": "미션 결과 제출 완료",
  "data": {
    "storyId": 7001,
    "chapterId": 9001,
    "isCompleted": true,
    "completedAt": "2026-04-24T11:00:00",
    "nextChapterId": 9002
  }
}
```

---

## 11. 미션 사진 제출 (결과물 생성용 / 파일 제출)
- method : `POST`
- url : `/stories/{storyId}/chapters/{chapterId}/files`

### Header

```jsx
Authorization: Bearer {accessToken}
```

### Request
```json
{
  "fileUrl": "https://cdn-url/photo.png",
  "type": "IMAGE",
  "locationVerificationStatus": "PHOTO_EXIF_PLACE"
}
```

### Response
```json
{
  "success": true,
  "code": 201,
  "message": "미션 파일 제출 완료",
  "data": {
    "storyId": 7001,
    "chapterId": 9001,
    "fileUrl": "https://cdn-url/photo.png",
    "type": "IMAGE",
    "locationVerificationStatus": "PHOTO_EXIF_PLACE",
    "uploadedAt": "2026-04-24T11:05:00"
  }
}
```

### locationVerificationStatus

| value | description |
| --- | --- |
| `CURRENT_GPS` | 현재 기기 GPS가 미션 장소 반경 안에 있어 인증됨 |
| `PHOTO_EXIF_PLACE` | 사진 EXIF GPS가 미션 장소 반경 안에 있어 인증됨 |
| `PHOTO_EXIF_AREA` | 위치 권한이 없고 사진 EXIF GPS가 경주 권역 안에 있어 넓은 범위로 인증됨 |
| `UNKNOWN_PLACE` | 장소 좌표가 없어 거리 검증을 할 수 없음 |
| `UNVERIFIED` | GPS/EXIF 위치 인증이 완료되지 않은 임시 저장 상태 |

- `POST /stories/{storyId}/chapters/{chapterId}/files` 요청에서 생략하거나 빈 값이면 서버는 `UNVERIFIED`로 저장합니다.
- `GET /stories/{storyId}/chapters/{chapterId}` 응답의 `mission.progress.locationVerificationStatus`와 `GET /stories/{storyId}/progress` 응답의 `chapters[].locationVerificationStatus`에서도 같은 값을 반환합니다.
