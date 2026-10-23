using joykit.Application.Users;
using Microsoft.Extensions.DependencyInjection;

namespace joykit.Application;

public static class DependencyInjection
{
    public static IServiceCollection AddApplication(this IServiceCollection services)
    {
        services.AddScoped<UserService>();
        return services;
    }
}
