using joykit.Domain.Users;

namespace joykit.Application.Users;

public interface IUserRepository
{
    Task<(IReadOnlyList<User> Items, long Total)> GetPageAsync(UserPageQuery query, CancellationToken ct);
    Task<User> CreateAsync(UserWriteData request, CancellationToken ct);
    Task<User?> UpdateAsync(long id, UserWriteData request, CancellationToken ct);
    Task<long> DeleteAsync(long[] ids, CancellationToken ct);
    Task<long> DeleteAllAsync(CancellationToken ct);
}
