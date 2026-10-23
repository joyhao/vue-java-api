using joykit.Infrastructure;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Npgsql;

namespace joykit.Tests.Infrastructure;

public class DependencyInjectionTests
{
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData(" ")]
    public void AddInfrastructure_RejectsMissingConnectionString(string? connectionString)
    {
        var configuration = CreateConfiguration(connectionString);
        var services = new ServiceCollection();

        Assert.Throws<InvalidOperationException>(() => services.AddInfrastructure(configuration));
    }

    [Fact]
    public void AddInfrastructure_RegistersSingletonDataSource()
    {
        var configuration = CreateConfiguration(
            "Host=localhost;Port=9006;Database=postgres;Username=postgres;Timeout=5");
        var services = new ServiceCollection();
        services.AddLogging();
        services.AddInfrastructure(configuration);
        using var provider = services.BuildServiceProvider();

        var dataSource = provider.GetRequiredService<NpgsqlDataSource>();

        Assert.Same(dataSource, provider.GetRequiredService<NpgsqlDataSource>());
    }

    [Fact]
    public void Layers_RegisterScopedServicesWithoutConnectingToDatabase()
    {
        var services = new ServiceCollection();
        joykit.Application.DependencyInjection.AddApplication(services);
        services.AddInfrastructure(CreateConfiguration("Host=localhost;Database=postgres;Username=postgres"));
        using var provider = services.BuildServiceProvider(new ServiceProviderOptions
        {
            ValidateOnBuild = true, ValidateScopes = true
        });
        using var first = provider.CreateScope();
        using var second = provider.CreateScope();
        var service = first.ServiceProvider.GetRequiredService<joykit.Application.Users.UserService>();
        Assert.Same(service, first.ServiceProvider.GetRequiredService<joykit.Application.Users.UserService>());
        Assert.NotSame(service, second.ServiceProvider.GetRequiredService<joykit.Application.Users.UserService>());
        Assert.IsType<joykit.Infrastructure.Users.UserRepository>(
            first.ServiceProvider.GetRequiredService<joykit.Application.Users.IUserRepository>());
    }

    private static IConfiguration CreateConfiguration(string? connectionString)
    {
        return new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["ConnectionStrings:Postgres"] = connectionString
            })
            .Build();
    }
}
