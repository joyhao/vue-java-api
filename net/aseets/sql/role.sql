-- 角色表，可独立创建；仅用于新建，不会自动分配角色。
BEGIN;

CREATE TABLE "public"."role" (
  "id" bigint GENERATED ALWAYS AS IDENTITY,
  "code" varchar(64) NOT NULL,
  "name" varchar(100) NOT NULL,
  "description" text,
  "status" smallint NOT NULL DEFAULT 1,
  "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "role_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "role_code_key" UNIQUE ("code"),
  -- 角色编码统一使用小写，例如 admin、user_manager。
  CONSTRAINT "role_code_check" CHECK ("code" ~ '^[a-z][a-z0-9_]*$'),
  CONSTRAINT "role_name_not_blank" CHECK (char_length(btrim("name")) > 0),
  CONSTRAINT "role_status_check" CHECK ("status" IN (0, 1))
);

-- 使用本表专属函数，不依赖用户表脚本。
CREATE FUNCTION "public"."role_set_updated_at"()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW."updated_at" := clock_timestamp();
  RETURN NEW;
END;
$$;

CREATE TRIGGER "role_before_update"
BEFORE UPDATE ON "public"."role"
FOR EACH ROW
EXECUTE FUNCTION "public"."role_set_updated_at"();

COMMENT ON TABLE "public"."role" IS '角色定义';
COMMENT ON COLUMN "public"."role"."id" IS '角色ID，自动生成';
COMMENT ON COLUMN "public"."role"."code" IS '唯一角色编码，例如 admin、user_manager';
COMMENT ON COLUMN "public"."role"."name" IS '角色显示名称';
COMMENT ON COLUMN "public"."role"."description" IS '角色说明，可为空';
COMMENT ON COLUMN "public"."role"."status" IS '角色状态：0=禁用，1=正常';
COMMENT ON COLUMN "public"."role"."created_at" IS '创建时间，含时区';
COMMENT ON COLUMN "public"."role"."updated_at" IS '最后更新时间，由触发器维护';

-- 后端鉴权时必须排除禁用角色，status 本身不会阻止 SQL 访问。
COMMIT;
