# 백엔드 ↔ AI 서버 API 명세서

## 1. 스토리 및 미션 생성 API

### 개요

확정된 코스와 사용자 성향을 바탕으로 탐방 시작 전 스토리를 생성한다.

AI 서버는 사용자 정보와 방문 장소 목록을 입력받아 다음 구조의 콘텐츠를 생성한다.

- 탐방 시작 전 인트로 스토리
- 장소별 미션
- 장소별 스토리 조각
- 탐방 종료 아웃트로 스토리

---

### Endpoint

```http
POST /story/intro
```

---

### Request Body

```json
{
  "people_cnt": 2,
  "people_information": [
    {
      "name": "민준",
      "age": 5,
      "tendency": "모험적"
    },
    {
      "name": "성준",
      "age": 6,
      "tendency": "모험적"
    }
  ],
  "places": [
    {
      "place_id": 301,
      "sequence": 1,
      "name": "첨성대",
      "description": "신라 시대 천문 관측소",
      "address": "경북 경주시 첨성로 140-25",
      "category": "역사",
      "latitude": 35.8347,
      "longitude": 129.2194
    },
    {
      "place_id": 302,
      "sequence": 2,
      "name": "불국사",
      "description": "유네스코 세계문화유산으로 지정된 신라 불교 사찰",
      "address": "경북 경주시 불국로 385",
      "category": "역사",
      "latitude": 35.7900,
      "longitude": 129.3320
    },
    {
      "place_id": 303,
      "sequence": 3,
      "name": "석굴암",
      "description": "신라 시대 석굴 사원",
      "address": "경북 경주시 석굴로 238",
      "category": "역사",
      "latitude": 35.7950,
      "longitude": 129.3490
    }
  ]
}
```

---

### Request Field

| 필드명 | 타입 | 필수 여부 | 설명 |
|---|---:|:---:|---|
| people_cnt | Number | Y | 탐방 인원 수 |
| people_information | Array | Y | 탐방 참여자 정보 목록 |
| people_information[].name | String | Y | 참여자 이름 |
| people_information[].age | Number | Y | 참여자 나이 |
| people_information[].tendency | String | Y | 참여자 성향 |
| places | Array | Y | 탐방 장소 목록 |
| places[].place_id | Number | Y | 장소 ID |
| places[].sequence | Number | Y | 탐방 순서 |
| places[].name | String | Y | 장소명 |
| places[].description | String | Y | 장소 설명 |
| places[].address | String | Y | 장소 주소 |
| places[].category | String | Y | 장소 카테고리 |
| places[].latitude | Number | Y | 위도 |
| places[].longitude | Number | Y | 경도 |

---

### Response Body

```json
{
  "intro": "...",
  "missions": [
    {
      "sequence": 1,
      "title": "숨겨진 문양 찾기",
      "description": "불국사에서 금색 문양을 찾아라",
      "verification_hint": "사진에 보여야 하는 완료 판정 조건",
      "type": "탐색",
      "story": "당신은 신라의 탐험가로..."
    }
  ],
  "outro": "..."
}
```

---

### Response Field

| 필드명 | 타입 | 설명 |
|---|---:|---|
| intro | String | 탐방 시작 전 오프닝 스토리 |
| missions | Array | 장소별 미션 및 스토리 목록 |
| missions[].sequence | Number | 미션 순서 |
| missions[].title | String | 미션 제목 |
| missions[].description | String | 미션 설명 |
| missions[].verification_hint | String / null | 사진 미션 완료 판정용 힌트 |
| missions[].type | String | 미션 유형 |
| missions[].story | String | 해당 장소에서 이어지는 스토리 조각 |
| outro | String | 탐방 종료 후 마무리 스토리 |

---

## 2. E-book 콘텐츠 생성 API

### 개요

AI 서버는 E-book용 콘텐츠 구조를 생성한다.

최종 PDF 또는 EPUB 파일 저장은 Spring Boot 또는 File 서버가 담당한다.  
AI 서버는 PDF 파일 자체를 생성하는 것이 아니라, E-book 제작에 필요한 구조화된 콘텐츠를 반환한다.

---

### Endpoint

```http
POST /artifacts/ebook/jobs
```

---

### Request Body

