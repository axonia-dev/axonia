package dev.axonia.identity.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.Classification;
import dev.axonia.shared.api.personaldata.ColumnFootprint;
import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.Disposition;
import dev.axonia.shared.api.personaldata.ExportTreatment;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * Identity owns the person record itself, so {@code identity_user} carries the widest personal footprint in the
 * platform: {@code user_id} is the subject, and every SCIM/IdP-sourced field describing that person - name, email,
 * external and SCIM ids, avatar, org unit - is content about them that leaves with their row.
 * {@code personal_access_token} and {@code directory_group_member} each carry one more identifier of the same shape: a
 * token holder, a synced group member. A table another module owns is declared by that module's own footprint: each
 * table is declared by the module that owns it.
 *
 * <p>
 * {@code scim_token} and {@code directory_group} hold IdP plumbing - hashed tokens, directory metadata - not people.
 */
@Component
public class IdentityPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(identityUser(), personalAccessToken(), agentConnection(), agentConnectionToken(),
                directoryGroupMember(), scimToken(), directoryGroup());
    }

    /**
     * The record-keeping columns are not handed verbatim to both buckets. {@code notPersonal} is still the full
     * affirmation that somebody looked at every one of them, but an export carries only the five that say something
     * about the person: whether they are active, how they arrived, when they were last seen, and the two timestamps
     * every export keeps. {@code id}, {@code organization_id} and {@code version} are this database's own bookkeeping,
     * and {@code avatar_etag} and {@code avatar_synced_at} describe a mirroring job rather than a member.
     *
     * <p>
     * Four personal columns are {@link ExportTreatment#WITHHELD}: an erasure still deletes each of them exactly as it
     * deletes the person's name, and an export declines to print them because each is a handle rather than a fact.
     * {@code scim_id} is the key the customer's directory filed this person under, {@code external_id} the key their
     * identity provider did, and {@code avatar_media_key} and {@code avatar_override_key} are where object storage
     * keeps the two images. The images themselves still travel, bundled through {@code ExportFragment#mediaKeys()}.
     */
    private TableFootprint identityUser() {

        var recordKeepingColumns = Set.of("id", "organization_id", "created_at", "updated_at", "version", "active",
                "arrival", "avatar_etag", "avatar_synced_at", "last_seen_at");

        return TableFootprint.builder().table("identity_user").category(DataCategory.WHO_YOU_ARE).personal(List.of(
                new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW),
                new ColumnFootprint("display_name", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW),
                new ColumnFootprint("email", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW),
                new ColumnFootprint("external_id", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW,
                        ExportTreatment.WITHHELD),
                new ColumnFootprint("scim_id", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW,
                        ExportTreatment.WITHHELD),
                new ColumnFootprint("avatar_media_key", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW,
                        ExportTreatment.WITHHELD),
                new ColumnFootprint("avatar_override_key", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW,
                        ExportTreatment.WITHHELD),
                new ColumnFootprint("observed_time_zone", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW),
                new ColumnFootprint("declared_time_zone", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW),
                new ColumnFootprint("business_unit", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW),
                new ColumnFootprint("department", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW),
                new ColumnFootprint("team", Classification.PERSONAL_CONTENT, Disposition.DELETE_ROW)))
                .notPersonal(recordKeepingColumns)
                .exportedContext(Set.of("created_at", "updated_at", "active", "arrival", "last_seen_at")).build();
    }

    /**
     * {@code token_hash} is opted out of the export: a credential digest is not something an access request asks for.
     * So are {@code id}, {@code organization_id} and {@code version}, which name a row and its revision in this
     * database rather than saying anything about the person who minted the token.
     */
    private TableFootprint personalAccessToken() {

        return TableFootprint.builder().table("personal_access_token").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "token_hash", "name", "scopes", "last_used_at",
                        "revoked_at", "expires_at", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("name", "scopes", "last_used_at", "revoked_at", "expires_at", "created_at",
                        "updated_at"))
                .build();
    }

    /**
     * One consent a member gave an MCP client. {@code code_challenge} is a PKCE digest and {@code redirect_uri} a
     * loopback address on the member's own machine; neither is exported.
     */
    private TableFootprint agentConnection() {

        return TableFootprint.builder().table("agent_connection").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "client_id", "client_name", "client_host", "scopes",
                        "resource", "redirect_uri", "code_challenge", "activated_at", "last_used_at", "ends_at",
                        "revoked_at", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("client_name", "client_host", "scopes", "activated_at", "last_used_at",
                        "ends_at", "revoked_at", "created_at"))
                .build();
    }

    private TableFootprint agentConnectionToken() {

        return TableFootprint.builder().table("agent_connection_token").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "connection_id", "kind", "token_hash",
                        "expires_at", "consumed_at", "created_at", "updated_at", "version"))
                .build();
    }

    /**
     * {@code user_scim_id} is {@link ExportTreatment#WITHHELD}: the row is squarely the member's and an erasure takes
     * it, but a SCIM id is the key the customer's directory filed them under rather than anything they could read. The
     * export resolves it to the canonical identity provider subject and emits that instead, which is why
     * {@code user_id} sits in {@code exportedLabels} despite being identifier-shaped - it is the one alias of that
     * shape allowed, and the gate carries a single allowlist entry for it. {@code group_id} gives way to
     * {@code directory_group}, the group's own display name, for the same reason: a bigint naming a row in this
     * database says nothing to the person reading their export.
     */
    private TableFootprint directoryGroupMember() {

        return TableFootprint.builder().table("directory_group_member").category(DataCategory.WHO_YOU_ARE)
                .personal(List.of(new ColumnFootprint("user_scim_id", Classification.SUBJECT, Disposition.DELETE_ROW,
                        ExportTreatment.WITHHELD)))
                .notPersonal(Set.of("id", "organization_id", "group_id", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("created_at", "updated_at"))
                .exportedLabels(Set.of("directory_group", "user_id")).build();
    }

    private TableFootprint scimToken() {

        return TableFootprint.builder().table("scim_token").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "token_hash", "label", "last_used_at",
                        "created_at", "updated_at", "version", "revoked_at", "expires_at"))
                .build();
    }

    private TableFootprint directoryGroup() {

        return TableFootprint.builder().table("directory_group").category(DataCategory.ACCESS_AND_SECURITY)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "scim_id", "external_id",
                        "display_name", "reconciliation_key", "confirmed_at", "created_at", "updated_at", "version"))
                .build();
    }
}
