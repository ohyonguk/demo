-- net_cancel_url 컬럼 추가 마이그레이션
-- 결제 테이블에 망취소 전용 URL 저장을 위한 컬럼 추가

-- payments 테이블에 net_cancel_url 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS net_cancel_url VARCHAR(500);

-- 인덱스 추가 (필요시 망취소 URL로 조회하는 경우를 대비)
CREATE INDEX IF NOT EXISTS idx_payments_net_cancel_url ON payments(net_cancel_url);

-- 주석 추가
COMMENT ON COLUMN payments.net_cancel_url IS '망취소 전용 URL (이니시스: netCancelUrl, 나이스페이: NetCancelURL)';