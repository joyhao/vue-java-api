CREATE TABLE public.login_session (
    user_id BIGINT NOT NULL REFERENCES public."user"(id) ON DELETE CASCADE,
    client_type VARCHAR(3) NOT NULL CHECK (client_type IN ('WEB', 'APP')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, client_type)
);
CREATE INDEX login_session_expires_at_idx ON public.login_session(expires_at);
COMMENT ON TABLE public.login_session IS '每个用户WEB和APP各保留一个有效会话，新登录原子替换同端旧会话';