```json
{
  "story_id": 301,
  "user_id": 1,
  "story": {
    "title": "경주의 비밀을 푸는 두 꼬마 탐험가",
    "intro": "햇살이 따스한 어느 날, 다섯 살 민준과 여섯 살 성준은 할아버지의 낡은 책장에서 반짝이는 지도 한 장을 발견했어요. 지도에는 '경주의 잃어버린 별빛을 찾아라'라는 글귀가 적혀 있었죠...",
    "outro": "모든 단서를 풀어낸 민준과 성준은 할아버지의 지도 속으로 다시 돌아왔어요. 두 형제의 손에는 경주에서 얻은 별빛, 지혜, 용기의 기억이 반짝이고 있었죠...",
    "protagonist_info": {
      "people_cnt": 2,
      "people_information": [
        {
          "name": "민준",
          "age": 5,
          "tendency": "모험적"
        },
        {
          "name": "성준",
          "age": 6,
          "tendency": "모험적"
        }
      ]
    }
  },
  "chapters": [
    {
      "sequence": 1,
      "place_id": 301,
      "place_name": "첨성대",
      "place_address": "경북 경주시 첨성로 140-25",
      "mission_title": "별빛 관측소에서 단서 찾기",
      "mission_description": "첨성대 앞에서 별자리 사진을 찍어보세요.",
      "mission_type": "PHOTO",
      "story_content": "첨성대에 도착한 두 형제는 하늘을 올려다보며 별자리의 비밀을 발견했어요. 민준이 카메라를 들어 올리자 별빛이 두 형제를 감싸며 첫 번째 단서를 비춰주었답니다.",
      "user_result": {
        "image_url": "https://s3.amazonaws.com/atoria/mission1.jpg",
        "choice": null
      }
    },
    {
      "sequence": 2,
      "place_id": 302,
      "place_name": "불국사",
      "place_address": "경북 경주시 불국로 385",
      "mission_title": "불국사의 비밀을 선택하라",
      "mission_description": "불국사가 품고 있는 진짜 의미는 무엇일까요?",
      "mission_type": "CHOICE",
      "story_content": "불국사에 선 민준과 성준은 세 개의 두루마리 앞에서 고민했어요. 성준이 먼저 '이상 세계'라고 적힌 두루마리를 가리키자, 순간 사찰 전체가 따스한 빛으로 물들었어요.",
      "user_result": {
        "image_url": null,
        "choice": "불국사는 이상 세계를 표현한 사찰이다."
      }
    },
    {
      "sequence": 3,
      "place_id": 303,
      "place_name": "석굴암",
      "place_address": "경북 경주시 석굴로 238",
      "mission_title": "수호자의 포즈를 취하라",
      "mission_description": "석굴암 앞에서 수호자 포즈로 사진을 찍어보세요.",
      "mission_type": "PHOTO",
      "story_content": "석굴암 앞에 선 민준과 성준은 서로를 바라보며 씩 웃었어요. 두 형제는 어깨를 펴고 수호자의 포즈를 취했어요. 찰칵!",
      "user_result": {
        "image_url": "https://s3.amazonaws.com/atoria/mission3.jpg",
        "choice": null
      }
    }
  ]
}
```

---

### Request Field

| 필드명 | 타입 | 필수 여부 | 설명 |
|---|---:|:---:|---|
| story_id | Number | Y | 생성된 스토리 ID |
| user_id | Number | Y | 사용자 ID |
| story | Object | Y | 전체 스토리 정보 |
| story.title | String | Y | E-book 제목 |
| story.intro | String | Y | 인트로 스토리 |
| story.outro | String | Y | 아웃트로 스토리 |
| story.protagonist_info | Object | Y | 주인공 정보 |
| story.protagonist_info.people_cnt | Number | Y | 주인공 수 |
| story.protagonist_info.people_information | Array | Y | 주인공 상세 정보 목록 |
| chapters | Array | Y | E-book 챕터 목록 |
| chapters[].sequence | Number | Y | 챕터 순서 |
| chapters[].place_id | Number | Y | 장소 ID |
| chapters[].place_name | String | Y | 장소명 |
| chapters[].place_address | String | Y | 장소 주소 |
| chapters[].mission_title | String | Y | 미션 제목 |
| chapters[].mission_description | String | Y | 미션 설명 |
| chapters[].mission_type | String | Y | 미션 유형 |
| chapters[].story_content | String | Y | 해당 챕터의 스토리 내용 |
| chapters[].user_result | Object | Y | 사용자의 미션 수행 결과 |
| chapters[].user_result.image_url | String / null | N | 미션 수행 이미지 URL |
| chapters[].user_result.choice | String / null | N | 선택형 미션의 사용자 선택값 |

