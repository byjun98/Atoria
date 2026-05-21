# 데이터 파이프라인 문서

## 개요

문화재/설화 원천 데이터를 수집하고 정제·보강하여 RAG 인제스트 직전의 표준 JSON과 인덱스 CSV를 생성하는 파이프라인.

## 폴더 구조

```
data/
  raw/                        # 원본 CSV (gyeongju_heritage.csv 등)
  raw_html/<site>/            # 크롤링 원본 HTML (재현/재파싱 위해 보존)
  enriched/
    legends/                  # 설화 단위 JSON (1파일 = 1설화)
    heritage_context/         # 문화재 배경자료 JSON (1파일 = 1문서)
    heritage_index.csv        # 문화재 ↔ 관련 설화/문맥 매핑
    legend_index.csv          # 설화 메타 인덱스
  failures.jsonl              # 수집/파싱 실패 레코드 (JSONL, append-only)
```

## 스키마

### BaseDataRecord (공통 필드)
| 필드 | 타입 | 설명 |
|------|------|------|
| source_type | str | `"legend"` 또는 `"heritage_context"` |
| source_site | str | 출처 사이트명 |
| source_url | str | 원본 URL |
| region | str | 지역 (기본: "경상북도 경주") |
| era | str? | 시대 |
| related_heritages | list[str] | 관련 문화재 |
| related_people | list[str] | 관련 인물 |
| related_places | list[str] | 관련 장소 |
| motifs | list[str] | 모티프 태그 |
| tone_tags | list[str] | 분위기 태그 |
| story_hooks | list[str] | 스토리 훅 |
| factuality_level | str | legend/history/folktale/mixed |
| metadata | RecordMetadata | 메타데이터 객체 |

### LegendRecord (설화)
BaseDataRecord 확장:
- `legend_id`: 고유 ID
- `title`: 설화 제목
- `category`: 분류
- `summary`: 요약문
- `original_text`: 원문

### HeritageContextRecord (문화재 배경)
BaseDataRecord 확장:
- `record_id`: 고유 ID
- `heritage_name`: 문화재 정식명
- `heritage_aliases`: 별칭 목록
- `title`: 문서 제목
- `definition`: 정의
- `key_facts`: 핵심 사실 (dict)
- `narrative_excerpts`: 서사 발췌문

### HeritageEntity (마스터)
- `heritage_id`: 영문 슬러그 (kebab-case)
- `canonical_name`: 정식 한글명
- `aliases`: 별칭
- `designation`: 국보/보물/사적 등

## CLI 사용법

```bash
# 특정 사이트 + 특정 문화재 수집
python -m scripts.data.preprocess collect --site encykorea --heritage 첨성대

# 전체 보강
python -m scripts.data.preprocess enrich

# 검증
python -m scripts.data.preprocess validate

# 인덱스 재생성
python -m scripts.data.preprocess index

# 전체 파이프라인
python -m scripts.data.preprocess all

# 실패 재시도 포함 전체 실행
python -m scripts.data.preprocess all --retry-failures
```

## 선정 문화재 (15개)

| ID | 정식명 | 주요 별칭 | 지정 |
|----|--------|-----------|------|
| cheomseongdae | 첨성대 | 瞻星臺, 점성대 | 국보 |
| seokbinggo | 석빙고 | 石氷庫 | 보물 |
| donggung-wolji | 동궁과월지 | 안압지, 임해전지 | 사적 |
| dabotap | 불국사 다보탑 | 多寶塔 | 국보 |
| seokgatap | 불국사 석가탑 | 釋迦塔, 무영탑 | 국보 |
| bulguksa-daeungjeon | 불국사 대웅전 | 大雄殿 | 보물 |
| baekungyo | 불국사 백운교 | 白雲橋, 청운교 | 국보 |
| emille-bell | 에밀레종 | 성덕대왕신종, 聖德大王神鐘 | 국보 |
| cheonmachong | 천마총 | 天馬塚 | 사적 |
| geumgwanchong | 금관총 | 金冠塚 | 사적 |
| bonghwangdae | 봉황대 | 鳳凰臺 | 사적 |
| gyeongju-hyanggyo | 경주향교 | 鄕校 | 사적 |
| goseonsa-samcheung-seoktap | 경주 고선사지 삼층석탑 | 高仙寺址 | 국보 |
| cheonmachong-gold-crown | 천마총 금관 | 天馬塚 金冠 | 국보 |
| seondeok-queen-tomb | 선덕여왕릉 | 善德女王陵 | 사적 |

## 새 사이트 추가 방법

1. `app/data_pipeline/sites.py`에 `SiteConfig` 항목 추가
2. `app/data_pipeline/collectors/` 아래 새 모듈 생성
   - `BaseCollector`를 상속하고 `collect()` 구현
3. `app/data_pipeline/run.py`의 `COLLECTOR_MAP`에 등록

## 문화재 추가/교체 절차

1. `app/data_pipeline/registry.py`의 `SELECTED_HERITAGES` 리스트에 `HeritageEntity` 추가
2. `heritage_id`는 kebab-case 영문 슬러그
3. `aliases`에 한자/별칭 모두 등록
4. 파이프라인 재실행: `python -m scripts.data.preprocess all`
5. `heritage_index.csv`에 새 ID가 나타나는지 확인

## 환경 설정

`.env` 파일에서 제어 가능:

```env
ENABLE_LLM_SUMMARY=false       # LLM 요약 활성화 (기본: false)
CRAWL_POLITE_DELAY_SEC=1.5     # 크롤링 요청 간 대기 (초)
CRAWL_USER_AGENT=Mozilla/5.0 ...
```

## 파이프라인 단계

```
collect → clean → enrich → validate → index
```

1. **collect**: 각 사이트에서 HTML 수집 → 파싱 → LegendRecord/HeritageContextRecord 생성
2. **clean**: 텍스트 정규화 (NFC, 한자 병기 보존, 제어문자 제거)
3. **enrich**: 엔티티 링킹, 모티프 탐지, 톤 태깅, 요약, 스토리 훅 생성
4. **validate**: Pydantic 검증 + needs_review 플래그 설정
5. **index**: heritage_index.csv, legend_index.csv 재생성
