-- payments 테이블에 user_id 컬럼 추가 (NULL 허용)
ALTER TABLE payments ADD COLUMN user_id BIGINT;

-- 기존 데이터의 user_id 업데이트 (orders 테이블과 조인)
UPDATE payments p
SET user_id = (
    SELECT o.user_id
    FROM orders o
    WHERE o.order_no = p.order_no
)
WHERE p.user_id IS NULL;

-- user_id에 인덱스 추가 (성능 향상)
CREATE INDEX idx_payments_user_id ON payments(user_id);

-- 외래키 제약조건 추가 (선택사항)
-- ALTER TABLE payments ADD CONSTRAINT fk_payments_user_id FOREIGN KEY (user_id) REFERENCES users(id);