---

### Response Body

```json
{
  "success": true,
  "data": {
    "story_id": 301,
    "ebook_content": {
      "meta": {
        "title": "경주의 비밀을 푸는 두 꼬마 탐험가",
        "subtitle": "민준과 성준의 경주 탐험 이야기",
        "author": "민준, 성준",
        "page_count": 7,
        "language": "ko"
      },
      "cover": {
        "title": "경주의 비밀을 푸는 두 꼬마 탐험가",
        "background_color": "#F5E6D3",
        "thumbnail_hint": "첨성대 앞에서 별을 바라보는 두 어린 형제"
      },
      "pages": [
        {
          "page_number": 1,
          "type": "COVER",
          "layout": "COVER",
          "title": "경주의 비밀을 푸는 두 꼬마 탐험가",
          "subtitle": "민준과 성준의 경주 탐험 이야기",
          "text": null,
          "image_url": null,
          "caption": null
        },
        {
          "page_number": 2,
          "type": "INTRO",
          "layout": "TEXT_ONLY",
          "title": "이야기의 시작",
          "subtitle": null,
          "text": "햇살이 따스한 어느 날, 다섯 살 민준과 여섯 살 성준은 할아버지의 낡은 책장에서 반짝이는 지도 한 장을 발견했어요. 지도에는 '경주의 잃어버린 별빛을 찾아라'라는 글귀가 적혀 있었죠.",
          "image_url": null,
          "caption": null
        },
        {
          "page_number": 3,
          "type": "CHAPTER",
          "layout": "IMAGE_TOP_TEXT_BOTTOM",
          "sequence": 1,
          "title": "첫 번째 단서, 첨성대",
          "subtitle": "경북 경주시 첨성로 140-25",
          "text": "첨성대에 도착한 두 형제는 하늘을 올려다보며 별자리의 비밀을 발견했어요. 민준이 카메라를 들어 올리자 별빛이 두 형제를 감싸며 첫 번째 단서를 비춰주었답니다.",
          "image_url": "https://s3.amazonaws.com/atoria/mission1.jpg",
          "caption": "민준이 찍은 첨성대의 별빛"
        },
        {
          "page_number": 4,
          "type": "CHAPTER",
          "layout": "TEXT_WITH_QUOTE",
          "sequence": 2,
          "title": "불국사의 지혜",
          "subtitle": "경북 경주시 불국로 385",
          "text": "불국사에 선 민준과 성준은 세 개의 두루마리 앞에서 고민했어요. 성준이 먼저 '이상 세계'라고 적힌 두루마리를 가리키자, 순간 사찰 전체가 따스한 빛으로 물들었어요.",
          "image_url": null,
          "caption": null,
          "quote": "불국사는 이상 세계를 표현한 사찰이다."
        },
        {
          "page_number": 5,
          "type": "CHAPTER",
          "layout": "IMAGE_TOP_TEXT_BOTTOM",
          "sequence": 3,
          "title": "석굴암의 수호자",
          "subtitle": "경북 경주시 석굴로 238",
          "text": "석굴암 앞에 선 민준과 성준은 서로를 바라보며 씩 웃었어요. 두 형제는 어깨를 펴고 수호자의 포즈를 취했어요. 찰칵!",
          "image_url": "https://s3.amazonaws.com/atoria/mission3.jpg",
          "caption": "수호자가 된 두 형제"
        },
        {
          "page_number": 6,
          "type": "OUTRO",
          "layout": "TEXT_ONLY",
          "title": "그리고, 새로운 여행",
          "subtitle": null,
          "text": "모든 단서를 풀어낸 민준과 성준은 할아버지의 지도 속으로 다시 돌아왔어요. 두 형제의 손에는 경주에서 얻은 별빛, 지혜, 용기의 기억이 반짝이고 있었죠.",
          "image_url": null,
          "caption": null
        },
        {
          "page_number": 7,
          "type": "BACK_COVER",
          "layout": "BACK_COVER",
          "title": "민준과 성준의 모험은 계속됩니다",
          "subtitle": "Atoria와 함께한 경주 여행",
          "text": "방문한 장소: 첨성대 · 불국사 · 석굴암",
          "image_url": null,
          "caption": null
        }
      ]
    }
  },
  "error": null,
  "timestamp": "2026-04-23T13:00:00Z"
}
```

