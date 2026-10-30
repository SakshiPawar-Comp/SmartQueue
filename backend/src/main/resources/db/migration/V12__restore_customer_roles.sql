-- Flyway Migration V12: Restore CUSTOMER role for users without any assigned role
INSERT INTO user_roles (id, user_id, role_id, organization_id)
SELECT 
    RANDOM_UUID(),
    u.id,
    '55555555-5555-5555-5555-555555555555',
    'c91b7ba0-e325-4f06-bb51-e37990c46cbe'
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
WHERE ur.id IS NULL;

