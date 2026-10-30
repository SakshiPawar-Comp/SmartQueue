-- Flyway Migration V8: Add Performance Indexes for Analytics and Reporting

CREATE INDEX idx_queue_tokens_analytics_queue ON queue_tokens(queue_id, created_at, status);
CREATE INDEX idx_queue_tokens_analytics_counter ON queue_tokens(served_by_counter_id, created_at);
CREATE INDEX idx_queue_tokens_analytics_user ON queue_tokens(served_by_user_id, created_at);
CREATE INDEX idx_queues_analytics_org_branch ON queues(organization_id, branch_id, service_id);
