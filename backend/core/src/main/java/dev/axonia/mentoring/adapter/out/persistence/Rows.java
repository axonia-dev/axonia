package dev.axonia.mentoring.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

final class Rows {

    private Rows() {
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {

        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Timestamp timestamp(Instant value) {

        return value == null ? null : Timestamp.from(value);
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {

        return rs.getObject(column, Long.class);
    }
}
