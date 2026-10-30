-- Flyway Migration V10: Add AI Document Verification Facility

-- Add document verification requirements to services table
ALTER TABLE services ADD COLUMN IF NOT EXISTS requires_document_verification BOOLEAN DEFAULT FALSE;
ALTER TABLE services ADD COLUMN IF NOT EXISTS required_document_types VARCHAR(255) DEFAULT 'GOVERNMENT_ID';

-- Add document verification requirements to queues table
ALTER TABLE queues ADD COLUMN IF NOT EXISTS requires_document_verification BOOLEAN DEFAULT FALSE;

-- Table to store uploaded documents for tokens and their AI verification records
CREATE TABLE token_documents (
    id UUID PRIMARY KEY,
    token_id UUID NOT NULL REFERENCES queue_tokens(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_content_type VARCHAR(100),
    file_data TEXT,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ai_confidence DOUBLE PRECISION,
    ai_model_version VARCHAR(50),
    extracted_data TEXT,
    failure_reason TEXT,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_token_documents_token_id ON token_documents (token_id);
CREATE INDEX idx_token_documents_verification_status ON token_documents (verification_status);
