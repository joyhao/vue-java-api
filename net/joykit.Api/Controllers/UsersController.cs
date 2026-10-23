using joykit.Application.Users;
using Microsoft.AspNetCore.Mvc;

namespace joykit.Api.Controllers;

[ApiController]
[Route("api/users")]
public sealed class UsersController(UserService service) : ControllerBase
{
    [HttpGet]
    public Task<PagedUsers> GetPage([FromQuery] UserQuery query, CancellationToken ct) => service.GetPageAsync(query, ct);

    [HttpPost]
    public async Task<ActionResult<UserDto>> Create(SaveUserRequest request, CancellationToken ct)
    {
        var user = await service.CreateAsync(request, ct);
        return StatusCode(StatusCodes.Status201Created, user);
    }

    [HttpPut("{id}")]
    public Task<UserDto> Update(string id, SaveUserRequest request, CancellationToken ct) => service.UpdateAsync(id, request, ct);

    [HttpDelete("{id}")]
    public async Task<ActionResult<DeleteUsersResult>> Delete(string id, CancellationToken ct)
    {
        var result = await service.DeleteAsync([id], ct);
        return result.DeletedCount == 0 ? NotFound(new { detail = "用户不存在或已被删除。" }) : Ok(result);
    }

    [HttpPost("batch-delete")]
    public Task<DeleteUsersResult> DeleteBatch(DeleteUsersRequest request, CancellationToken ct) => service.DeleteAsync(request.Ids, ct);

    [HttpPost("delete-all")]
    public Task<DeleteUsersResult> DeleteAll(DeleteAllUsersRequest request, CancellationToken ct) => service.DeleteAllAsync(request.Confirmation, ct);
}
