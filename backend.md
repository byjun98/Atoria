# Backend 설정 가이드 (Spring Boot)

> ## 1. Spring Initializr 기본 설정

### ✅ Project
- **Gradle - Groovy**

**이유**
- Spring Boot에서 가장 보편적
- 레퍼런스 많고 팀 협업 시 안정적
- 초기 세팅 시 문제 적음

---

### ✅ Language
- **Java**

**이유**
- Spring 생태계에서 가장 안정적
- 자료 / 트러블슈팅 압도적으로 많음

---

### ✅ Spring Boot 버전
- ✅ **3.5.13**

**이유**
- 4.x는 아직 검증 부족
- Docker / Redis / JWT / PostgreSQL / FastAPI 연동까지 고려하면  
  **3.5.x가 훨씬 안정적**

---

### ✅ Java Version
- **Java 21**

---

### ✅ Packaging
- **Jar**

**이유**
- 내장 톰캣 포함
- Docker 환경에서 가장 편함
- WAR 쓸 이유 거의 없음

---

### ✅ Configuration
- **YAML (application.yml)**

**이유**
- 설정 많아질 예정 (DB, Redis, JWT, S3, AI 서버 등)
- profile 분리까지 고려하면 YAML이 훨씬 가독성 좋음



<br>

> ## 2. 프로젝트 네이밍 규칙

- Group: com.atoria
- Artifact: backend
- Package: com.atoria.backend
<br><br>

**이유**
- 구조 명확
- 서비스 분리 (Frontend / AI / Infra) 시 관리 편함

<br>

> ## 3. Dependency 구성

#### 1. Spring Web
- REST API 서버 구성

---

#### 2. Spring Data JPA
- PostgreSQL ORM 처리
- 주요 엔티티 관리 (User, Mission, Session 등)

---

#### 3. PostgreSQL Driver
- DB 연결 필수

---

#### 4. Spring Security
- JWT 기반 인증 / 인가 처리

---

#### 5. Validation
- DTO 검증
- 예: `@NotBlank`, `@Email`, `@Size`

---

#### 6. Spring Data Redis
- Refresh Token 저장
- 임시 상태 저장 (세션, 미션 등)

---

#### 7. Lombok
- Boilerplate 코드 제거
- DTO / Entity / Builder 간소화

---

#### 8. Spring Boot DevTools
- 개발 중 자동 재시작  
> 운영 환경에서는 제외됨

---

#### 9. Actuator
- 헬스 체크 (`/actuator/health`)
- Docker / 배포 상태 확인
- 모니터링 확장 가능

---

#### 10. OAuth2 Client
- 카카오 / 구글 로그인 대비

---

#### 11. Spring Reactive Web (WebFlux)
- `WebClient` 사용 목적

**이유**
- FastAPI (AI 서버) 호출 시 표준 HTTP 클라이언트
- RestTemplate보다 최신 / 비동기 지원

<br>

> ## 4. REDIS GUI (RedisInsight)



![alt text](image.png)