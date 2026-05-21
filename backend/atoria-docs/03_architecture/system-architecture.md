# 📄 Backend System Architecture (Atoria)

본 문서는 Atoria 서비스의 **백엔드 시스템 아키텍처**를 정의합니다.
Docker 기반 인프라 환경에서의 서비스 구조, 데이터 흐름, 비동기 처리 방식을 포함합니다.

---

## 🎯 목표

* AI 기반 콘텐츠 생성 구조 분리
* 비동기 처리 기반 안정성 확보
* 서비스 확장 가능한 구조 설계
* Docker 기반 로컬/배포 환경 일관성 유지

---

## 🧱 전체 백엔드 구조

```mermaid
flowchart LR
    Nginx[Nginx (Gateway)]
    Backend[Backend (Spring Boot)]
    AI[AI Service]
    DB[(PostgreSQL + pgvector)]
    Redis[(Redis)]
    Worker[Async Worker]
    S3[(S3 Storage)]

    Nginx --> Backend
    Nginx --> AI

    Backend --> DB
    Backend --> Redis
    Backend --> AI

    Redis --> Worker
    Worker --> AI
    Worker --> S3
    Worker --> DB
```

---

## ⚙️ 구성 요소

---

### 1. Nginx (Gateway)

* 모든 요청의 진입점
* Reverse Proxy 역할

```plaintext
/api → backend
/ai  → ai service
```

---

### 2. Backend (Spring Boot)

👉 핵심 역할

* REST API 제공
* 인증 / 인가 처리
* 비즈니스 로직 처리
* AI 요청 트리거
* DB 저장

---

### 3. AI Service

👉 역할

* 스토리 생성
* 미션 생성
* E-book 콘텐츠 생성

👉 Backend와 HTTP 통신

---

### 4. Database (PostgreSQL + pgvector)

👉 저장 데이터

* users / stories / chapters
* user_chapter_progress
* ebooks

👉 pgvector 사용 가능 (AI 확장 대비)

---

### 5. Redis (Queue)

👉 역할

* 비동기 작업 큐
* 작업 상태 관리

---

### 6. Worker

👉 역할

* Redis Queue 소비
* AI 호출
* 파일 생성
* DB 업데이트

---

### 7. Storage (S3)

👉 역할

* 이미지 저장
* E-book 저장
* CDN 제공

---

## 🔄 주요 처리 흐름

---

### 1. 스토리 생성 (동기)

```plaintext
Client → Nginx → Backend → AI → DB 저장 → 응답
```

👉 특징

* 한 번에 생성 (스토리 + 챕터 + 미션)
* 빠른 응답 필요

---

### 2. 미션 수행 (동기)

```plaintext
Client → Backend → DB 저장
```

---

### 3. 파일 업로드 (동기)

```plaintext
Client → Backend → S3 → DB 저장
```

---

### 4. E-book 생성 (비동기) ⭐

```plaintext
Client → Backend → Redis Queue → Worker → AI → S3 → DB → 완료
```

---

## ⚡ 동기 vs 비동기 전략

| 작업        | 방식  |
| --------- | --- |
| 스토리 생성    | 동기  |
| 챕터 조회     | 동기  |
| 미션 수행     | 동기  |
| 파일 업로드    | 동기  |
| E-book 생성 | 비동기 |
| 영상 생성     | 비동기 |

---

## 🔐 인증 구조

```plaintext
Client → JWT → Backend
```

* Access Token 기반 인증
* 사용자별 데이터 분리

---

## 📊 데이터 흐름

```plaintext
요청 → Backend → DB 저장 → AI 호출 → 결과 저장 → 응답
```

---

## 🚀 확장 전략

---

### 1. Backend 확장

```plaintext
Backend → 여러 컨테이너로 scale-out
```

---

### 2. Worker 확장

```plaintext
Worker 수 증가 → 병렬 처리
```

---

### 3. Redis 기반 큐 확장

```plaintext
Queue → 작업 분산
```

---

### 4. AI 서비스 분리

```plaintext
AI Service → 별도 서버 운영 가능
```

---

## 🔥 장애 대응 전략

---

### 1. AI 실패

* Retry (재시도)
* fallback 응답

---

### 2. Queue 장애

* DLQ (Dead Letter Queue)
* 로그 기반 복구

---

### 3. DB 장애

* 백업 전략
* read replica 고려

---

## 📌 설계 핵심 포인트

---

### 1. AI와 Backend 분리

```plaintext
Backend ≠ AI
```

---

### 2. 무거운 작업은 Queue

```plaintext
E-book 생성 → 비동기 처리
```

---

### 3. 상태 기반 처리

```plaintext
PROCESSING / COMPLETED / FAILED
```

---

### 4. Docker 기반 환경

```plaintext
nginx / backend / ai / db / redis
```

👉 동일한 환경으로 개발/배포 가능

---

## 💥 최종 요약

```plaintext
1. Backend는 요청 처리와 상태 관리 담당
2. AI는 콘텐츠 생성 담당
3. Redis는 비동기 작업 처리
4. Worker는 실제 작업 수행
5. S3는 파일 저장
```

---

## 🚀 결론

👉 **Atoria Backend는 AI 중심 비동기 처리 기반의 확장형 시스템이다**
