package dev.axonia.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Every open context inserts through {@link KeyReturningPostgresDialect}, not the dialect Spring Boot resolves. */
class InsertDialectWiringTest {

    @Nested
    class AJdbcSlice extends CoreJdbcTestSupport {

        @Autowired
        private ApplicationContext context;

        @Test
        void insertsThroughTheKeyReturningDialect() {

            // when
            var actual = context.getBean(DataAccessStrategy.class).getDialect();

            // then
            assertThat(context.getBeansOfType(JdbcDialect.class)).hasSize(1);
            assertThat(actual).isInstanceOf(KeyReturningPostgresDialect.class);
        }
    }

    @Nested
    @CoreSpringBootTest
    @ActiveProfiles("DEV")
    class AFullContext {

        @Autowired
        private ApplicationContext context;

        @Test
        void insertsThroughTheKeyReturningDialect() {

            // when
            var actual = context.getBean(DataAccessStrategy.class).getDialect();

            // then
            assertThat(context.getBeansOfType(JdbcDialect.class)).hasSize(1);
            assertThat(actual).isInstanceOf(KeyReturningPostgresDialect.class);
        }
    }
}
