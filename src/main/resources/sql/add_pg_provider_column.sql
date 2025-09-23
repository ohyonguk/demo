-- pg_provider 컬럼 추가 마이그레이션
-- 결제 테이블에 PG사 구분을 위한 컬럼 추가

-- payments 테이블에 pg_provider 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pg_provider VARCHAR(20);

-- 기존 데이터에 대해 기본값 설정 (NULL인 경우 INICIS로 간주)
-- 실제 운영환경에서는 기존 데이터 분석 후 적절한 값 설정 필요
UPDATE payments
SET pg_provider = 'INICIS'
WHERE pg_provider IS NULL;

-- 인덱스 추가 (PG사별 조회 성능 향상)
CREATE INDEX IF NOT EXISTS idx_payments_pg_provider ON payments(pg_provider);

-- 주석 추가
COMMENT ON COLUMN payments.pg_provider IS '결제 PG사 구분 (INICIS, NICEPAY)';