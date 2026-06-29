package com.ddh.agent.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class DatabaseMigration {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    @Autowired private DataSource dataSource;

    @PostConstruct
    void migrate() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            addColumnIfMissing(meta, conn, "etl_jobs", "plan_content",
                "ALTER TABLE etl_jobs ADD COLUMN plan_content TEXT AFTER plan_md_path");
            addColumnIfMissing(meta, conn, "etl_steps", "sql_content",
                "ALTER TABLE etl_steps ADD COLUMN sql_content TEXT AFTER sql_file_path");
        } catch (Exception e) {
            log.warn("Database migration failed, skipping: {}", e.getMessage());
        }
    }

    private void addColumnIfMissing(DatabaseMetaData meta, Connection conn,
                                    String table, String column, String ddl) {
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            if (!rs.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(ddl);
                    log.info("Added column {}.{}", table, column);
                }
            }
        } catch (Exception e) {
            log.warn("Could not add column {}.{}: {}", table, column, e.getMessage());
        }
    }
}
