package dev.axonia.events.internal;

import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class SessionRepositoryCustomImpl implements SessionRepositoryCustom {

    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    @Override
    public long countPast(PastSessionsQuery pastSessions) {

        return jdbcAggregateTemplate.count(Query.query(pastCriteria(pastSessions)), SessionEntity.class);
    }

    private Criteria pastCriteria(PastSessionsQuery pastSessions) {

        var criteria = Criteria.where("organizationId").is(pastSessions.organizationId()).and("communityId")
                .is(pastSessions.communityId()).and("status").notIn("CANCELLED").and("scheduledAt")
                .lessThan(pastSessions.now());
        if (pastSessions.status() != null) {
            criteria = criteria.and("status").is(pastSessions.status().name());
        }
        if (pastSessions.from() != null) {
            criteria = criteria.and("scheduledAt").greaterThanOrEquals(pastSessions.from());
        }
        if (pastSessions.to() != null) {
            criteria = criteria.and("scheduledAt").lessThan(pastSessions.to());
        }
        return criteria;
    }
}
