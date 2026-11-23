COMMENT ON COLUMN public.login_session.user_id IS '用户ID，关联user表主键；与client_type组成联合主键';
COMMENT ON COLUMN public.login_session.client_type IS '客户端类型：WEB=网页端，APP=应用端；同一用户每端仅保留一个会话';
COMMENT ON COLUMN public.login_session.token_hash IS '登录凭证的SHA-256哈希（64位十六进制），不保存原始Token';
COMMENT ON COLUMN public.login_session.created_at IS '当前会话创建时间，同端重新登录时更新';
COMMENT ON COLUMN public.login_session.expires_at IS '会话过期时间，超过此时间登录凭证失效';
