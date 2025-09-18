# 환경별 설정 및 실행 가이드

## 📋 환경 구성

이 프로젝트는 다음과 같은 환경을 지원합니다:

- **local**: 로컬 개발 환경 (기본값)
- **dev**: 개발 서버 환경
- **stg**: 스테이징 환경
- **prd**: 운영 환경

## 🚀 환경별 실행 방법

### 1. Local 환경 (기본)
```bash
# 기본 실행 (local 프로파일)
./gradlew bootRun

# 또는 명시적 지정
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 2. Development 환경
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 3. Staging 환경
```bash
./gradlew bootRun --args='--spring.profiles.active=stg'
```

### 4. Production 환경
```bash
./gradlew bootRun --args='--spring.profiles.active=prd'
```

## 🔧 환경 변수 설정

### Staging 환경 필수 환경 변수
```bash
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
export NICEPAY_MERCHANT_ID=your_nicepay_merchant_id
export NICEPAY_MERCHANT_KEY=your_nicepay_merchant_key
export NICEPAY_CANCEL_PWD=your_cancel_password
export INICIS_MERCHANT_ID=your_inicis_merchant_id
export INICIS_SIGN_KEY=your_inicis_sign_key
export JWT_SECRET=your_jwt_secret_key
```

### Production 환경 필수 환경 변수
```bash
export DB_HOST=your_production_db_host
export DB_PORT=5432
export DB_NAME=demo_prd
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
export NICEPAY_MERCHANT_ID=your_production_nicepay_merchant_id
export NICEPAY_MERCHANT_KEY=your_production_nicepay_merchant_key
export NICEPAY_CANCEL_PWD=your_production_cancel_password
export INICIS_MERCHANT_ID=your_production_inicis_merchant_id
export INICIS_SIGN_KEY=your_production_inicis_sign_key
export JWT_SECRET=your_production_jwt_secret_key
export ALLOWED_ORIGINS=https://your-frontend-domain.com
export REDIS_HOST=your_redis_host
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password
```

## 🌐 CORS 설정

각 환경별로 허용되는 도메인이 다르게 설정되어 있습니다:

### Local 환경
- Frontend: `http://localhost:3000`, `http://127.0.0.1:3000`
- PG 도메인: 테스트 환경 도메인들

### Development 환경
- Frontend: `http://localhost:3000`, `http://localhost:3001`
- PG 도메인: 테스트 환경 도메인들

### Staging 환경
- Frontend: `https://stg-app.company.com`
- PG 도메인: 테스트 환경 도메인들

### Production 환경
- Frontend: 환경 변수로 설정 (`ALLOWED_ORIGINS`)
- PG 도메인: 운영 환경 도메인들

## 💳 PG 설정

### 테스트 환경 (Local, Dev, Stg)
- **이니시스**: 테스트 MID (`INIpayTest`) 사용
- **나이스페이**: 샌드박스 API 사용

### 운영 환경 (Prd)
- **이니시스**: 실제 운영 MID 사용
- **나이스페이**: 운영 API 사용

## 🗄️ 데이터베이스 설정

### Local 환경
- Database: `demo` (로컬 PostgreSQL)
- DDL: `update` (자동 스키마 업데이트)

### Development 환경
- Database: `demo_dev`
- DDL: `create-drop` (테스트용)

### Staging 환경
- Database: `demo_stg`
- DDL: `validate` (스키마 검증만)

### Production 환경
- Database: `demo_prd`
- DDL: `validate` (스키마 검증만)
- 커넥션 풀 최적화 설정

## 🔍 로깅 설정

### Local/Development
- 레벨: `DEBUG`
- SQL 로깅: 활성화
- 포맷팅: 활성화

### Staging/Production
- 레벨: `INFO`
- SQL 로깅: 비활성화
- 파일 로깅: 활성화 (Production만)

## 🏥 헬스체크

### Staging/Production 환경에서만 활성화
```
GET /actuator/health
GET /actuator/info
```

## 🔐 보안 설정

### JWT 토큰
- Local: 간단한 키 사용
- Staging/Production: 복잡한 키 환경 변수로 설정

### SSL (Production)
- Production 환경에서는 SSL 설정 주석 해제하여 사용
- 키스토어 경로와 비밀번호를 환경 변수로 설정

## 📝 IDE에서 실행

### IntelliJ IDEA
1. Run Configuration 생성
2. Program arguments에 추가: `--spring.profiles.active=dev`
3. Environment variables에 필요한 변수들 설정

### VS Code
1. `launch.json` 설정
```json
{
    "type": "java",
    "name": "Spring Boot - Dev",
    "request": "launch",
    "mainClass": "com.example.demo.DemoApplication",
    "args": "--spring.profiles.active=dev",
    "env": {
        "DB_USERNAME": "your_username",
        "DB_PASSWORD": "your_password"
    }
}
```

## 🐳 Docker 실행

```dockerfile
# Dockerfile 예시
FROM openjdk:17-jdk-slim

COPY target/demo-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prd

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
# Docker 실행
docker run -e SPRING_PROFILES_ACTIVE=prd \
           -e DB_USERNAME=your_username \
           -e DB_PASSWORD=your_password \
           your-app-image
```