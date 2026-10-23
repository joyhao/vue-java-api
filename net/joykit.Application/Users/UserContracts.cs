using System.ComponentModel.DataAnnotations;
using joykit.Domain.Users;

namespace joykit.Application.Users;

public sealed class SaveUserRequest
{
    [Required, StringLength(255)]
    public string Name { get; set; } = "";
    [StringLength(2048)]
    public string? AvatarUrl { get; set; }
    [EmailAddress, StringLength(254)]
    public string? Email { get; set; }
    [StringLength(32), RegularExpression(@"^\+?[0-9()\-\s]{3,32}$")]
    public string? Phone { get; set; }
    [Range(0, 1)]
    public short Status { get; set; } = 1;
}

public sealed class UserQuery
{
    [Range(1, int.MaxValue)]
    public int PageNumber { get; set; } = 1;
    [Range(1, 100)]
    public int PageSize { get; set; } = 20;
    [StringLength(100)]
    public string? Keyword { get; set; }
}

public sealed record UserDto(string Id, string Name, string? AvatarUrl, string? Email,
    string? Phone, short Status, DateTime CreatedAt, DateTime UpdatedAt)
{
    public static UserDto From(User user) => new(user.Id.ToString(System.Globalization.CultureInfo.InvariantCulture),
        user.Name, user.AvatarUrl, user.Email, user.Phone, user.Status, user.CreatedAt, user.UpdatedAt);
}

public sealed record PagedUsers(IReadOnlyList<UserDto> Items, long Total, int PageNumber, int PageSize);
public sealed record DeleteUsersRequest(string[] Ids);
public sealed record DeleteAllUsersRequest(string Confirmation);
public sealed record DeleteUsersResult(long DeletedCount);
public sealed class UserConflictException(string message) : Exception(message);
