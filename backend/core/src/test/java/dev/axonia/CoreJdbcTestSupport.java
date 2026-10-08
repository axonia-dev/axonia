package dev.axonia;

import org.springframework.test.context.ContextConfiguration;

/** {@link JdbcTestSupport} on {@link CoreTestApplication}, for the open JDBC tests. */
@ContextConfiguration(classes = CoreTestApplication.class)
public abstract class CoreJdbcTestSupport extends JdbcTestSupport {
}
