-- 角色权限关联表；执行前须已存在 public."role" 和 public."permission"。
-- 仅用于新建；不会自动向任何角色授予权限。
BEGIN;

-- 联合主键防止重复授权，同时支持按 role_id 查询。
-- 删除角色时清理关联；仍被引用的权限不允许直接删除，须先解除关联。
CREATE TABLE "public"."role_permission" (
  "role_id" bigint NOT NULL,
  "permission_id" bigint NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "role_permission_pkey" PRIMARY KEY ("role_id", "permission_id"),
  CONSTRAINT "role_permission_role_fkey" FOREIGN KEY ("role_id")
    REFERENCES "public"."role" ("id") ON DELETE CASCADE,
  CONSTRAINT "role_permission_permission_fkey" FOREIGN KEY ("permission_id")
    REFERENCES "public"."permission" ("id") ON DELETE RESTRICT
);

-- 支持按权限反查角色，并加速相关外键检查。
CREATE INDEX "role_permission_permission_id_idx"
  ON "public"."role_permission" ("permission_id");

COMMENT ON TABLE "public"."role_permission" IS '角色与权限的多对多关联';
COMMENT ON COLUMN "public"."role_permission"."role_id" IS '关联角色ID';
COMMENT ON COLUMN "public"."role_permission"."permission_id" IS '关联权限ID';
COMMENT ON COLUMN "public"."role_permission"."created_at" IS '权限授予时间，含时区';

COMMIT;
