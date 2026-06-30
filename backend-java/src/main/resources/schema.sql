-- 生产/本地 MySQL 建表脚本（幂等）。
-- 由 spring.sql.init.mode=always 在应用启动时执行。
-- 全部使用 CREATE TABLE IF NOT EXISTS：已存在的表（含 Python 后端 SQLAlchemy 建的）不会被改动，仅补建缺失的表。
-- 切勿在此添加 DROP 语句，以免清空已有数据。

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY            COMMENT '用户ID',
    username      VARCHAR(64)  NOT NULL UNIQUE                 COMMENT '用户名（唯一）',
    email         VARCHAR(128) NOT NULL UNIQUE                 COMMENT '邮箱（唯一）',
    password_hash VARCHAR(256) NOT NULL                        COMMENT '密码哈希',
    role          SMALLINT DEFAULT 2                           COMMENT '角色：1=管理员 2=普通成员',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS projects (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY              COMMENT '项目ID',
    name        VARCHAR(256) NOT NULL                          COMMENT '项目名称',
    description TEXT                                           COMMENT '项目描述',
    owner_id    BIGINT NOT NULL                                COMMENT '所属用户ID',
    status      SMALLINT DEFAULT 1                             COMMENT '状态：1=草稿 2=进行中 3=已完成',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

CREATE TABLE IF NOT EXISTS project_tables (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY               COMMENT '主键ID',
    project_id BIGINT NOT NULL                                 COMMENT '项目ID',
    table_id   BIGINT NOT NULL                                 COMMENT '源表ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目-源表关联表';

CREATE TABLE IF NOT EXISTS source_tables (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY              COMMENT '源表ID',
    name        VARCHAR(128) NOT NULL                          COMMENT '表名',
    description TEXT                                           COMMENT '表描述',
    scope       SMALLINT DEFAULT 2                             COMMENT '可见范围：1=公开 2=私有',
    owner_id    BIGINT                                         COMMENT '所属用户ID（公开表可为空）',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='源表元信息表';

CREATE TABLE IF NOT EXISTS table_columns (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY              COMMENT '主键ID',
    table_id    BIGINT NOT NULL                                COMMENT '所属源表ID',
    column_name VARCHAR(128) NOT NULL                          COMMENT '列名',
    data_type   VARCHAR(64)  NOT NULL                          COMMENT '数据类型',
    comment     TEXT                                           COMMENT '列备注/字段中文名',
    sort_order  INT DEFAULT 0                                  COMMENT '排序序号/字段序号',
    col_length           INT                                   COMMENT '字段长度',
    col_precision        INT                                   COMMENT '字段精度',
    is_distribution_key  SMALLINT DEFAULT 0                     COMMENT '是否分布键 0/1',
    is_partition_key     SMALLINT DEFAULT 0                     COMMENT '是否分区键 0/1',
    is_primary_key       SMALLINT DEFAULT 0                     COMMENT '是否主键 0/1',
    is_nullable          SMALLINT DEFAULT 1                     COMMENT '是否可为空 0/1',
    code_info            VARCHAR(512)                           COMMENT '代码信息',
    default_value        VARCHAR(255)                           COMMENT '缺省值',
    downstream_job_count INT                                   COMMENT '下游作业数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='源表字段定义表';

CREATE TABLE IF NOT EXISTS conversations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY               COMMENT '会话ID',
    project_id BIGINT NOT NULL                                 COMMENT '所属项目ID',
    conv_name  VARCHAR(256)                                    COMMENT '会话名称',
    state      INT NOT NULL DEFAULT 1                          COMMENT '状态：1=信息收集 2=结构确认 3=步骤确认 4=生成中 5=完成',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP             COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

CREATE TABLE IF NOT EXISTS conversation_tables (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY          COMMENT '主键ID',
    conversation_id BIGINT NOT NULL                           COMMENT '会话ID',
    table_id        BIGINT NOT NULL                           COMMENT '源表ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话-源表关联表';

CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY          COMMENT '消息ID',
    conversation_id BIGINT NOT NULL                           COMMENT '所属会话ID',
    role            VARCHAR(20) NOT NULL                      COMMENT '角色：user/assistant/tool',
    content         TEXT NOT NULL                             COMMENT '消息内容',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话消息表';

CREATE TABLE IF NOT EXISTS etl_jobs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY          COMMENT 'ETL作业ID',
    project_id      BIGINT NOT NULL                            COMMENT '所属项目ID',
    conversation_id BIGINT                                     COMMENT '生成该作业的会话ID',
    target_table  VARCHAR(128) NOT NULL                        COMMENT '目标表名',
    target_schema TEXT                                         COMMENT '目标表结构（JSON数组）',
    plan_md_path  VARCHAR(512)                                 COMMENT '计划文档(Markdown)路径',
    plan_content  TEXT                                         COMMENT '计划文档(Markdown)内容',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL作业表';

CREATE TABLE IF NOT EXISTS etl_steps (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY            COMMENT '步骤ID',
    job_id        BIGINT NOT NULL                              COMMENT '所属ETL作业ID',
    step_order    INT NOT NULL                                 COMMENT '步骤顺序',
    step_name     VARCHAR(256) NOT NULL                        COMMENT '步骤名称',
    is_temp_table SMALLINT DEFAULT 0                           COMMENT '是否临时表：0=最终表 1=临时表',
    sql_file_path VARCHAR(512)                                 COMMENT 'SQL文件路径',
    sql_content   TEXT                                         COMMENT 'SQL文本内容'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL步骤表';

CREATE TABLE IF NOT EXISTS table_relation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY      COMMENT '主键ID',
    source_table_id BIGINT NOT NULL                        COMMENT '主表(source_tables.id)',
    target_table_id BIGINT NOT NULL                        COMMENT '关联表(source_tables.id)',
    relation_type   VARCHAR(32)                            COMMENT 'ONE_TO_ONE/ONE_TO_MANY/MANY_TO_ONE/MANY_TO_MANY',
    description     TEXT                                   COMMENT '关系说明',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原表关系';

CREATE TABLE IF NOT EXISTS table_relation_column (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY     COMMENT '主键ID',
    relation_id      BIGINT NOT NULL                       COMMENT 'table_relation.id',
    source_column_id BIGINT                                COMMENT '主表字段(table_columns.id)',
    target_column_id BIGINT                                COMMENT '关联表字段(table_columns.id)',
    sort_order       INT DEFAULT 0                          COMMENT '排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表关系字段对(支持复合键)';
