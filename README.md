# ATORIA

> AI 스토리, 현장 미션, 지도 기반 동선, Unity 게임을 결합한 경주 문화유산 체험형 모바일 서비스

- 서비스명: ATORIA
- 프로젝트 기간: 2026.04 ~ 2026.05
- 개발 인원: 6명

<p align="center">
  <img src="docs/readme-assets/main.png" alt="ATORIA 대표 이미지" width="80%">
</p>

Android + Unity 기반 경주 문화유산 체험형 모바일 서비스입니다. 사용자의 인원과 성향에 맞춰 AI가 탐방 전용 이야기와 장소별 미션을 생성하고, Kakao Map 기반 동선과 CameraX 현장 미션, 로컬 ONNX 사진 검증, Unity 게임 콘텐츠, e-book 기록까지 한 흐름으로 묶은 체험형 안드로이드 앱을 목표로 했습니다.

> 저는 이 프로젝트에서 **프론트엔드(Android 앱)**와 **게임(Unity 콘텐츠/Android 연동)**을 담당했습니다.  
> 화면 전환, 지도, 코스 선택, 미션 카메라, 로컬 이미지 판별, 이동 경로 기록, e-book 뷰어, Unity 모듈 연동까지 사용자가 직접 만지는 영역을 중심으로 구현했습니다.

---

## 목차

