# Ebook API 명세서

---

## 1. 결과물 생성 요청

- 사용자가 코스 기반 스토리를 E-book 결과물로 생성 요청합니다.
- 실제 AI 생성 로직은 추후 연동하며, 현재 API는 생성 요청을 접수하고 `PROCESSING` 상태로 저장합니다.
- method : `POST`
- url : `/ebooks`

### Header

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### Request

```json
{
  "courseId": 1001,
  "storyId": 7004,
  "title": "테스트유저의 경주 시간여행 E-book",
  "options": {
    "style": "동화",
    "language": "ko"
  }
}
```

### Response

```json
{
  "success": true,
  "code": 202,
  "message": "결과물 생성 요청 완료",
  "data": {
    "ebookId": "083959f8-affe-4dde-8583-39f03fb33b9c",
    "status": "PROCESSING"
  }
}
```

---

## 2. 결과물 목록 조회

- 로그인한 사용자의 E-book 결과물 목록을 조회합니다.
- method : `GET`
- url : `/ebooks`

### Header

```http
Authorization: Bearer {accessToken}
```

### Query Parameters

| Name | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| page | number | N | 0 | 페이지 번호 |
| size | number | N | 10 | 페이지 크기 |

### Response

```json
{
  "success": true,
  "code": 200,
  "message": "결과물 목록 조회 성공",
  "data": [
    {
      "ebookId": "083959f8-affe-4dde-8583-39f03fb33b9c",
      "title": "테스트유저의 경주 시간여행 E-book",
      "fileKey": "ebooks/uuid-file.pdf",
      "thumbnailKey": "ebooks/uuid-thumb.png",
      "status": "PROCESSING",
      "createdAt": "2026-05-04T16:44:02.970788"
    }
  ],
  "pageInfo": {
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 3. 결과물 상세 조회

- 특정 E-book 결과물의 상세 정보를 조회합니다.
- method : `GET`
- url : `/ebooks/{ebookId}`

### Header

```http
Authorization: Bearer {accessToken}
```

### Response

```json
{
  "success": true,
  "code": 200,
  "message": "결과물 상세 조회 성공",
  "data": {
    "ebookId": "083959f8-affe-4dde-8583-39f03fb33b9c",
    "title": "테스트유저의 경주 시간여행 E-book",
    "fileKey": "ebooks/uuid-file.pdf",
    "thumbnailKey": "ebooks/uuid-thumb.png",
    "status": "COMPLETED",
    "createdAt": "2026-05-04T16:44:02.970788",
    "metadata": {
      "pageCount": 20
    }
  }
}
```

---

## 상태값

```text
PROCESSING : 생성 중
COMPLETED  : 생성 완료
FAILED     : 생성 실패
```

---

## 참고사항

- 결과물 생성 요청 API는 생성 작업을 즉시 완료하지 않고 `PROCESSING` 상태로 저장합니다.
- AI 생성 및 S3 업로드가 완료되면 추후 `fileKey`, `thumbnailKey`, `metadata`, `status`를 업데이트합니다.
- private S3 버킷 사용을 기준으로 조회 API는 공개 URL 대신 `fileKey`, `thumbnailKey`를 반환합니다.
