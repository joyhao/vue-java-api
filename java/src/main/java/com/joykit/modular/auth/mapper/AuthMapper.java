package com.joykit.modular.auth.mapper;

import com.joykit.modular.user.po.UserPO;
import org.apache.ibatis.annotations.*;
import java.time.Instant;

@Mapper
public interface AuthMapper {
    @Select("SELECT * FROM public.\"user\" WHERE lower(account)=lower(#{account})")
    UserPO findByAccount(@Param("account") String account);

    @Insert("INSERT INTO public.login_session(user_id,client_type,token_hash,created_at,expires_at) VALUES (#{userId},#{clientType},#{hash},#{created},#{expires}) ON CONFLICT (user_id,client_type) DO UPDATE SET token_hash=EXCLUDED.token_hash,created_at=EXCLUDED.created_at,expires_at=EXCLUDED.expires_at")
    void saveSession(@Param("userId") long userId, @Param("clientType") String clientType,
                     @Param("hash") String hash, @Param("created") Instant created, @Param("expires") Instant expires);

    @Update("UPDATE public.login_session SET refresh_hash=#{refreshHash},refresh_expires_at=#{expires} WHERE token_hash=#{hash}")
    void setRefresh(@Param("hash") String hash, @Param("refreshHash") String refreshHash, @Param("expires") Instant expires);

    record RefreshSession(long userId, String clientType, Instant expires) {}

    @Select("SELECT user_id,client_type,refresh_expires_at AS expires FROM public.login_session WHERE refresh_hash=#{hash} AND refresh_expires_at>CURRENT_TIMESTAMP FOR UPDATE")
    RefreshSession lockRefresh(@Param("hash") String hash);

    @Select("SELECT * FROM public.\"user\" WHERE id=#{id} AND status=1")
    UserPO findEnabledUser(@Param("id") long id);

    @Update("UPDATE public.login_session SET token_hash=#{access},expires_at=#{expires},refresh_hash=#{next} WHERE refresh_hash=#{previous}")
    int rotate(@Param("previous") String previous, @Param("access") String access, @Param("next") String next, @Param("expires") Instant expires);

    @Delete("DELETE FROM public.login_session WHERE refresh_hash=#{hash}")
    void deleteRefresh(@Param("hash") String hash);

    @Update("UPDATE public.\"user\" SET last_login_at=#{time} WHERE id=#{id}")
    void updateLastLogin(@Param("id") long id, @Param("time") Instant time);

    @Select("SELECT u.* FROM public.login_session s JOIN public.\"user\" u ON u.id=s.user_id WHERE s.token_hash=#{hash} AND s.expires_at>CURRENT_TIMESTAMP AND u.status=1")
    UserPO findByTokenHash(@Param("hash") String hash);

    @Select("SELECT client_type FROM public.login_session WHERE token_hash=#{hash}")
    String findClientType(@Param("hash") String hash);

    @Delete("DELETE FROM public.login_session WHERE token_hash=#{hash}")
    void deleteSession(@Param("hash") String hash);
}
