using joykit.Domain.Users;

namespace joykit.Application.Users;

public interface IUserRepository
{
    Task<(IReadOnlyList<User> Items, long Total)> GetPageAsync(UserQuery query, CancellationToken ct);
    Task<User> CreateAsync(SaveUserRequest request, CancellationToken ct);
    Task<User?> UpdateAsync(long id, SaveUserRequest request, CancellationToken ct);
    Task<long> DeleteAsync(long[] ids, CancellationToken ct);
    Task<long> DeleteAllAsync(CancellationToken ct);
}
