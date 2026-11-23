ALTER TABLE public."user" ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE public."user" ALTER COLUMN id DROP DEFAULT;
COMMENT ON COLUMN public."user".id IS '用户主键，由应用使用MyBatis-Plus雪花算法生成；数据库不自增';
