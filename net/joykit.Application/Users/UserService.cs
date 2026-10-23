using System.ComponentModel.DataAnnotations;
using System.Globalization;
using joykit.Application.Common.Exceptions;

namespace joykit.Application.Users;

public sealed class UserService(IUserRepository repository)
{
    public async Task<PagedUsers> GetPageAsync(UserQuery query, CancellationToken ct)
    {
        Validate(query);
        var criteria = new UserPageQuery(query.PageNumber, query.PageSize, query.Keyword);
        var (items, total) = await repository.GetPageAsync(criteria, ct);
        return new(items.Select(UserDto.From).ToArray(), total, query.PageNumber, query.PageSize);
    }

    public async Task<UserDto> CreateAsync(SaveUserRequest request, CancellationToken ct)
    {
        var data = Prepare(request);
        return UserDto.From(await repository.CreateAsync(data, ct));
    }

    public async Task<UserDto> UpdateAsync(string id, SaveUserRequest request, CancellationToken ct)
    {
        var data = Prepare(request);
        var user = await repository.UpdateAsync(ParseId(id), data, ct)
            ?? throw new ResourceNotFoundException("用户不存在或已被删除。");
        return UserDto.From(user);
    }

    public async Task<DeleteUsersResult> DeleteAsync(string[]? ids, CancellationToken ct)
    {
        if (ids is null || ids.Length is < 1 or > 1000)
            throw new RequestValidationException("请选择 1 至 1000 个用户。");
        return new(await repository.DeleteAsync(ids.Select(ParseId).Distinct().ToArray(), ct));
    }

    public async Task<DeleteUsersResult> DeleteAllAsync(string? confirmation, CancellationToken ct)
    {
        if (confirmation != "删除全部用户")
            throw new RequestValidationException("请输入“删除全部用户”确认操作。");
        return new(await repository.DeleteAllAsync(ct));
    }

    private static long ParseId(string id) =>
        long.TryParse(id, NumberStyles.None, CultureInfo.InvariantCulture, out var value) && value > 0
            ? value : throw new RequestValidationException("用户 ID 无效。");

    private static UserWriteData Prepare(SaveUserRequest request)
    {
        Validate(request);
        if (request.AvatarUrl is not null &&
            (!Uri.TryCreate(request.AvatarUrl, UriKind.Absolute, out var uri) ||
             (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp)))
            throw new RequestValidationException("头像地址必须为 HTTP 或 HTTPS URL。");
        return new(request.Name, request.AvatarUrl, request.Email, request.Phone, request.Status);
    }

    private static void Validate(object value)
    {
        var errors = new List<ValidationResult>();
        if (!Validator.TryValidateObject(value, new ValidationContext(value), errors, true))
            throw new RequestValidationException(string.Join("；", errors.Select(error => error.ErrorMessage)));
    }
}
