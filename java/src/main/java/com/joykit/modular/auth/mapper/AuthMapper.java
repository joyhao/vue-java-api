package com.joykit.modular.auth.mapper;

import com.joykit.modular.user.po.UserPO;
import org.apache.ibatis.annotations.*;
import java.time.Instant;

@Mapper
public interface AuthMapper {
    @Select("SELECT * FROM public.\"user\" WHERE lower(account)=lower(#{account})")
    UserPO findByAccount(@Param("account") String account);

    @Select("SELECT * FROM public.\"user\" WHERE id=#{id} AND status=1")
    UserPO findEnabledUser(@Param("id") long id);

    @Update("UPDATE public.\"user\" SET last_login_at=#{time} WHERE id=#{id}")
    void updateLastLogin(@Param("id") long id, @Param("time") Instant time);
}
