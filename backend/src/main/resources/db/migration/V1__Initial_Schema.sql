CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE branches (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    address TEXT,
    status VARCHAR(50) NOT NULL,
    UNIQUE(organization_id, code)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    preferred_language VARCHAR(10),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE user_roles (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    organization_id UUID REFERENCES organizations(id),
    UNIQUE (user_id, role_id, organization_id)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Insert initial roles
INSERT INTO roles (id, code, name) VALUES 
('11111111-1111-1111-1111-111111111111', 'SUPER_ADMIN', 'Super Admin'),
('22222222-2222-2222-2222-222222222222', 'ORGANIZATION_ADMIN', 'Organization Admin'),
('33333333-3333-3333-3333-333333333333', 'BRANCH_ADMIN', 'Branch Admin'),
('44444444-4444-4444-4444-444444444444', 'STAFF', 'Staff'),
('55555555-5555-5555-5555-555555555555', 'CUSTOMER', 'Customer');
