package dev.axonia.shared.adapter.out.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import dev.axonia.shared.api.tenancy.OrganizationResolver;
import dev.axonia.shared.api.tenancy.SubjectResolver;

/**
 * Wraps every acquired {@link Connection} so that when an Organization is present in the {@link OrganizationResolver}
 * the Postgres GUC {@code app.current_org} is SET on open and RESET on close, and likewise when a subject is present in
 * the {@link SubjectResolver} the GUC {@code app.current_subject} is SET on open and RESET on close, enabling Row-Level
 * Security policies keyed on either GUC.
 */
public class OrganizationAwareDataSource extends DelegatingDataSource {

    private final OrganizationResolver resolver;
    private final SubjectResolver subjects;

    public OrganizationAwareDataSource(DataSource delegate, OrganizationResolver resolver, SubjectResolver subjects) {
        super(delegate);
        this.resolver = resolver;
        this.subjects = subjects;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return prepare(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return prepare(obtainTargetDataSource().getConnection(username, password));
    }

    private Connection prepare(Connection connection) throws SQLException {

        var organization = resolver.currentOrganization();
        var subject = subjects.currentSubject();

        setGucs(connection, organization, subject);

        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        try {
                            resetGucs(connection, organization, subject);
                        } finally {
                            connection.close();
                        }
                        return null;
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private void setGucs(Connection connection, Optional<String> organization, Optional<String> subject)
            throws SQLException {

        if (organization.isPresent() && subject.isPresent()) {
            try (var ps = connection.prepareStatement(
                    "SELECT set_config('app.current_org', ?, false), set_config('app.current_subject', ?, false)")) {
                ps.setString(1, organization.get());
                ps.setString(2, subject.get());
                ps.execute();
            }
        } else if (organization.isPresent()) {
            try (var ps = connection.prepareStatement("SELECT set_config('app.current_org', ?, false)")) {
                ps.setString(1, organization.get());
                ps.execute();
            }
        } else if (subject.isPresent()) {
            try (var ps = connection.prepareStatement("SELECT set_config('app.current_subject', ?, false)")) {
                ps.setString(1, subject.get());
                ps.execute();
            }
        }
    }

    private void resetGucs(Connection connection, Optional<String> organization, Optional<String> subject)
            throws SQLException {

        if (organization.isPresent() && subject.isPresent()) {
            try (var s = connection.createStatement()) {
                s.execute("RESET app.current_org; RESET app.current_subject");
            }
        } else if (organization.isPresent()) {
            try (var s = connection.createStatement()) {
                s.execute("RESET app.current_org");
            }
        } else if (subject.isPresent()) {
            try (var s = connection.createStatement()) {
                s.execute("RESET app.current_subject");
            }
        }
    }
}
