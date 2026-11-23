ALTER TABLE public.login_session ADD COLUMN refresh_hash VARCHAR(64) UNIQUE,
    ADD COLUMN refresh_expires_at TIMESTAMPTZ;
COMMENT ON COLUMN public.login_session.refresh_hash IS '刷新凭证SHA-256哈希，每次刷新轮换；旧会话需重新登录';
COMMENT ON COLUMN public.login_session.refresh_expires_at IS '刷新凭证绝对截止时间，登录后7天，刷新不延长';
