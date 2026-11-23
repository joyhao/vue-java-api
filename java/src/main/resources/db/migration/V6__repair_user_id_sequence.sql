LOCK TABLE public."user" IN SHARE ROW EXCLUSIVE MODE;
SELECT setval(
    pg_get_serial_sequence('public."user"', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM public."user"), 0) + 1,
             nextval(pg_get_serial_sequence('public."user"', 'id'))),
    false
);
