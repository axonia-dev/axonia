package dev.axonia.identity.internal;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.jdbc.core.convert.EntityRowMapper;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;

/**
 * Token-matching user search.
 *
 * <p>
 * Every token of the query must appear somewhere in the user's display name or email, which is what lets
 * {@code "Dinant, Gabriel"} find a display name of {@code "Gabriel Dinant"}. A whole-substring match, which this
 * replaces, found neither.
 *
 * <p>
 * A middle initial is dropped rather than matched, as long as a real name token remains: {@code "Gabriel J. Dinant"}
 * searches for {@code "gabriel"} and {@code "dinant"} only. Under the AND semantics above an initial can never add
 * matching power - the directory that exported the name rarely stores the initial - and requiring it would turn every
 * such name into a false NOT_FOUND, which on the paths that resolve an actor is a refused write.
 *
 * <p>
 * The SQL is assembled rather than declared because the number of {@code LIKE} predicates is the number of tokens.
 * Every token is bound as a parameter and escaped for {@code LIKE}, so no part of the caller's query reaches the SQL
 * text.
 */
@RequiredArgsConstructor
class UserSearchRepositoryImpl implements UserSearchRepository {

    /** A person's name is a handful of words. Past this the query is noise, and each token costs another scan. */
    private static final int MAX_TOKENS = 5;

    /** What a token is matched against: the display name and the email, lowercased, as one string. */
    private static final String NAME_AND_EMAIL_HAYSTACK = "lower(coalesce(display_name,'') || ' ' || coalesce(email,''))";

    /** {@link UserSearch#nameOnly()}'s haystack: the display name alone, never the email. */
    private static final String NAME_ONLY_HAYSTACK = "lower(coalesce(display_name,''))";

    /**
     * Whitespace, plus the punctuation that surrounds a name in the places these queries come from: {@code "Dinant,
     * Gabriel"} from a directory export, {@code "Gabriel Dinant <gabriel@acme.com>"} from a mail header.
     */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,;<>\"()]+");

    /** A single letter, with or without the period that follows it: a middle initial, never a name. */
    private static final Pattern INITIAL = Pattern.compile("^\\p{L}\\.?$");

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcMappingContext mappingContext;
    private final JdbcConverter converter;

    @Override
    public List<UserEntity> search(UserSearch search) {

        var restrictToUserIds = search.restrictToUserIds();
        var tokens = tokenize(search.query());
        if (restrictToUserIds != null && (restrictToUserIds.isEmpty() || tokens.isEmpty())) {
            // A restricted search has nobody to draw from, or nothing to match on. Answered without SQL, because
            // "IN ()" is a syntax error and an unrestricted-looking query must not silently widen to the whole org.
            return List.of();
        }

        var params = new MapSqlParameterSource().addValue("organizationId", search.organizationId())
                .addValue("limit", search.limit()).addValue("offset", search.offset());
        // Sub-less rows are matched on purpose, unlike UserRepository#findPresentUserIds, which filters them out: a
        // SCIM-provisioned member who has never signed in exists, and the admin roster's job is to list who exists -
        // AdminUser#userId stays nullable all the way to the frontend schema for exactly that row. The restricted arm
        // below excludes them anyway without saying so, because NULL IN (:userIds) is never true.
        var sql = new StringBuilder(
                "SELECT " + UserEntity.COLUMNS + " FROM identity_user WHERE organization_id = :organizationId");
        if (!search.includeDeparted()) {
            sql.append(" AND active = true");
        }
        if (restrictToUserIds != null) {
            params.addValue("userIds", restrictToUserIds);
            sql.append(" AND user_id IN (:userIds)");
        }
        var haystack = search.nameOnly() ? NAME_ONLY_HAYSTACK : NAME_AND_EMAIL_HAYSTACK;
        for (var i = 0; i < tokens.size(); i++) {
            params.addValue("t" + i, "%" + tokens.get(i) + "%");
            sql.append(" AND ").append(haystack).append(" LIKE :t").append(i).append(" ESCAPE '\\'");
        }
        // user_id is nullable (a SCIM-provisioned user before first sign-in has none, and the tenant-scoped unique
        // constraint does not constrain NULLs), so display_name and user_id alone are not a total order: two
        // sub-less users sharing a display name would tie, making the LIMIT arbitrary. id is the NOT NULL primary
        // key, so it is the tiebreak that makes the ordering, and therefore the LIMIT, deterministic.
        sql.append(" ORDER BY display_name, user_id, id LIMIT :limit OFFSET :offset");

        return jdbc.query(sql.toString(), params, rowMapper());
    }

    private static List<String> tokenize(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }
        var raw = SEPARATORS.splitAsStream(query.trim().toLowerCase(Locale.ROOT)).filter(token -> !token.isEmpty())
                .toList();
        var names = raw.stream().filter(token -> !INITIAL.matcher(token).matches()).toList();
        // Initials are dropped only while a name survives them, and before the cap, so an initial can never eat one of
        // the five slots a real name token needs. A query that is nothing but single letters has no name to fall back
        // on and is matched as written: that is what keeps a one-letter search ("a" in the admin roster box) a
        // prefix search rather than a request for everybody.
        var tokens = names.isEmpty() ? raw : names;
        return tokens.stream().limit(MAX_TOKENS).map(UserSearchRepositoryImpl::escapeForLike).toList();
    }

    /**
     * Turns a token into a literal. Without this a query of {@code "%"} is a wildcard that matches the whole
     * organization, and {@code "_"} matches every name, which is the opposite of a search.
     */
    private static String escapeForLike(String token) {

        return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Spring Data's own entity mapping, so a column added to {@link UserEntity} tomorrow is read by this query, once it
     * is in {@link UserEntity#COLUMNS}, which {@code UserEntityColumnsTest} enforces, without anyone remembering to
     * update a hand-written row mapper. Built per query rather than once in the constructor: the mapping context caches
     * the entity, and a constructor that can throw is a SpotBugs finding.
     *
     * <p>
     * The cast is safe by construction: the mapping context is asked for the entity of exactly {@link UserEntity}.
     */
    @SuppressWarnings("unchecked")
    private RowMapper<UserEntity> rowMapper() {

        var entity = (RelationalPersistentEntity<UserEntity>) mappingContext
                .getRequiredPersistentEntity(UserEntity.class);
        return new EntityRowMapper<>(entity, converter);
    }
}
