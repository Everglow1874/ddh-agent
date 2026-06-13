DROP TABLE IF EXISTS etl_steps;
DROP TABLE IF EXISTS etl_jobs;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS conversation_tables;
DROP TABLE IF EXISTS conversations;
DROP TABLE IF EXISTS table_columns;
DROP TABLE IF EXISTS source_tables;
DROP TABLE IF EXISTS project_tables;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL UNIQUE,
    email      VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role       SMALLINT DEFAULT 2,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE projects (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(256) NOT NULL,
    description TEXT,
    owner_id    BIGINT NOT NULL,
    status      SMALLINT DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_tables (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    table_id   BIGINT NOT NULL
);

CREATE TABLE source_tables (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    scope       SMALLINT DEFAULT 2,
    owner_id    BIGINT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE table_columns (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id    BIGINT NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    data_type   VARCHAR(64)  NOT NULL,
    comment     TEXT,
    sort_order  INT DEFAULT 0
);

CREATE TABLE conversations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    state      INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conversation_tables (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    table_id        BIGINT NOT NULL
);

CREATE TABLE messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE etl_jobs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT NOT NULL,
    target_table  VARCHAR(128) NOT NULL,
    target_schema TEXT,
    plan_md_path  VARCHAR(512),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE etl_steps (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id        BIGINT NOT NULL,
    step_order    INT NOT NULL,
    step_name     VARCHAR(256) NOT NULL,
    is_temp_table SMALLINT DEFAULT 0,
    sql_file_path VARCHAR(512)
);
