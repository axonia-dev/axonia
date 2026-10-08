package dev.axonia;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Counts what a block of code actually costs the database: connection checkouts, and statement preparations on those
 * connections. Test scope only.
 *
 * <p>
 * This decorator wraps the {@code dataSource} bean, which is {@code OrganizationAwareDataSource} itself - so a
 * connection checkout also pays {@code OrganizationAwareDataSource}'s up to four RLS round trips (a {@code set_config}
 * and a matching {@code RESET} for each of organization and subject, only when present), executed directly on the raw
 * connection this counter never sees. That is precisely why connections are counted separately from statements: a
 * checkout is expensive in a way no statement count can reveal.
 *
 * <p>
 * The counters are static because the {@code dataSource} bean is a singleton shared by the whole context, and because
 * the alternative - injecting the decorator - would make every budget assertion depend on which bean the test happened
 * to receive. Tests that assert a budget must therefore not run in parallel with each other; none do.
 *
 * <p>
 * Statement counting wraps {@code Connection} in the same JDK dynamic proxy technique
 * {@code OrganizationAwareDataSource} uses, rather than adding a proxying JDBC library, so the two behave the same way
 * under the same edge cases.
 */
public class CountingDataSource extends DelegatingDataSource {

    private static final AtomicInteger CONNECTIONS = new AtomicInteger();
    private static final AtomicInteger STATEMENTS = new AtomicInteger();

    public CountingDataSource(DataSource delegate) {
        super(delegate);
    }

    public static void reset() {
        CONNECTIONS.set(0);
        STATEMENTS.set(0);
    }

    public static int connections() {
        return CONNECTIONS.get();
    }

    public static int statements() {
        return STATEMENTS.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return count(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return count(obtainTargetDataSource().getConnection(username, password));
    }

    private Connection count(Connection connection) {

        CONNECTIONS.incrementAndGet();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("prepare") || "createStatement".equals(method.getName())) {
                        STATEMENTS.incrementAndGet();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
