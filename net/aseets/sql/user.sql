/*
 Navicat Premium Dump SQL

 Source Server         : pgsql
 Source Server Type    : PostgreSQL
 Source Server Version : 180001 (180001)
 Source Host           : localhost:9006
 Source Catalog        : postgres
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 180001 (180001)
 File Encoding         : 65001

 Date: 23/10/2026 11:21:29
*/


-- 新建表脚本：适用于 public."user" 尚不存在的数据库。
-- 已有表请使用单独的 ALTER TABLE 迁移脚本，不要删除原表后执行。
BEGIN;

-- user 使用双引号，避免与 PostgreSQL 的 USER 关键字混淆。
-- IDENTITY 自动生成主键；新增记录时通常应省略 id 字段。
CREATE TABLE "public"."user" (
  "id" bigint GENERATED ALWAYS AS IDENTITY,
  "name" varchar(255) NOT NULL,
  "avatar_url" text,
  "email" varchar(254),
  "phone" varchar(32),
  "status" smallint NOT NULL DEFAULT 1,
  "created_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- 主键保证用户 ID 唯一且非空。
  CONSTRAINT "user_pkey" PRIMARY KEY ("id"),
  -- btrim 去除首尾普通空格；禁止空字符串和全部由普通空格组成的姓名。
  CONSTRAINT "user_name_not_blank" CHECK (char_length(btrim("name")) > 0),
  -- 邮箱允许 NULL，表示未填写。
  -- 非 NULL 时，不允许首尾普通空格，也不允许空字符串。
  -- 此约束不校验邮箱格式：例如 abc 仍可通过；邮箱格式由应用层校验。
  CONSTRAINT "user_email_not_blank" CHECK (
    "email" IS NULL OR ("email" = btrim("email") AND char_length("email") > 0)
  ),
  -- 手机号允许 NULL；填写时不能是空字符串或带首尾普通空格。
  -- 此处不限制国家区号、长度或号码格式，由应用层统一校验。
  CONSTRAINT "user_phone_not_blank" CHECK (
    "phone" IS NULL OR ("phone" = btrim("phone") AND char_length("phone") > 0)
  ),
  -- 非 NULL 手机号必须唯一；默认允许多条记录的手机号为 NULL。
  CONSTRAINT "user_phone_key" UNIQUE ("phone"),
  -- 只允许两种状态：0=禁用，1=正常。
  CONSTRAINT "user_status_check" CHECK ("status" IN (0, 1))
);

-- DEFAULT 只在插入且未指定字段值时生效，不会自动维护更新时的时间。
-- 因此通过触发器函数，在每次 UPDATE 时覆盖 updated_at 为实际当前时间。
-- created_at 保持原值；即使 UPDATE 未改变业务字段，也会刷新 updated_at。
CREATE FUNCTION "public"."user_set_updated_at"()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW."updated_at" := clock_timestamp();
  RETURN NEW;
END;
$$;

CREATE TRIGGER "user_before_update"
BEFORE UPDATE ON "public"."user"
FOR EACH ROW
EXECUTE FUNCTION "public"."user_set_updated_at"();

COMMENT ON TABLE "public"."user" IS '用户基础信息';
COMMENT ON COLUMN "public"."user"."id" IS '用户ID，由数据库自动生成';
COMMENT ON COLUMN "public"."user"."name" IS '用户姓名或昵称，不要求唯一';
COMMENT ON COLUMN "public"."user"."avatar_url" IS '头像图片 URL 或相对路径，可为空；不存储图片二进制内容';
COMMENT ON COLUMN "public"."user"."email" IS '邮箱，可为空，允许重复；格式由应用校验';
COMMENT ON COLUMN "public"."user"."phone" IS '手机号，可为空，非空时唯一；国家区号与格式由应用统一';
COMMENT ON COLUMN "public"."user"."status" IS '用户状态：0=禁用，1=正常';
COMMENT ON COLUMN "public"."user"."created_at" IS '创建时间，含时区';
COMMENT ON COLUMN "public"."user"."updated_at" IS '最后更新时间，由更新触发器维护';

COMMIT;