- [기획 배경](#기획-배경)
- [서비스 주요 기능](#서비스-주요-기능)
- [시연 영상](#시연-영상)
- [주요 화면 및 기능 소개](#주요-화면-및-기능-소개)
- [프로젝트 핵심 기술](#프로젝트-핵심-기술)
- [개인 기여사항](#개인-기여사항)
- [시스템 아키텍처](#시스템-아키텍처)
- [프로젝트 구조](#프로젝트-구조)
- [팀원 소개](#팀원-소개)
- [기술 스택](#기술-스택)
- [실행 방법](#실행-방법)

---

## 기획 배경

### 기존 문화유산 여행 앱에서 느낀 한계

- 장소 정보, 지도, 후기 중심의 평면적인 구성이 많아 몰입할 거리가 부족했습니다.
- 어린 사용자나 가족 단위 방문자에게는 역사 설명만으로 흥미를 끌기 어려웠습니다.
- 실제 장소에 도착해도 "무엇을 보고, 어떤 행동을 해야 하는지"가 분명하지 않았습니다.
- 탐방이 끝나면 사진과 기억이 흩어져, 다시 꺼내볼 수 있는 결과물이 남지 않았습니다.

### ATORIA가 제안한 방향

문화유산 방문 자체가 하나의 이야기와 미션이 되도록 설계했습니다.

- 인원과 성향에 맞춰 AI가 탐방 전용 스토리와 장소별 미션을 생성
- 지도 기반 동선과 현장 미션을 결합해 방문 자체를 게임처럼 만든 흐름
- CameraX 촬영 결과를 로컬 ONNX 모델로 판별해 현장에서 미션 수행 여부를 즉시 확인
- 탐방 종료 후 스토리·미션 결과·사진을 엮은 동화책 형태의 e-book 결과물 제공
- 앱 안에서 실행되는 Unity 게임 콘텐츠로 탐방 사이의 몰입형 휴식 경험 제공

---

## 서비스 주요 기능

### 코스 및 스토리 생성

- 탐방 인원·연령·성향을 입력 받아 AI가 사용자 맞춤 스토리/미션을 생성
- 추천 코스 + 거리 기반 후보를 함께 보여주고, 선택 바구니로 코스 구성
- 선택한 장소 순서를 사용자가 직접 조정하고 경로를 재계산

### 지도 및 동선

- Kakao Map 기반 핀/경로 표시, 주변 문화유산 검색, 커스텀 마커
- 현재 위치/EXIF 좌표로 현장성 검증
- 탐방 모드에 따라 foreground only / foreground service 분기 동선 기록

### 현장 미션

- 장소별 사진 촬영형, 선택형, 퀴즈형 미션 제공
- CameraX 촬영 + EXIF/GPS 위치 검증
- 로컬 ONNX 모델로 문화유산 분류, 미션 장소명과 top-k 매칭 후 제출

### e-book 결과물

- AI 서버에서 받은 e-book 구조(페이지·본문·이미지 힌트·캡션)를 Compose 책 페이지로 렌더링
- 원격 PDF가 있을 경우 다운로드/캐시, 전체화면 읽기, PDF 저장/공유까지 연결

### Unity 게임 콘텐츠

- Android 앱 안에 Unity Library를 모듈로 포함, 탐방 흐름 안에서 게임 진입
- `UnityPlayerGameActivity` 기반 fullscreen landscape 실행
- Compose 앱과 단일 APK로 빌드, 게임 종료 후 Compose 화면 복귀

---

## 시연 영상

> 영상 업로드 후 아래 `YOUTUBE_VIDEO_ID`를 실제 ID로 교체해 주세요.

<p align="center">
  <a href="https://youtu.be/YOUTUBE_VIDEO_ID">
    <img src="https://img.youtube.com/vi/YOUTUBE_VIDEO_ID/maxresdefault.jpg" alt="ATORIA 시연 영상" width="80%">
  </a>
</p>

---

## 주요 화면 및 기능 소개

### 1. 온보딩 — 로그인 / 권한 / 인원·성향

| 로그인                                                     | 인원·성향 설문                                                |
|:--------------------------------------------------------:|:---------------------------------------------------------:|
| <img src="docs/readme-assets/login.png" width="420">     | <img src="docs/readme-assets/survey.png" width="420">     |

- OAuth 딥링크 처리, 토큰 저장/복원, 자동 로그인 흐름을 단일 진입점에서 처리합니다.
- 권한 온보딩 화면에서 위치·카메라·알림 권한을 일괄 안내합니다.
- 탐방 인원/연령/성향 설문을 받아 AI 스토리 생성에 전달합니다.

### 2. 코스 선택 및 순서 조정

| 코스 선택                                                     | 코스 순서 조정                                                    |
|:------------------------------------------------------------:|:-------------------------------------------------------------:|
| <img src="docs/readme-assets/course-select.png" width="420"> | <img src="docs/readme-assets/course-order.png" width="420">   |

- 추천/거리 기반 후보 그룹을 카드 + Kakao Map 핀으로 동시에 보여줍니다.
- 선택 바구니, 지도 경로, 줌 컨트롤이 단일 상태로 동기화됩니다.
- 선택한 장소 순서를 자유롭게 변경하고 경로가 즉시 재계산됩니다.

### 3. 지도 및 주변 문화유산

| 지도 / 주변 검색                                          | 장소 상세 모달                                                |
|:--------------------------------------------------------:|:------------------------------------------------------------:|
| <img src="docs/readme-assets/map.png" width="420">       | <img src="docs/readme-assets/place-detail.png" width="420">   |

- Kakao Map 기반 커스텀 마커, 선택 카드, 상세 모달을 일관된 디자인으로 구성했습니다.
- 위치 권한이 없거나 Kakao Directions 응답이 없을 때는 Compose Canvas fallback map으로 화면을 유지합니다.

### 4. 미션 / 미션 카메라

| 미션 상세                                                     | 미션 카메라                                                    |
|:-------------------------------------------------------------:|:-------------------------------------------------------------:|
| <img src="docs/readme-assets/quest-detail.png" width="420">   | <img src="docs/readme-assets/quest-camera.png" width="420">   |

- 장소별 스토리·미션·진행 상태·제출 상태를 한 화면에 모았습니다.
- 사진 미션은 CameraX 촬영 → EXIF/GPS 검증 → ONNX 분류 → top-k 매칭 → Presigned URL 업로드 순으로 진행됩니다.

### 5. e-book 뷰어

| e-book 뷰어                                                  | 전체화면 / PDF 저장                                              |
|:------------------------------------------------------------:|:--------------------------------------------------------------:|
| <img src="docs/readme-assets/ebook-viewer.png" width="420">  | <img src="docs/readme-assets/ebook-full.png" width="420">      |

- AI 서버가 반환한 e-book 구조를 Compose 책 페이지로 렌더링합니다.
- 원격 PDF 다운로드/캐시, 전체화면 읽기 모드, PDF 저장/공유를 한 흐름으로 연결했습니다.

### 6. Unity 게임 콘텐츠

| Unity 게임 인트로                                            | 인게임 플레이                                                  |
|:------------------------------------------------------------:|:-------------------------------------------------------------:|
| <img src="docs/readme-assets/unity-game.png" width="420">    | <img src="docs/readme-assets/unity-play.png" width="420">     |

- Compose 화면에서 게임 진입 시 `UnityPlayerGameActivity`가 fullscreen landscape로 실행됩니다.
- 게임 종료 후 Compose 화면의 시스템 바·인셋·테마·orientation이 복원됩니다.

### 7. 결과 / 갤러리

| 탐방 결과                                                     | 갤러리                                                        |
|:------------------------------------------------------------:|:------------------------------------------------------------:|
| <img src="docs/readme-assets/result.png" width="420">         | <img src="docs/readme-assets/gallery.png" width="420">       |

- 미션 결과, 이동 경로, 촬영 사진을 결과 화면과 갤러리에서 다시 볼 수 있습니다.

---

## 프로젝트 핵심 기술

### Jetpack Compose 기반 단일 진입 흐름

- `MainActivity` → `CultureApp` → `CultureNavGraph`로 이어지는 단일 진입점에서 인증, 권한, 코스, 퀘스트, e-book, 게임 모든 화면을 연결했습니다.
- `culture://oauth` 딥링크를 받아 `CultureAppViewModel`에서 토큰 복원과 시작 화면 분기를 처리합니다.
- DataStore 기반 토큰 저장/복원, refresh 흐름, 개발용 mock 접근 제어를 한 곳에서 관리합니다.

### Compose ↔ 명령형 SDK 브리징

- Kakao Map SDK의 명령형 API(`addMarker`, `moveCamera`)와 Compose 선언형 상태를 단일 상태로 동기화했습니다.
- 선택 바구니·지도 핀·경로를 같은 데이터 소스로 묶어 코스 선택과 순서 조정 화면에서 동일하게 동작하게 했습니다.
- Kakao Directions가 실패해도 Compose Canvas 기반 fallback map으로 화면을 유지합니다.

### CameraX + 로컬 ONNX 현장 검증 파이프라인

- CameraX 촬영, MediaStore 저장, EXIF/GPS 위치 검증, ONNX 모델 추론, 미션 매칭, Presigned URL 업로드를 한 흐름으로 묶었습니다.
- 서버 호출 전 앱 내부에서 분류 결과로 통과 여부를 판단해 잘못된 사진의 업로드를 차단했습니다.
- EXIF·GPS와 분류 결과를 이중 검증해 단순 위변조 시도를 막았습니다.

### 위치 기반 동선 기록

- foreground only / foreground service 두 모드를 분기해 권한 비용과 배터리 사용량을 조절했습니다.
- Android 14+ foreground service location type을 명시하고, 1분 / 5m 단위로 좌표 샘플링을 제한했습니다.
- 탐방 종료 후 좌표 시퀀스를 백엔드에 전송해 결과 화면과 e-book에 활용했습니다.

### e-book 구조 → Compose 책 페이지 렌더링

- AI 서버가 반환한 e-book 구조(페이지·본문·이미지 힌트·캡션)를 Compose 책 페이지로 직접 렌더링했습니다.
- 원격 PDF가 있을 경우 다운로드/캐시, 없을 경우 본문을 페이지 단위로 분할 처리했습니다.
- 전체화면 모드, PDF 저장, 공유 시트를 한 흐름으로 연결했습니다.

### Unity 모듈 통합 빌드

- Unity Android export 산출물(`unityLibrary`)을 Android Gradle 멀티모듈로 포함했습니다.
- `implementation(project(":unityLibrary"))`로 Compose 앱과 Unity fullscreen activity가 같은 APK에서 실행되게 했습니다.
- `arm64-v8a` 단일 ABI, IL2CPP 산출물 포함, `jniLibs.useLegacyPackaging = true`로 디바이스 호환성을 확보했습니다.
- Unity NDK 경로를 `local.properties` → 환경 변수 → Android SDK 순으로 탐색해 팀원별 빌드 환경 차이를 흡수했습니다.

---

## 개인 기여사항

Android 프론트엔드 전반과 Unity 모듈 Android 연동을 담당했습니다.

### 기여한 시스템과 해결한 문제

**앱 시작 흐름과 인증 복원**

- `MainActivity` 딥링크 처리, `CultureAppViewModel` 토큰 복원, 자동 로그인 분기를 단일 진입점으로 묶었습니다.
- 자동 로그인 성공/실패, refresh 만료, mock 접근 등 분기마다 시작 화면이 명확하게 결정되도록 했습니다.
- 화면 전환 애니메이션과 시스템 바·인셋 처리를 공통화해 모든 화면에서 일관된 진입 경험을 만들었습니다.

**코스 선택과 지도 UX**

- 추천/거리 기반 후보 그룹, 선택 바구니, Kakao Map 핀/경로 표시를 단일 상태로 동기화했습니다.
- Kakao Map SDK의 명령형 API를 Compose 상태와 연결해 카드 선택과 지도 표시가 어긋나지 않게 했습니다.
- Kakao Directions 응답이 없을 때 Compose Canvas fallback map을 띄워 빈 화면 없이 흐름을 유지했습니다.

**미션 카메라와 현장 검증**

- CameraX 촬영, 카운트다운 UI, 사진 저장, EXIF 위치 검증, S3 업로드, 미션 제출을 하나의 흐름으로 정리했습니다.
- 로컬 ONNX 모델(`HeritageClassifier`)과 미션 매칭기(`HeritageMissionMatcher`)를 연결해 사진의 장소 일치 여부를 앱 내부에서 즉시 판별했습니다.
- 잘못된 사진이 서버로 올라가기 전 차단해 S3 비용과 백엔드 부하를 줄였습니다.

**이동 경로 기록**

- foreground only와 foreground service 두 모드를 모두 지원하는 `RouteTracker`, `RouteTrackingService`, `RouteHistoryStore`를 구현했습니다.
- Android 14+ foreground service location type을 명시하고 1분 / 5m 단위로 좌표 샘플링을 제한했습니다.
- 배터리 사용량과 동선 기록 밀도의 균형을 잡았습니다.

**e-book 뷰어**

- AI 서버 e-book 구조를 Compose 책 페이지로 렌더링하는 `StoryBookViewerScreen`을 구현했습니다.
- 원격 PDF 다운로드/캐시(`EbookFileRepository`), 본문 페이지 분할, 전체화면 모드, PDF 저장/공유를 연결했습니다.
- 긴 본문이 한 페이지에 몰리지 않도록 페이지 단위 분할 로직을 직접 구성했습니다.

**Unity 모듈 Android 연동**

- Android Gradle 멀티모듈에 `unityLibrary`를 결합하고, Compose 앱과 같은 APK에서 빌드되게 했습니다.
- `UnityPlayerGameActivity`를 fullscreen landscape로 실행하고, notch / safe area / GameActivity 설정을 분리했습니다.
- `arm64-v8a` 단일 ABI, IL2CPP 산출물, legacy JNI packaging, Unity NDK 다중 경로 탐색 등 빌드 안정화 작업을 수행했습니다.
- Compose ↔ Unity 화면 전환 시 시스템 바·인셋·테마·orientation이 복원되도록 처리했습니다.

**UI 시스템과 공통 컴포넌트**

- 문화유산 분위기에 맞춘 Compose 테마, 공통 컴포넌트, 모달, 바텀시트, 로딩 화면 패턴을 정리했습니다.
- 미션·지도·게임·e-book 등 모드별 시스템 바 색상과 인셋을 일관되게 제어했습니다.

### 관련 코드

```text
frontend/app/src/main/kotlin/com/ssafy/culture/
  MainActivity.kt
  ui/CultureApp.kt
  ui/navigation/CultureNavGraph.kt
  ui/screen/course/CourseSelectScreen.kt
  ui/screen/course/CourseOrderScreen.kt
  ui/screen/camera/QuestCameraScreen.kt
  ui/screen/story/StoryBookViewerScreen.kt
  data/ml/HeritageClassifier.kt
  data/ml/HeritageMissionMatcher.kt
  data/route/RouteTracker.kt
  data/route/RouteTrackingService.kt
  data/route/RouteHistoryStore.kt
  data/ebook/EbookFileRepository.kt
  data/ebook/FairyTaleImageRepository.kt
  data/repository/KakaoDirectionsRepository.kt
  data/repository/KakaoLocalRepository.kt

frontend/
  settings.gradle.kts
  app/build.gradle.kts
  unityLibrary/build.gradle
  unityLibrary/src/main/AndroidManifest.xml
  unityLibrary/src/main/java/com/unity3d/player/UnityPlayerGameActivity.java
```

---

## 시스템 아키텍처

<details>
<summary>시스템 아키텍처 보기</summary>

```text
┌─────────────────────────────────────────────────────────────────┐
│                Android App (Kotlin · Jetpack Compose)           │
│                                                                 │
│  ┌────────────┐  ┌────────────┐  ┌──────────────────────────┐   │
│  │  UI / Nav  │  │  Map / UX  │  │   CameraX + Local ML     │   │
│  │ • Compose  │  │ • Kakao    │  │ • CameraX                │   │
│  │ • NavGraph │  │   Map SDK  │  │ • EXIF / GPS             │   │
│  │ • Hilt     │  │ • Local /  │  │ • ONNX Heritage Classify │   │
│  │ • Theme    │  │   Direct   │  │ • Mission Matcher        │   │
│  └────────────┘  └────────────┘  └──────────────────────────┘   │
│                                                                 │
│  ┌────────────┐  ┌────────────┐  ┌──────────────────────────┐   │
│  │ Route Track│  │ E-book View│  │  Unity Library Module    │   │
│  │ • FG svc   │  │ • PDF cache│  │ • UnityPlayerGameActivity│   │
│  │ • Sampling │  │ • Page lay │  │ • IL2CPP, arm64-v8a      │   │
│  └────────────┘  └────────────┘  └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│           Spring Boot Backend          │     FastAPI AI Server  │
│                                        │                        │
│  • JWT / OAuth                         │  • RAG / pgvector      │
│  • Course / Place / Story API          │  • Story / Mission gen │
│  • S3 Presigned URL                    │  • E-book content gen  │
│  • Redis cache                         │  • OpenAI-compatible   │
│                                        │                        │
│            PostgreSQL + pgvector  ·  Redis  ·  S3               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                Kakao Local / Map / Directions API
```

### 데이터 흐름

```
사용자 → 인원·성향 설문 → 코스 선택/순서 조정
                                │
                                ▼
                    Spring Boot ─→ FastAPI AI Server
                                │      (스토리 / 미션 생성)
                                ▼
                       장소별 스토리·미션 응답
                                │
                                ▼
                  Android: 지도 + 퀘스트 진행
                                │
                                ▼
                CameraX → EXIF/GPS → ONNX 분류 → 매칭
                                │
                                ▼
                 Presigned URL 업로드 → 미션 제출
                                │
                                ▼
            탐방 종료 → e-book 생성 → Compose 책 페이지 렌더링
```

</details>

---

## 프로젝트 구조

<details>
<summary>프로젝트 구조 보기</summary>

```text
S14P31D109/
├── frontend/
│   ├── app/
│   │   └── src/main/kotlin/com/ssafy/culture/
│   │       ├── ui/                # 화면, 내비게이션, 테마, 공통 컴포넌트
│   │       ├── data/              # API, Repository, 로컬 저장소, ML, route
│   │       ├── domain/            # 화면에서 사용하는 도메인 모델
│   │       └── di/                # Hilt 모듈
│   ├── unityLibrary/              # Unity Android export 모듈
│   ├── shared/
│   └── settings.gradle.kts
├── backend/                       # Spring Boot API 서버
├── ai/                            # FastAPI AI 마이크로서비스
├── db/                            # PostgreSQL / pgvector 초기화
├── nginx/                         # 배포용 reverse proxy
├── docs/                          # 문서 / README 이미지
└── docker-compose.yml
```

</details>

---

## 팀원 소개

| 이름  | 역할  | 담당                                                     |
| --- | --- | ------------------------------------------------------ |
| 변○○ | 팀장  | (역할 입력)                                                |
| ○○○ | 개발  | 백엔드 / Spring Boot                                      |
| ○○○ | 개발  | AI / FastAPI / RAG                                     |
| ○○○ | 개발  | 인프라 / DevOps                                           |
| **본인** | 개발  | **프론트엔드(Android), 게임(Unity 모듈 연동)**                    |
| ○○○ | 개발  | 디자인 / 기획                                               |

> 팀원 이름과 역할은 실제 정보로 교체해 주세요.

---

## 기술 스택

| 분류           | 스택                                                                              |
| ------------ | ------------------------------------------------------------------------------- |
| Android      | Kotlin, Jetpack Compose, Material 3, Navigation Compose, Hilt                   |
| State / 저장   | ViewModel, StateFlow, DataStore, Room                                           |
| Network      | Retrofit 3, OkHttp, Gson Converter                                              |
| Map / 위치     | Kakao Map SDK, Kakao Local / Directions API, LocationManager, Foreground Service|
| Camera / ML  | CameraX, MediaStore, FileProvider, S3 Presigned URL, ONNX Runtime Android, ML Kit|
| Game         | Unity, UnityPlayerGameActivity, IL2CPP, Android GameActivity, arm64-v8a         |
| Backend      | Java 21, Spring Boot 3.5, Spring Security, JPA, WebFlux, Redis, PostgreSQL, S3  |
| AI Server    | FastAPI, SQLAlchemy, pgvector, OpenAI SDK, RAG pipeline                         |
| Infra        | Docker Compose, Nginx, PostgreSQL pgvector, Redis                               |
| Collaboration| Git, GitLab, Jenkins, Jira, Notion                                              |

---

## 실행 방법

### 사전 요구사항

- Android Studio (Hedgehog 이상 권장), JDK 17
- Unity Editor (Unity Library export 산출물이 포함된 상태)
- Docker 및 Docker Compose
- Kakao Developers에서 발급한 Native App Key / REST API Key

### 전체 서버 실행

```bash
docker compose up --build
```

구성 서비스: `nginx`, `backend`, `ai`, `db (pgvector/pgvector:pg16)`, `redis`

### Android 앱

```bash
cd frontend
./gradlew :app:assembleDebug
```

`frontend/local.properties` 예시:

```properties
sdk.dir=YOUR_ANDROID_SDK_PATH
kakao.native.app.key=YOUR_KAKAO_NATIVE_KEY
kakao.rest.api.key=YOUR_KAKAO_REST_KEY
gms.api.key=YOUR_GMS_KEY
api.base.url=http://YOUR_SERVER/api/

# Unity 빌드가 필요한 경우
unity.androidSdkPath=YOUR_ANDROID_SDK_PATH
unity.androidNdkPath=YOUR_ANDROID_NDK_PATH
```

### Backend / AI

```bash
# Backend
cd backend && ./gradlew bootRun

# AI Server
cd ai
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

헬스 체크:

```bash
curl http://localhost:8000/api/v1/health
```
