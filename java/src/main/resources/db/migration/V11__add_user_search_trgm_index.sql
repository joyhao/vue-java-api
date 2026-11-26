-- 用户列表关键字搜索（姓名 / 邮箱 / 电话的 ILIKE '%x%'）使用三元组索引。
-- 限制：关键字不足 3 个字符时拆不出三元组，用不上本索引，仍是全表扫描。
-- CREATE EXTENSION 需要超级用户或数据库所有者权限。
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_user_search_trgm
    ON public."user" USING gin (name gin_trgm_ops, email gin_trgm_ops, phone gin_trgm_ops);
