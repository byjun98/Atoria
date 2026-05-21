# File API 명세서

---

## 1. S3 프리사인드 URL 발급

- method : `POST`
- url : `/files/presigned-url`

### Header

```http
Authorization: Bearer {accessToken}
```

### Request

```json
{
  "fileName": "photo.png",
  "contentType": "image/png"
}
```

### Response

```json
{
  "presignedUrl": "https://s3.../photo.png?X-Amz-Signature=...",
  "fileKey": "uploads/uuid-photo.png"
}
```

### 필드 설명

- `presignedUrl`: 클라이언트가 S3에 직접 업로드할 때 사용하는 임시 PUT URL
- `fileKey`: private S3 버킷에서 파일을 식별하기 위해 DB에 저장할 S3 object key

---

## 유의사항

- `presignedUrl`은 업로드용 임시 URL이므로 DB 저장 대상이 아니다.
- private S3 버킷 기준으로 파일 접근 URL 대신 `fileKey`를 저장한다.
- 실제 업로드 요청의 `Content-Type`은 프리사인드 URL 발급 요청의 `contentType`과 동일해야 한다.
