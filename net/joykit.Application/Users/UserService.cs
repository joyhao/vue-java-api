using System.ComponentModel.DataAnnotations;
using System.Globalization;

namespace joykit.Application.Users;

public sealed class UserService(IUserRepository repository)
{
    public async Task<PagedUsers> GetPageAsync(UserQuery query, CancellationToken ct)
    {
        Validate(query);
        query.Keyword = Normalize(query.Keyword);
        var (items, total) = await repository.GetPageAsync(query, ct);
        return new(items.Select(UserDto.From).ToArray(), total, query.PageNumber, query.PageSize);
    }

    public async Task<UserDto> CreateAsync(SaveUserRequest request, CancellationToken ct)
    {
        Prepare(request);
        return UserDto.From(await repository.CreateAsync(request, ct));
    }

    public async Task<UserDto> UpdateAsync(string id, SaveUserRequest request, CancellationToken ct)
    {
        Prepare(request);
        var user = await repository.UpdateAsync(ParseId(id), request, ct)
            ?? throw new KeyNotFoundException("用户不存在或已被删除。");
        return UserDto.From(user);
    }

    public async Task<DeleteUsersResult> DeleteAsync(string[]? ids, CancellationToken ct)
    {
        if (ids is null || ids.Length is < 1 or > 1000)
            throw new ArgumentException("请选择 1 至 1000 个用户。");
        return new(await repository.DeleteAsync(ids.Select(ParseId).Distinct().ToArray(), ct));
    }

    public async Task<DeleteUsersResult> DeleteAllAsync(string? confirmation, CancellationToken ct)
    {
        if (confirmation != "删除全部用户")
            throw new ArgumentException("请输入“删除全部用户”确认操作。");
        return new(await repository.DeleteAllAsync(ct));
    }

    private static long ParseId(string id) =>
        long.TryParse(id, NumberStyles.None, CultureInfo.InvariantCulture, out var value) && value > 0
            ? value : throw new ArgumentException("用户 ID 无效。");

    private static string? Normalize(string? value) => string.IsNullOrWhiteSpace(value) ? null : value.Trim();

    private static void Prepare(SaveUserRequest request)
    {
        request.Name = request.Name?.Trim() ?? "";
        request.Email = Normalize(request.Email);
        request.Phone = Normalize(request.Phone);
        request.AvatarUrl = Normalize(request.AvatarUrl);
        Validate(request);
        if (request.AvatarUrl is not null &&
            (!Uri.TryCreate(request.AvatarUrl, UriKind.Absolute, out var uri) ||
             (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp)))
            throw new ArgumentException("头像地址必须为 HTTP 或 HTTPS URL。");
    }

    private static void Validate(object value)
    {
        var errors = new List<ValidationResult>();
        if (!Validator.TryValidateObject(value, new ValidationContext(value), errors, true))
            throw new ArgumentException(string.Join("；", errors.Select(error => error.ErrorMessage)));
    }
}
