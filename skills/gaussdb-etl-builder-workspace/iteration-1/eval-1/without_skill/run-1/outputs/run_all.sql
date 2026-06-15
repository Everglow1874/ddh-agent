-- =============================================================
-- 编排脚本：按顺序执行用户月度消费宽表 ETL
-- 用法：gsql -d <db> -h <host> -p <port> -U <user> -f run_all.sql
-- =============================================================

\echo '== Step 0: create target & dwd tables =='
\i 00_create_target_table.sql

\echo '== Step 1: clean orders -> dwd =='
\i 01_stage_clean_orders.sql

\echo '== Step 2: build monthly wide table =='
\i 02_build_monthly_wide.sql

\echo '== Step 3: data quality checks =='
\i 03_data_quality_checks.sql

\echo '== ETL finished =='
