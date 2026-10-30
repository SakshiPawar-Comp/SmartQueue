-- Flyway Migration V6: Create counters, staff/counter assignments, counter/queue assignments and operational fields

CREATE TABLE counters (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_counters_organization_id ON counters (organization_id);
CREATE INDEX idx_counters_branch_id ON counters (branch_id);
CREATE INDEX idx_counters_status ON counters (status);

CREATE TABLE staff_counter_assignments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    counter_id UUID NOT NULL REFERENCES counters(id),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    unassigned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_staff_assignments_org ON staff_counter_assignments (organization_id);
CREATE INDEX idx_staff_assignments_user ON staff_counter_assignments (user_id);
CREATE INDEX idx_staff_assignments_counter ON staff_counter_assignments (counter_id);
CREATE INDEX idx_staff_assignments_status ON staff_counter_assignments (status);

CREATE TABLE counter_queue_assignments (
    id UUID PRIMARY KEY,
    counter_id UUID NOT NULL REFERENCES counters(id),
    queue_id UUID NOT NULL REFERENCES queues(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_counter_queue UNIQUE (counter_id, queue_id)
);

CREATE INDEX idx_cqa_counter_id ON counter_queue_assignments (counter_id);
CREATE INDEX idx_cqa_queue_id ON counter_queue_assignments (queue_id);

-- Operational fields for queues
ALTER TABLE queues ADD COLUMN IF NOT EXISTS capacity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE queues ADD COLUMN IF NOT EXISTS is_priority_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE queues ADD COLUMN IF NOT EXISTS default_service_time_minutes INTEGER NOT NULL DEFAULT 5;

-- Operational audit and priority fields for queue_tokens
ALTER TABLE queue_tokens ADD COLUMN IF NOT EXISTS priority VARCHAR(50) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE queue_tokens ADD COLUMN IF NOT EXISTS served_by_counter_id UUID REFERENCES counters(id) ON DELETE SET NULL;
ALTER TABLE queue_tokens ADD COLUMN IF NOT EXISTS served_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_queue_tokens_priority ON queue_tokens (priority);
CREATE INDEX idx_queue_tokens_served_counter ON queue_tokens (served_by_counter_id);
CREATE INDEX idx_queue_tokens_served_user ON queue_tokens (served_by_user_id);
