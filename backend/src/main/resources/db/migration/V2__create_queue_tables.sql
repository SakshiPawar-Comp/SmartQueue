CREATE TABLE queues (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    token_prefix VARCHAR(10) NOT NULL DEFAULT 'A',
    current_token_number VARCHAR(20) NOT NULL DEFAULT 'A000',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE queue_tokens (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    token_number VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    called_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    held_at TIMESTAMP WITH TIME ZONE,
    skipped_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_queue_token_unique UNIQUE (queue_id, token_number)
);

CREATE INDEX idx_queue_tokens_queue_id ON queue_tokens (queue_id);
CREATE INDEX idx_queue_tokens_status ON queue_tokens (status);
CREATE INDEX idx_queue_tokens_called_at ON queue_tokens (called_at);

-- Insert initial queue status enum values comment
COMMENT ON TABLE queues IS 'Represents a queue for a service/counter within an organization';
COMMENT ON TABLE queue_tokens IS 'Represents a customer digital token in a queue';
COMMENT ON COLUMN queues.status IS 'ACTIVE | PAUSED | CLOSED';
COMMENT ON COLUMN queue_tokens.status IS 'WAITING | CALLED | HOLD | COMPLETED | SKIPPED';
COMMENT ON COLUMN queue_tokens.token_number IS 'Sequential token display number (e.g., A001, A002)';