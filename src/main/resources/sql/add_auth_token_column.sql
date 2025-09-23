-- auth_token 컬럼 추가 마이그레이션
-- 결제 테이블에 NicePay 망취소용 인증 토큰 저장을 위한 컬럼 추가

-- payments 테이블에 auth_token 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS auth_token VARCHAR(50);

-- 주석 추가
COMMENT ON COLUMN payments.auth_token IS 'NicePay 망취소용 인증 토큰 (AuthToken)';