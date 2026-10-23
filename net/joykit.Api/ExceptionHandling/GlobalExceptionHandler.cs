using joykit.Application.Common.Exceptions;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Mvc;

namespace joykit.Api.ExceptionHandling;

public sealed class GlobalExceptionHandler(
    IProblemDetailsService problemDetailsService,
    ILogger<GlobalExceptionHandler> logger) : IExceptionHandler
{
    public async ValueTask<bool> TryHandleAsync(
        HttpContext context, Exception exception, CancellationToken cancellationToken)
    {
        var (status, title, detail) = exception switch
        {
            RequestValidationException => (400, "请求参数无效", exception.Message),
            ResourceNotFoundException => (404, "资源不存在", exception.Message),
            ConflictException => (409, "操作冲突", exception.Message),
            _ => (500, "服务器内部错误", "服务暂时无法处理请求，请稍后重试。")
        };

        if (status >= 500)
            logger.LogError(exception, "请求处理失败：{Method} {Path}，追踪编号 {TraceId}",
                context.Request.Method, context.Request.Path, context.TraceIdentifier);

        context.Response.StatusCode = status;
        var problem = new ProblemDetails
        {
            Status = status,
            Title = title,
            Detail = detail,
            Instance = context.Request.Path
        };
        problem.Extensions["traceId"] = context.TraceIdentifier;
        if (!await problemDetailsService.TryWriteAsync(new ProblemDetailsContext
        {
            HttpContext = context,
            Exception = exception,
            ProblemDetails = problem
        }))
        {
            await context.Response.WriteAsJsonAsync(problem, cancellationToken);
        }
        return true;
    }
}
