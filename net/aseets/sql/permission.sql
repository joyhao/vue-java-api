-- 权限表，可独立创建；仅用于新建，不会自动授予权限。
BEGIN;

CREATE TABLE "public"."permission" (
  "id" bigint GENERATED ALWAYS AS IDENTITY,
  "code" varchar(128) NOT NULL,
  "name" varchar(100) NOT NULL,
  "description" text,
  "status" smallint NOT NULL DEFAULT 1,
  "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "permission_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "permission_code_key" UNIQUE ("code"),
  -- 权限编码使用小写分段格式，例如 user:read、user:create。
  CONSTRAINT "permission_code_check" CHECK ("code" ~ '^[a-z][a-z0-9_]*(:[a-z][a-z0-9_]*)*$'),
  CONSTRAINT "permission_name_not_blank" CHECK (char_length(btrim("name")) > 0),
  CONSTRAINT "permission_status_check" CHECK ("status" IN (0, 1))
);

-- 使用本表专属函数，不依赖用户表脚本。
CREATE FUNCTION "public"."permission_set_updated_at"()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW."updated_at" := clock_timestamp();
  RETURN NEW;
END;
$$;

CREATE TRIGGER "permission_before_update"
BEFORE UPDATE ON "public"."permission"
FOR EACH ROW
EXECUTE FUNCTION "public"."permission_set_updated_at"();

COMMENT ON TABLE "public"."permission" IS '权限定义';
COMMENT ON COLUMN "public"."permission"."id" IS '权限ID，自动生成';
COMMENT ON COLUMN "public"."permission"."code" IS '唯一权限编码，例如 user:read、user:create';
COMMENT ON COLUMN "public"."permission"."name" IS '权限显示名称';
COMMENT ON COLUMN "public"."permission"."description" IS '权限说明，可为空';
COMMENT ON COLUMN "public"."permission"."status" IS '权限状态：0=禁用，1=正常';
COMMENT ON COLUMN "public"."permission"."created_at" IS '创建时间，含时区';
COMMENT ON COLUMN "public"."permission"."updated_at" IS '最后更新时间，由触发器维护';

-- 后端须按权限编码实际鉴权，并排除禁用的用户、角色和权限。
COMMIT;
