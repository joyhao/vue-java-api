-- 用户角色关联表；执行前须已存在 public."user" 和 public."role"。
-- 仅用于新建；不会自动分配任何角色。
BEGIN;

-- 联合主键防止重复分配角色，同时支持按 user_id 查询。
-- 删除用户时清理关联；仍被分配的角色不允许直接删除，须先解除关联。
CREATE TABLE "public"."user_role" (
  "user_id" bigint NOT NULL,
  "role_id" bigint NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "user_role_pkey" PRIMARY KEY ("user_id", "role_id"),
  CONSTRAINT "user_role_user_fkey" FOREIGN KEY ("user_id")
    REFERENCES "public"."user" ("id") ON DELETE CASCADE,
  CONSTRAINT "user_role_role_fkey" FOREIGN KEY ("role_id")
    REFERENCES "public"."role" ("id") ON DELETE RESTRICT
);

-- 外键不会自动创建引用列索引；补充按角色查用户及外键检查所需索引。
CREATE INDEX "user_role_role_id_idx"
  ON "public"."user_role" ("role_id");

COMMENT ON TABLE "public"."user_role" IS '用户与角色的多对多关联';
COMMENT ON COLUMN "public"."user_role"."user_id" IS '关联用户ID';
COMMENT ON COLUMN "public"."user_role"."role_id" IS '关联角色ID';
COMMENT ON COLUMN "public"."user_role"."created_at" IS '角色分配时间，含时区';

COMMIT;
