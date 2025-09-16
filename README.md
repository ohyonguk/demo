# Payment System Backend

Spring Boot 기반의 결제 시스템 백엔드 API

## 기술 스택
- Spring Boot 3.5.5
- Spring Data JPA
- PostgreSQL 15.13  
- Spring Security
- Gradle 8.x
- Java 21

## 주요 기능
- 사용자 인증 및 관리
- 주문 생성 및 관리
- 결제 처리 (카드, 적립금)
- 결제 취소 (개별 취소 지원)
- 이니시스 PG 연동
- 결제 내역 조회
- 주문 내역 조회

## API 엔드포인트

### 인증
- POST /api/auth/login - 로그인
- POST /api/auth/register - 회원가입
- GET /api/auth/me - 사용자 정보 조회

### 결제
- POST /api/payment/create-order - 주문 생성
- POST /api/payment/notify - PG 결제 알림
- POST /api/payment/response - PG 결제 응답
- GET /api/payment/status/{orderNo} - 주문 상태 조회
- POST /api/payment/refund - 카드 결제 취소
- POST /api/payment/refund/points/{orderNo} - 적립금 취소

### 내역 조회
- GET /api/payment/history/{userId} - 결제 내역 조회
- GET /api/payment/orders/{userId} - 주문 내역 조회
- GET /api/payment/order-detail/{orderNo} - 주문 상세 조회

## 실행 방법

```bash
# 애플리케이션 실행
./gradlew bootRun

# 빌드
./gradlew build
```

## 설정

application.properties에서 데이터베이스 연결 정보 설정 필요

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/your_db
spring.datasource.username=your_username  
spring.datasource.password=your_password
```

## 포트
- 기본 포트: 8081

