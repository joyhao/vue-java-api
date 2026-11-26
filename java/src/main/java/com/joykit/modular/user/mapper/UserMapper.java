package com.joykit.modular.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.joykit.modular.user.po.UserPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    /**
     * 删除任意一批行，返回本批删除的行数；表已删空时返回 0。
     * 哈希分区后 ctid 只在单个分区内唯一，不能再用 ctid 定位，改按 id 升序删。
     */
    @Delete("DELETE FROM public.\"user\" WHERE id IN (SELECT id FROM public.\"user\" ORDER BY id LIMIT #{batchSize})")
    int deleteChunk(@Param("batchSize") int batchSize);

    /**
     * 用统计信息估算表行数（ANALYZE 之后误差很小），纳秒级返回。
     * 上亿行时精确 COUNT(*) 要十几秒，列表页总数改用这个。
     * 返回负数表示统计信息不可用，调用方应退回精确计数。
     */
    @Select("SELECT reltuples::bigint FROM pg_class WHERE oid = ('public.user')::regclass")
    long estimateTotal();

    /**
     * 无关键字的列表取页（延迟关联）。
     * OFFSET 要逐行数过前面的行，直接取全列的话每一行都要回表。这里内层只沿主键索引数 id，
     * 外层再按 id 取当页的行，深分页时快约 1.8 倍。耗时仍随页深线性增长，这是 OFFSET 的固有代价。
     */
    @Select("""
            SELECT u.* FROM (
                SELECT id FROM public."user" ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            ) k JOIN public."user" u ON u.id = k.id
            ORDER BY u.id DESC
            """)
    List<UserPO> pageByOffset(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * 关键字搜索取页（关键词至少 3 个字符，才能用上 trigram 索引）。
     * 不能写成 ORDER BY id DESC LIMIT：优化器会高估命中率，选择按 id 顺序扫整表来凑够 LIMIT，
     * 稀有关键词要扫完上千万行。这里先用索引把全部命中 id 物化，再排序分页，最后按主键逐条回表。
     */
    @Select("""
            WITH hit AS MATERIALIZED (
                SELECT id FROM public."user"
                WHERE name ILIKE #{pattern} ESCAPE '!'
                   OR email ILIKE #{pattern} ESCAPE '!'
                   OR phone ILIKE #{pattern} ESCAPE '!'
            )
            SELECT u.* FROM (SELECT id FROM hit ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}) p
            CROSS JOIN LATERAL (SELECT * FROM public."user" u WHERE u.id = p.id) u
            ORDER BY u.id DESC
            """)
    List<UserPO> searchByKeyword(@Param("pattern") String pattern, @Param("offset") long offset,
            @Param("limit") int limit);

    /**
     * 关键字搜索计数，最多数到 limit 为止：高频词在上亿行时精确总数既慢又没有展示价值。
     */
    @Select("""
            SELECT count(*) FROM (
                SELECT 1 FROM public."user"
                WHERE name ILIKE #{pattern} ESCAPE '!'
                   OR email ILIKE #{pattern} ESCAPE '!'
                   OR phone ILIKE #{pattern} ESCAPE '!'
                LIMIT #{limit}
            ) t
            """)
    long countKeywordMatches(@Param("pattern") String pattern, @Param("limit") long limit);

    /**
     * 当前事务内的语句超时（毫秒），事务结束自动失效。
     * 用于 1~2 个字符的短关键词搜索：trigram 索引用不上，只能全表扫描，超时兜底防止拖垮数据库。
     * SET 语句不接受绑定参数，所以用 set_config 的第三个参数 true 表示仅当前事务。
     */
    @Select("SELECT set_config('statement_timeout', #{millis}::text, true)")
    String setStatementTimeout(@Param("millis") int millis);

    /** 仅在当前事务内生效的参数设置（第三个参数 true 等价于 SET LOCAL），事务结束自动失效 */
    @Select("SELECT set_config(#{name}, #{value}, true)")
    String setLocal(@Param("name") String name, @Param("value") String value);
}
