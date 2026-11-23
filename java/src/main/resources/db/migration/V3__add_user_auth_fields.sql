ALTER TABLE public."user"
    ADD COLUMN account VARCHAR(100),
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN last_login_at TIMESTAMPTZ,
    ADD CONSTRAINT user_account_not_blank CHECK (
        account IS NULL OR (account = btrim(account) AND length(account) > 0)
    ),
    ADD CONSTRAINT user_password_hash_not_blank CHECK (
        password_hash IS NULL OR length(btrim(password_hash)) > 0
    );

CREATE UNIQUE INDEX user_account_unique ON public."user" (lower(account))
    WHERE account IS NOT NULL;

COMMENT ON COLUMN public."user".account IS '登录账号，忽略大小写唯一；NULL 表示尚未配置账号';
COMMENT ON COLUMN public."user".password_hash IS '密码哈希，禁止存储明文密码；NULL 表示尚未配置密码';
COMMENT ON COLUMN public."user".last_login_at IS '最后一次成功登录时间';
