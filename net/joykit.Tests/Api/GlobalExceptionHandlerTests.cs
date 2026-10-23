using joykit.Api.ExceptionHandling;
using joykit.Application.Common.Exceptions;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;

namespace joykit.Tests.Api;

public class GlobalExceptionHandlerTests
{
    [Theory]
    [InlineData(400)]
    [InlineData(404)]
    [InlineData(409)]
    [InlineData(500)]
    public async Task Handle_MapsOnlyExpectedExceptionsAndHidesInternalMessages(int status)
    {
        Exception error = status switch
        {
            400 => new RequestValidationException("校验失败"),
            404 => new ResourceNotFoundException("资源不存在"),
            409 => new ConflictException("数据冲突"),
            _ => new ArgumentException("内部连接参数和机密")
        };
        var writer = new CapturingProblemDetailsService();
        var handler = new GlobalExceptionHandler(writer, NullLogger<GlobalExceptionHandler>.Instance);
        var context = new DefaultHttpContext();
        context.Request.Path = "/api/test";

        Assert.True(await handler.TryHandleAsync(context, error, default));
        Assert.Equal(status, context.Response.StatusCode);
        Assert.NotNull(writer.Context);
        Assert.Equal(status, writer.Context.ProblemDetails.Status);
        Assert.True(writer.Context.ProblemDetails.Extensions.ContainsKey("traceId"));
        if (status == 500) Assert.DoesNotContain("机密", writer.Context.ProblemDetails.Detail!);
        else Assert.Equal(error.Message, writer.Context.ProblemDetails.Detail);
    }

    private sealed class CapturingProblemDetailsService : IProblemDetailsService
    {
        public ProblemDetailsContext? Context { get; private set; }
        public ValueTask WriteAsync(ProblemDetailsContext context)
        {
            Context = context;
            return ValueTask.CompletedTask;
        }
        public ValueTask<bool> TryWriteAsync(ProblemDetailsContext context)
        {
            Context = context;
            return ValueTask.FromResult(true);
        }
    }
}
