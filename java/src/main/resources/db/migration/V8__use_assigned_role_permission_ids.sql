ALTER TABLE public.role ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE public.role ALTER COLUMN id DROP DEFAULT;
COMMENT ON COLUMN public.role.id IS '角色主键，由应用生成雪花ID；数据库不自增';

ALTER TABLE public.permission ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE public.permission ALTER COLUMN id DROP DEFAULT;
COMMENT ON COLUMN public.permission.id IS '权限主键，由应用生成雪花ID；数据库不自增';
