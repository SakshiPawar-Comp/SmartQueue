-- Flyway Migration V11: Remove All Demo and Dummy Data from Database

-- 1. Remove token documents linked to demo tokens
DELETE FROM token_documents
WHERE token_id IN (
    SELECT qt.id FROM queue_tokens qt
    JOIN queues q ON qt.queue_id = q.id
    JOIN organizations o ON q.organization_id = o.id
    WHERE o.slug = 'citycare'
);

-- 2. Remove notifications associated with demo organization or demo users
DELETE FROM notifications
WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare')
   OR user_id IN (SELECT id FROM users WHERE email LIKE '%@citycare.demo' OR email LIKE '%@customer.demo');

-- 3. Remove notification preferences for demo users
DELETE FROM user_notification_preferences
WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@citycare.demo' OR email LIKE '%@customer.demo');

-- 4. Remove staff counter assignments for demo organization or demo users
DELETE FROM staff_counter_assignments
WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare')
   OR user_id IN (SELECT id FROM users WHERE email LIKE '%@citycare.demo' OR email LIKE '%@customer.demo');

-- 5. Remove counter-queue assignments for demo counters or queues
DELETE FROM counter_queue_assignments
WHERE counter_id IN (SELECT id FROM counters WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare'))
   OR queue_id IN (SELECT id FROM queues WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare'));

-- 6. Remove counters for demo organization
DELETE FROM counters
WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare');

-- 7. Remove tokens for demo queues or demo customers
DELETE FROM queue_tokens
WHERE queue_id IN (SELECT id FROM queues WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare'))
   OR customer_id IN (SELECT id FROM users WHERE email LIKE '%@citycare.demo' OR email LIKE '%@customer.demo');

-- 8. Remove queues for demo organization
DELETE FROM queues
WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare');

-- 9. Remove services for demo organization
DELETE FROM services
WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare');

-- 10. Remove branches for demo organization
DELETE FROM branches
WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare');

-- 11. Remove refresh tokens for demo users
DELETE FROM refresh_tokens
WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@citycare.demo' OR email LIKE '%@customer.demo');

-- 12. Remove user roles for demo organization or demo users
DELETE FROM user_roles
WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'citycare')
   OR user_id IN (SELECT id FROM users WHERE email LIKE '%@citycare.demo' OR email LIKE '%@customer.demo');

-- 13. Remove demo users
DELETE FROM users
WHERE email LIKE '%@citycare.demo' OR email LIKE '%@customer.demo';

-- 14. Remove demo organization
DELETE FROM organizations
WHERE slug = 'citycare';