---

### Response Field

| 필드명 | 타입 | 설명 |
|---|---:|---|
| success | Boolean | API 처리 성공 여부 |
| data | Object | 응답 데이터 |
| data.story_id | Number | 스토리 ID |
| data.ebook_content | Object | E-book 콘텐츠 구조 |
| data.ebook_content.meta | Object | E-book 메타데이터 |
| data.ebook_content.meta.title | String | E-book 제목 |
| data.ebook_content.meta.subtitle | String | E-book 부제목 |
| data.ebook_content.meta.author | String | 저자명 |
| data.ebook_content.meta.page_count | Number | 전체 페이지 수 |
| data.ebook_content.meta.language | String | 언어 코드 |
| data.ebook_content.cover | Object | 표지 정보 |
| data.ebook_content.cover.title | String | 표지 제목 |
| data.ebook_content.cover.background_color | String | 표지 배경색 |
| data.ebook_content.cover.thumbnail_hint | String | 표지 이미지 생성 또는 선택을 위한 설명 |
| data.ebook_content.pages | Array | E-book 페이지 목록 |
| data.ebook_content.pages[].page_number | Number | 페이지 번호 |
| data.ebook_content.pages[].type | String | 페이지 유형 |
| data.ebook_content.pages[].layout | String | 페이지 레이아웃 |
| data.ebook_content.pages[].sequence | Number | 챕터 순서 |
| data.ebook_content.pages[].title | String | 페이지 제목 |
| data.ebook_content.pages[].subtitle | String / null | 페이지 부제목 |
| data.ebook_content.pages[].text | String / null | 페이지 본문 |
| data.ebook_content.pages[].image_url | String / null | 페이지 이미지 URL |
| data.ebook_content.pages[].caption | String / null | 이미지 캡션 |
| data.ebook_content.pages[].quote | String / null | 선택형 미션 결과 또는 인용 문구 |
| error | Object / null | 에러 정보 |
| timestamp | String | 응답 생성 시각 |

---

## Enum 정의

### Mission Type

| 값 | 설명 |
|---|---|
| PHOTO | 사진 촬영형 미션 |
| CHOICE | 선택형 미션 |
| QUIZ | 퀴즈형 미션 |
| ACTION | 행동 수행형 미션 |

---

### Page Type

| 값 | 설명 |
|---|---|
| COVER | E-book 표지 |
| INTRO | 인트로 페이지 |
| CHAPTER | 장소별 챕터 페이지 |
| OUTRO | 아웃트로 페이지 |
| BACK_COVER | 뒷표지 페이지 |

---

### Page Layout

| 값 | 설명 |
|---|---|
| COVER | 표지 레이아웃 |
| TEXT_ONLY | 텍스트 중심 레이아웃 |
| IMAGE_TOP_TEXT_BOTTOM | 상단 이미지, 하단 텍스트 레이아웃 |
| TEXT_WITH_QUOTE | 본문과 선택 결과 또는 인용문을 함께 보여주는 레이아웃 |
| BACK_COVER | 뒷표지 레이아웃 |

---

## 처리 책임 분리

| 구분 | 담당 서버 | 역할 |
|---|---|---|
| 스토리 생성 | AI 서버 | 사용자 정보와 장소 정보를 기반으로 인트로, 미션, 장소별 스토리, 아웃트로 생성 |
| E-book 콘텐츠 구조 생성 | AI 서버 | E-book 제작에 필요한 페이지 구조와 텍스트 콘텐츠 생성 |
| 최종 파일 생성 | Spring Boot / File 서버 | AI 서버가 반환한 콘텐츠 구조를 기반으로 PDF 또는 EPUB 파일 생성 |
| 파일 저장 | Spring Boot / File 서버 | 생성된 PDF 또는 EPUB 파일 저장 및 다운로드 URL 관리 |
| 사용자 미션 결과 관리 | Spring Boot 서버 | 사진 URL, 선택 결과, 미션 완료 상태 저장 |
