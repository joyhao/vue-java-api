namespace joykit.Application.Users;

public sealed record UserWriteData(string Name, string? AvatarUrl, string? Email, string? Phone, short Status);
public sealed record UserPageQuery(int PageNumber, int PageSize, string? Keyword);
