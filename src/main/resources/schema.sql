CREATE TABLE IF NOT EXISTS tasks (
    id          VARCHAR(36) PRIMARY KEY,
    teacher_id  VARCHAR(64) NOT NULL,
    content     TEXT NOT NULL,
    intent      VARCHAR(32),
    agent_id    VARCHAR(32),
    risk        VARCHAR(16),
    response    MEDIUMTEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_status (status)
);
