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
