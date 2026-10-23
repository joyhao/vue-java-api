using joykit.Application.Users;
using joykit.Application.Common.Exceptions;
using joykit.Domain.Users;

namespace joykit.Tests.Application;

public class UserServiceTests
{
    [Fact]
    public async Task Create_NormalizesFieldsAndPreservesLargeId()
    {
        var repository = new FakeRepository();
        var result = await new UserService(repository).CreateAsync(new()
        {
            Name = "  测试用户  ", Phone = " 123456789 ", Email = " ", Status = 1
        }, default);
        Assert.Equal("测试用户", result.Name);
        Assert.Equal("123456789", result.Phone);
        Assert.Null(result.Email);
        Assert.Equal("9007199254740993", result.Id);
    }

    [Theory]
    [InlineData("", 1)]
    [InlineData("   ", 1)]
    [InlineData("测试", 2)]
    public async Task Create_RejectsInvalidInput(string name, short status)
    {
        await Assert.ThrowsAsync<RequestValidationException>(() => new UserService(new FakeRepository())
            .CreateAsync(new() { Name = name, Status = status }, default));
    }

    [Theory]
    [InlineData(0, 20)]
    [InlineData(1, 101)]
    public async Task Query_RejectsInvalidPagination(int page, int size)
    {
        await Assert.ThrowsAsync<RequestValidationException>(() => new UserService(new FakeRepository())
            .GetPageAsync(new() { PageNumber = page, PageSize = size }, default));
    }

    [Fact]
    public async Task Update_MissingUserReturnsNotFound()
    {
        await Assert.ThrowsAsync<ResourceNotFoundException>(() => new UserService(new FakeRepository())
            .UpdateAsync("1", new() { Name = "测试" }, default));
    }

    [Fact]
    public async Task Delete_DeduplicatesIds()
    {
        var repository = new FakeRepository();
        var result = await new UserService(repository).DeleteAsync(["1", "1", "2"], default);
        Assert.Equal(2, result.DeletedCount);
        Assert.Equal(new long[] { 1, 2 }, repository.DeletedIds);
    }

    [Theory]
    [InlineData("-1")]
    [InlineData("abc")]
    [InlineData("0")]
    public async Task Delete_RejectsInvalidId(string id)
    {
        await Assert.ThrowsAsync<RequestValidationException>(() => new UserService(new FakeRepository()).DeleteAsync([id], default));
    }

    [Fact]
    public async Task Delete_RejectsEmptySelection()
    {
        await Assert.ThrowsAsync<RequestValidationException>(() => new UserService(new FakeRepository()).DeleteAsync([], default));
    }

    [Fact]
    public async Task DeleteAll_RequiresExactConfirmation()
    {
        var repository = new FakeRepository();
        var service = new UserService(repository);
        await Assert.ThrowsAsync<RequestValidationException>(() => service.DeleteAllAsync("确认", default));
        Assert.False(repository.AllDeleted);
        await service.DeleteAllAsync("删除全部用户", default);
        Assert.True(repository.AllDeleted);
    }

    [Fact]
    public async Task Query_PassesImmutableCriteriaWithoutMutatingRequest()
    {
        var repository = new FakeRepository();
        var query = new UserQuery { Keyword = "  测试  ", PageNumber = 2, PageSize = 10 };
        var result = await new UserService(repository).GetPageAsync(query, default);
        Assert.Equal(new UserPageQuery(2, 10, "测试"), repository.LastQuery);
        Assert.Equal("测试", query.Keyword);
        Assert.Equal(2, result.PageNumber);
    }

    [Fact]
    public void Request_NormalizesBeforeAnnotationValidation()
    {
        var request = System.Text.Json.JsonSerializer.Deserialize<SaveUserRequest>(
            """{"Name":"  测试  ","Email":" ","Phone":" 12345 ","AvatarUrl":" "}""")!;
        var errors = new List<System.ComponentModel.DataAnnotations.ValidationResult>();
        Assert.True(System.ComponentModel.DataAnnotations.Validator.TryValidateObject(request,
            new System.ComponentModel.DataAnnotations.ValidationContext(request), errors, true));
        Assert.Equal("测试", request.Name);
        Assert.Null(request.Email);
        Assert.Null(request.AvatarUrl);
        Assert.Equal("12345", request.Phone);
    }

    private sealed class FakeRepository : IUserRepository
    {
        public long[] DeletedIds { get; private set; } = [];
        public bool AllDeleted { get; private set; }
        public UserPageQuery? LastQuery { get; private set; }
        public Task<(IReadOnlyList<User> Items, long Total)> GetPageAsync(UserPageQuery query, CancellationToken ct)
        {
            LastQuery = query;
            return Task.FromResult<(IReadOnlyList<User>, long)>(([], 0));
        }
        public Task<User> CreateAsync(UserWriteData request, CancellationToken ct) => Task.FromResult(new User(
            9007199254740993, request.Name, request.AvatarUrl, request.Email, request.Phone, request.Status, DateTime.UtcNow, DateTime.UtcNow));
        public Task<User?> UpdateAsync(long id, UserWriteData request, CancellationToken ct) => Task.FromResult<User?>(null);
        public Task<long> DeleteAsync(long[] ids, CancellationToken ct)
        {
            DeletedIds = ids;
            return Task.FromResult((long)ids.Length);
        }
        public Task<long> DeleteAllAsync(CancellationToken ct)
        {
            AllDeleted = true;
            return Task.FromResult(3L);
        }
    }
}
