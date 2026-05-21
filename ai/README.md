# ATORIA AI Microservice

ATORIA AI 마이크로서비스 — 환경설정 및 공통 인프라 구성.

## 1. 로컬 직접 실행

```bash
cd ai
python -m venv venv
.\venv\Scripts\activate        # Windows
# source venv/bin/activate     # macOS/Linux
pip install -r requirements.txt
uvicorn app.main:app --reload
```

정상 기동 후 헬스체크:
```bash
curl http://localhost:8000/api/v1/health
# 기대 응답: {"status":"ok","database":"ok"}
```

## 2. DB 확인

- 개발 환경에서는 **DBeaver**로 PostgreSQL에 접속한다.
- 로컬 DBeaver 접속: Host `localhost`, Port `5432`.
- Docker 내부 AI 서비스의 `POSTGRES_HOST`는 `db`를 사용한다.

## 3. Docker 연동

- 전체 `docker-compose.yml`은 **인프라 파트**에서 관리한다.
- AI 서비스는 인프라 compose의 `ai: build: ./ai` 구조에서 빌드된다.
- 이번 티켓에서는 docker-compose 파일을 생성하지 않는다.
