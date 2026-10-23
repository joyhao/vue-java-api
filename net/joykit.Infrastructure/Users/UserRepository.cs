using System.Data;
using joykit.Application.Users;
using joykit.Domain.Users;
using Npgsql;
using NpgsqlTypes;

namespace joykit.Infrastructure.Users;

public sealed class UserRepository(NpgsqlDataSource dataSource) : IUserRepository
{
    private const string Columns = "id, name, avatar_url, email, phone, status, created_at, updated_at";

    public async Task<(IReadOnlyList<User> Items, long Total)> GetPageAsync(UserQuery query, CancellationToken ct)
    {
        await using var connection = await dataSource.OpenConnectionAsync(ct);
        await using var transaction = await connection.BeginTransactionAsync(IsolationLevel.RepeatableRead, ct);
        const string filter = " WHERE (@keyword IS NULL OR name ILIKE @keyword OR email ILIKE @keyword OR phone ILIKE @keyword)";
        var keyword = query.Keyword is null ? null : "%" + query.Keyword.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_") + "%";
        await using var count = new NpgsqlCommand("SELECT count(*) FROM public.\"user\"" + filter, connection, transaction);
        count.Parameters.AddWithValue("keyword", NpgsqlDbType.Text, (object?)keyword ?? DBNull.Value);
        var total = (long)(await count.ExecuteScalarAsync(ct))!;
        await using var command = new NpgsqlCommand($"SELECT {Columns} FROM public.\"user\"{filter} ORDER BY id DESC LIMIT @limit OFFSET @offset", connection, transaction);
        command.Parameters.AddWithValue("keyword", NpgsqlDbType.Text, (object?)keyword ?? DBNull.Value);
        command.Parameters.AddWithValue("limit", query.PageSize);
        command.Parameters.AddWithValue("offset", ((long)query.PageNumber - 1) * query.PageSize);
        var users = new List<User>();
        await using (var reader = await command.ExecuteReaderAsync(ct))
        {
            while (await reader.ReadAsync(ct)) users.Add(Read(reader));
        }
        await transaction.CommitAsync(ct);
        return (users, total);
    }

    public Task<User> CreateAsync(SaveUserRequest request, CancellationToken ct) =>
        SaveNewAsync(request, ct);

    private async Task<User> SaveNewAsync(SaveUserRequest request, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand($"INSERT INTO public.\"user\" (name, avatar_url, email, phone, status) VALUES (@name, @avatar, @email, @phone, @status) RETURNING {Columns}");
        AddFields(command, request);
        return (await SaveAsync(command, ct))!;
    }

    public async Task<User?> UpdateAsync(long id, SaveUserRequest request, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand($"UPDATE public.\"user\" SET name=@name, avatar_url=@avatar, email=@email, phone=@phone, status=@status, updated_at=clock_timestamp() WHERE id=@id RETURNING {Columns}");
        command.Parameters.AddWithValue("id", id);
        AddFields(command, request);
        return await SaveAsync(command, ct);
    }

    public async Task<long> DeleteAsync(long[] ids, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("WITH deleted AS (DELETE FROM public.\"user\" WHERE id=ANY(@ids) RETURNING 1) SELECT count(*) FROM deleted");
        command.Parameters.AddWithValue("ids", ids);
        return (long)(await command.ExecuteScalarAsync(ct))!;
    }

    public async Task<long> DeleteAllAsync(CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("WITH deleted AS (DELETE FROM public.\"user\" RETURNING 1) SELECT count(*) FROM deleted");
        return (long)(await command.ExecuteScalarAsync(ct))!;
    }

    private static async Task<User?> SaveAsync(NpgsqlCommand command, CancellationToken ct)
    {
        try
        {
            await using var reader = await command.ExecuteReaderAsync(ct);
            return await reader.ReadAsync(ct) ? Read(reader) : null;
        }
        catch (PostgresException exception) when (exception.SqlState == PostgresErrorCodes.UniqueViolation)
        {
            throw new UserConflictException("手机号已被其他用户使用。");
        }
    }

    private static void AddFields(NpgsqlCommand command, SaveUserRequest request)
    {
        command.Parameters.AddWithValue("name", request.Name);
        command.Parameters.AddWithValue("avatar", NpgsqlDbType.Text, (object?)request.AvatarUrl ?? DBNull.Value);
        command.Parameters.AddWithValue("email", NpgsqlDbType.Text, (object?)request.Email ?? DBNull.Value);
        command.Parameters.AddWithValue("phone", NpgsqlDbType.Text, (object?)request.Phone ?? DBNull.Value);
        command.Parameters.AddWithValue("status", request.Status);
    }

    private static User Read(NpgsqlDataReader reader) => new(reader.GetInt64(0), reader.GetString(1),
        reader.IsDBNull(2) ? null : reader.GetString(2), reader.IsDBNull(3) ? null : reader.GetString(3),
        reader.IsDBNull(4) ? null : reader.GetString(4), reader.GetInt16(5), reader.GetDateTime(6), reader.GetDateTime(7));
}
