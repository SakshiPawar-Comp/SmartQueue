-- Add customer_id column to queue_tokens table
ALTER TABLE queue_tokens ADD COLUMN customer_id UUID NOT NULL REFERENCES users(id);

-- Create index for customer_id for faster lookups
CREATE INDEX idx_queue_tokens_customer_id ON queue_tokens (customer_id);