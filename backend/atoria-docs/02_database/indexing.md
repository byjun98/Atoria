# 📄 Indexing Strategy (Atoria)

본 문서는 Atoria 서비스의 **데이터베이스 성능 최적화를 위한 인덱스 전략**을 정의합니다.
주요 조회 패턴을 기반으로 인덱스를 설계합니다.

---

## 🎯 목표

* API 응답 속도 개선
* N+1 문제 최소화
* JOIN 성능 최적화
* 사용자 경험(UX) 향상

---

## 🔍 주요 조회 패턴

### 1. 코스 조회

* `/courses`
* `/courses/{courseId}`

👉 course_places → places JOIN 발생

---

### 2. 스토리 조회

* `/stories`
* `/stories/{storyId}`

👉 user_id 기준 조회

---

### 3. 챕터 조회

* `/stories/{storyId}/chapters`
* `/stories/{storyId}/chapters/{chapterId}`

👉 story_id + sequence 정렬

---

### 4. 진행률 조회

* `/stories/{storyId}/progress`

👉 user_chapter_progress JOIN

---

### 5. 미션 수행 조회

* 특정 chapter 기준 progress 조회

---

# 🧱 테이블별 인덱스 전략

---

## 1️⃣ users

```sql id="idx-users"
-- 이메일 로그인
CREATE INDEX idx_users_email ON users(email);
```

👉 로그인 성능 최적화

---

## 2️⃣ courses

```sql id="idx-courses"
-- 정렬/조회 대비
CREATE INDEX idx_courses_created_at ON courses(created_at);
```

---

## 3️⃣ places

```sql id="idx-places"
-- 검색 대비
CREATE INDEX idx_places_name ON places(name);

-- 카테고리 필터
CREATE INDEX idx_places_category ON places(category);
```

---

## 4️⃣ course_places

```sql id="idx-course-places"
-- 코스 기준 조회 (가장 중요)
CREATE INDEX idx_course_places_course_id 
ON course_places(course_id);

-- 순서 정렬
CREATE INDEX idx_course_places_sequence 
ON course_places(course_id, sequence);
```

👉 코스 상세 조회 핵심

---

## 5️⃣ stories

```sql id="idx-stories"
-- 사용자별 조회 (핵심)
CREATE INDEX idx_stories_user_id 
ON stories(user_id);

-- 생성일 정렬
CREATE INDEX idx_stories_created_at 
ON stories(created_at);
```

---

## 6️⃣ chapters ⭐

```sql id="idx-chapters"
-- 스토리 기준 조회 (핵심)
CREATE INDEX idx_chapters_story_id 
ON chapters(story_id);

-- 순서 정렬
CREATE INDEX idx_chapters_story_sequence 
ON chapters(story_id, sequence);
```

👉 챕터 조회 성능 핵심

---

## 7️⃣ user_chapter_progress ⭐ (가장 중요)

```sql id="idx-progress"
-- 유저 + 스토리 조회
CREATE INDEX idx_progress_user_chapter 
ON user_chapter_progress(user_id, chapter_id);

-- 챕터 기준 조회
CREATE INDEX idx_progress_chapter_id 
ON user_chapter_progress(chapter_id);
```

👉 진행률 계산 / 완료 여부 조회 핵심

---

## 8️⃣ ebooks

```sql id="idx-ebooks"
-- 스토리 기준 조회
CREATE INDEX idx_ebooks_story_id 
ON ebooks(story_id);
```

---

## 9️⃣ notifications

```sql id="idx-notifications"
-- 읽지 않은 알림 조회
CREATE INDEX idx_notifications_is_read 
ON notifications(is_read);

-- 생성일 정렬
CREATE INDEX idx_notifications_created_at 
ON notifications(created_at);
```

---

# 🚀 고급 최적화 (선택)

---

## 1. Covering Index

```sql id="covering"
CREATE INDEX idx_chapters_covering
ON chapters(story_id, sequence, mission_type);
```

👉 SELECT 시 테이블 접근 최소화

---

## 2. Partial Index (PostgreSQL)

```sql id="partial"
CREATE INDEX idx_unread_notifications
ON notifications(notification_id)
WHERE is_read = false;
```

👉 읽지 않은 알림만 빠르게 조회

---

## 3. JSONB 인덱스 (선택)

```sql id="jsonb"
CREATE INDEX idx_stories_protagonist 
ON stories USING GIN (protagonist_info);
```

👉 JSON 검색 필요할 경우

---

# ⚠️ 주의사항

* 인덱스는 많을수록 좋은 것이 아님
* INSERT / UPDATE 성능 저하 발생 가능
* 실제 트래픽 기반으로 조정 필요

---

# 💥 핵심 요약

```id="summary"
1. story_id, user_id, chapter_id 인덱스는 필수
2. sequence 정렬 인덱스는 반드시 포함
3. user_chapter_progress가 성능 핵심 테이블
```

---

# 🎯 결론

👉 **“조회 패턴 기준으로 필요한 인덱스만 정확하게 넣는다”**
