package dev.axonia.expertise.adapter.out.persistence;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.expertise.api.MemberTag;
import dev.axonia.expertise.api.MemberTagKind;
import dev.axonia.expertise.api.MemberTagStore;
import dev.axonia.expertise.api.MemberTagStores;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcMemberTagStores implements MemberTagStores {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public <T extends MemberTag> MemberTagStore<T> of(MemberTagKind kind, MemberTagStore.Factory<T> factory) {

        return new MemberTagTable<>(jdbc, tableOf(kind), factory);
    }

    static String tableOf(MemberTagKind kind) {

        return switch (kind) {
            case LEARNING_INTENT -> "learning_intent";
        };
    }
}
