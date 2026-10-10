package dev.axonia.agent.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.Classification;
import dev.axonia.shared.api.personaldata.ColumnFootprint;
import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.Disposition;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * A tool call is logged against the token holder who made it. {@code user_id} is that person, and the row exists only
 * to record what they did with the agent, so it is deleted with them.
 * <p>
 * {@code token_id} is a key into {@code personal_access_token} rather than a fact about the person, so an export
 * resolves it to the token's own name instead of exposing the id. {@code id} and {@code organization_id} are the row's
 * own bookkeeping and carry nothing the person needs back.
 */
@Component
public class AgentPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("agent_tool_call").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "token_id", "connection_id", "tool_name", "outcome",
                        "occurred_at"))
                .exportedContext(Set.of("tool_name", "outcome", "occurred_at"))
                .exportedLabels(Set.of("token", "connection")).build());
    }
}
