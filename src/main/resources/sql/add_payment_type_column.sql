-- Add payment_type column to payments table
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_type VARCHAR(20) NOT NULL DEFAULT 'CARD';

-- Update existing records to have CARD as default payment type
UPDATE payments SET payment_type = 'CARD' WHERE payment_type IS NULL;

-- Add comment for the new column
COMMENT ON COLUMN payments.payment_type IS '결제 타입 (CARD, POINT, CARD_REFUND, POINT_REFUND)';