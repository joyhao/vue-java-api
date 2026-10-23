-- 用途：新增一百万条模拟用户，仅供测试库性能测试。
-- 前提：public."user" 已按 user.sql 创建，id 为 IDENTITY 自增主键。
-- 警告：会消耗大量磁盘、WAL、CPU 和 I/O，也可能造成复制延迟。
-- 执行前请预留表、索引以及 WAL、归档和备份所需的磁盘空间。
-- 不删除已有数据，不禁用约束，不分配角色或权限。
-- 每次完整执行都会额外新增一百万条，不是将表总量补齐至一百万条。
--
-- Navicat 执行要求：
-- 1. 确认连接的是测试库，启用自动提交，不能存在尚未结束的事务。
-- 2. 将下面整个 DO 语句作为一个顶层语句执行，不要包裹 BEGIN/COMMIT。
-- 3. 如果出现 invalid transaction termination，说明客户端使用了外层事务，
--    请先结束该事务并启用自动提交，不要直接删除循环内的 COMMIT。
-- 4. 每批提交 100000 条，共 10 批；可在消息面板查看每批的进度。
-- 5. 中途停止或失败时，已提交批次不会回滚；重跑会从新的批次重新新增，
--    本脚本不支持断点续传。取消时当前批次可能提交或回滚，须核实实际进度。
-- 6. 脚本本身不设置执行超时，执行前检查客户端及数据库的超时设置。

DO $seed_users$
DECLARE
  v_total CONSTANT bigint := 1000000;
  v_batch_size CONSTANT integer := 100000;
  v_run_id CONSTANT text := gen_random_uuid()::text;
  v_completed bigint := 0;
  v_rows bigint;
  v_started_at timestamptz := clock_timestamp();
BEGIN
  RAISE NOTICE '开始生成模拟用户，批次标识：%，目标：% 条', v_run_id, v_total;

  WHILE v_completed < v_total LOOP
    -- 省略 id，由数据库自动生成；手机号为空，避免唯一约束冲突。
    -- 邮箱使用保留示例域名；头像为空，不生成图片或外部网络请求。
    INSERT INTO "public"."user" (
      "name", "avatar_url", "email", "phone", "status", "created_at", "updated_at"
    )
    SELECT
      '压测用户_' || v_run_id || '_' || series.n::text,
      NULL,
      'load_' || v_run_id || '_' || series.n::text || '@example.com',
      NULL,
      1,
      CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP
    FROM generate_series(
      v_completed + 1,
      LEAST(v_completed + v_batch_size, v_total)
    ) AS series(n);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    v_completed := v_completed + v_rows;
    COMMIT;

    IF v_completed % v_batch_size = 0 OR v_completed = v_total THEN
      RAISE NOTICE '批次标识：%；已提交 % / % 条，耗时：%',
        v_run_id, v_completed, v_total, clock_timestamp() - v_started_at;
    END IF;
  END LOOP;

  RAISE NOTICE '完成：本次已提交 % 条模拟用户。建议随后执行 ANALYZE public."user" 更新统计信息。',
    v_completed;
END;
$seed_users$;

-- 插入完成后，可单独执行以下语句更新优化器统计信息：
-- ANALYZE "public"."user";
-- 不自动执行全表 COUNT(*)，避免插入后再次扫描海量数据。
