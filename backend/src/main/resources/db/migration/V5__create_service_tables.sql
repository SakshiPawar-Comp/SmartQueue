-- Flyway Migration V5: Create services table and link branch/service to queues for Phase 3 Customer Discovery

CREATE TABLE services (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_services_organization_id ON services (organization_id);
CREATE INDEX idx_services_branch_id ON services (branch_id);
CREATE INDEX idx_services_status ON services (status);

-- Add branch_id and service_id to queues table
ALTER TABLE queues ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;
ALTER TABLE queues ADD COLUMN IF NOT EXISTS service_id UUID REFERENCES services(id) ON DELETE SET NULL;

CREATE INDEX idx_queues_branch_id ON queues (branch_id);
CREATE INDEX idx_queues_service_id ON queues (service_id);
