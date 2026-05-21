# 📚 Atoria Backend Docs

AI 기반 문화유산 스토리텔링 서비스 **Atoria**의 백엔드 설계 문서입니다.
본 문서는 API, 데이터베이스, 시스템 아키텍처를 중심으로 구성되어 있으며,
Claude Code를 활용한 개발 및 협업을 목적으로 합니다.

---

## 🚀 Project Overview

Atoria는 사용자가 문화유산을 탐방하며 미션을 수행하고,
그 여정을 AI가 하나의 스토리로 생성하여 E-book 형태로 제공하는 서비스입니다.

👉 핵심 흐름

```
User → Course 선택 → Story 생성(AI) → Chapter(스토리+미션) → 수행 → 기록 → E-book 생성
```

---

## 📂 Directory Structure

```
atoria-docs/
│
├── 00_overview/
│   └── service-overview.md        # 서비스 개요
│
├── 01_api/
│   ├── auth-api.md               # 인증 (로그인, 회원가입, 토큰)
│   ├── course-api.md             # 코스 & 장소 조회
│   ├── chapter-api.md            # 스토리/챕터/미션 관련 API
│   ├── file-api.md               # 파일 업로드 및 결과물 생성
│   ├── notification-api.md       # 알림 API
│   └── user-api.md               # 사용자 정보 API
│
├── 02_database/
│   ├── erd.md                    # ERD 설명
│   ├── schema.sql                # DB 스키마
│   ├── indexing.md               # 인덱스 전략
│   └── assets/
│       └── erd.png               # ERD 이미지
│
├── 03_architecture/
│   ├── system-architecture.md    # 전체 시스템 구조
│   ├── ai-flow.md                # AI 생성 흐름
│   └── async-processing.md       # 비동기 처리 구조
│
├── 99_conventions/
│   └── backend-conventions.md    # 규칙 및 표준 정의
│
└── README.md
```

---

## 🧱 Architecture Overview

* **Backend**: Spring Boot (예정)
* **Database**: PostgreSQL (JSONB 활용)
* **Storage**: AWS S3 (파일 저장)
* **AI**: 스토리 및 미션 생성 (LLM 기반)
* **Processing**: 일부 비동기 처리 (E-book 생성 등)

---

## 🔑 Key Concepts

### 1. Story

* 사용자가 생성하는 하나의 여행 단위
* AI가 intro / outro 생성

### 2. Chapter

* 각 장소 단위
* **스토리 + 미션이 결합된 핵심 엔티티**

### 3. User Progress

* 유저의 미션 수행 결과 저장
* 선택, 텍스트, 이미지 등 포함

### 4. E-book

* 최종 결과물
* 사용자의 전체 여정을 기반으로 생성

---

## 📡 API Convention

### Response Format

```json
{
  "success": true,
  "code": 200,
  "message": "message",
  "data": {}
}
```

---

## 🧾 Naming Convention

| 영역       | 규칙         |
| -------- | ---------- |
| DB 컬럼    | snake_case |
| API JSON | camelCase  |
| 파일명      | kebab-case |

---

## 🔠 ENUM Definitions

### Mission Type

```
PHOTO | CHOICE | QUIZ | ACTION
```

### Notification Type

```
MISSION | SYSTEM | EVENT
```

---

## 🤖 AI Flow (요약)

1. 사용자가 스토리 생성 요청
2. 백엔드 → AI 요청
3. AI 응답:

   * intro
   * chapters (스토리 + 미션)
   * outro
4. DB 저장
5. 사용자 진행 데이터 누적
6. E-book 생성

---

## ⚙️ Development Guide

### 1. 문서 기반 개발

* 모든 기능은 **API 명세서 기준으로 구현**
* DB는 ERD 기준으로 설계

---

### 2. Claude Code 사용 시

* `01_api` → Controller 생성 기준
* `02_database` → Entity 설계 기준
* `03_architecture` → Service 로직 참고

---

### 3. 우선 구현 순서

1. 인증 (Auth)
2. 코스 / 장소 조회
3. 스토리 생성
4. 챕터 조회
5. 미션 수행
6. 파일 업로드
7. E-book 생성

---

## 🎯 Goal

* 문화유산 관광을 **참여형 경험**으로 전환
* 사용자 행동 기반 **개인화 스토리 생성**
* 단순 소비 → **기억으로 남는 콘텐츠 제공**

---

## 👨‍💻 Maintainer

Backend Developer: 한지원
