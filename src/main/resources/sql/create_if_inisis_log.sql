-- if_inisis_log 테이블 생성 (이니시스 API 통신 로그)
CREATE TABLE IF NOT EXISTS if_inisis_log (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL,
    request_type VARCHAR(20) NOT NULL, -- 'REQUEST' 또는 'RESPONSE'
    request_url VARCHAR(500),
    request_data TEXT,
    response_data TEXT,
    http_status INTEGER,
    is_success BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    transaction_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_if_inisis_log_order_no (order_no),
    INDEX idx_if_inisis_log_created_at (created_at),
    INDEX idx_if_inisis_log_transaction_id (transaction_id)
);

-- 테이블 코멘트
COMMENT ON TABLE if_inisis_log IS '이니시스 API 통신 로그 테이블';
COMMENT ON COLUMN if_inisis_log.id IS '로그 ID (자동증가)';
COMMENT ON COLUMN if_inisis_log.order_no IS '주문번호';
COMMENT ON COLUMN if_inisis_log.request_type IS '요청 타입 (REQUEST/RESPONSE)';
COMMENT ON COLUMN if_inisis_log.request_url IS '요청 URL';
COMMENT ON COLUMN if_inisis_log.request_data IS '요청 데이터 (JSON)';
COMMENT ON COLUMN if_inisis_log.response_data IS '응답 데이터 (JSON)';
COMMENT ON COLUMN if_inisis_log.http_status IS 'HTTP 상태 코드';
COMMENT ON COLUMN if_inisis_log.is_success IS '성공 여부';
COMMENT ON COLUMN if_inisis_log.error_message IS '에러 메시지';
COMMENT ON COLUMN if_inisis_log.transaction_id IS '거래 ID';
COMMENT ON COLUMN if_inisis_log.created_at IS '생성일시';