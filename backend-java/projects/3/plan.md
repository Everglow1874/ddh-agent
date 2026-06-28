# ETL 执行计划

## 需求描述

查询新的那场考试，各科最高平均分的班级

## 目标表

`exam_highest_avg_class`

## ETL 步骤

### Step 1: 创建目标表

创建目标表

输出表：`exam_highest_avg_class`

### Step 2: 创建临时表：最新考试（临时表）

创建临时表：最新考试

输出表：`exam_highest_avg_class`

### Step 3: 创建临时表：班级科目平均分（临时表）

创建临时表：班级科目平均分

输出表：`exam_highest_avg_class`

### Step 4: 写入目标表：各科最高平均分班级

写入目标表：各科最高平均分班级

输出表：`exam_highest_avg_class`

