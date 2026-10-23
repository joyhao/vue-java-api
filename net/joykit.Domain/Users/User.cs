namespace joykit.Domain.Users;

public sealed record User(long Id, string Name, string? AvatarUrl, string? Email,
    string? Phone, short Status, DateTime CreatedAt, DateTime UpdatedAt);
