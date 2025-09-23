-- Update orders table status column constraint to include NETWORK_CANCELLED
-- This script should be run after adding NETWORK_CANCELLED to OrderStatus enum

-- First, drop the existing check constraint
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;

-- Add the new check constraint with NETWORK_CANCELLED included
ALTER TABLE orders
ADD CONSTRAINT orders_status_check
CHECK (status IN ('PENDING', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'FAILED', 'NETWORK_CANCELLED'));

-- Alternative approach: If the above doesn't work, you can also recreate the constraint by name
-- Replace 'constraint_name' with the actual constraint name if different
-- ALTER TABLE orders DROP CONSTRAINT constraint_name;
-- ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN ('PENDING', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'FAILED', 'NETWORK_CANCELLED'));