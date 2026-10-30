-- Add cancelled_at column to queue_tokens table for Phase 2 Token cancellation/leave lifecycle
ALTER TABLE queue_tokens ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE;
