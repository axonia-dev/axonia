package dev.axonia.communication.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.Classification;
import dev.axonia.Disposition;
import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Pins the {@code facts} judgement call so a future edit cannot flip it silently: declared personal content, present in
 * the member's own export, and reached through the deliveries addressed to them rather than by a {@code user_id} the
 * message does not have. {@code PersonalDataFootprintTest} only proves emitted keys are a subset of
 * {@code exportedColumns()} - it would pass whichever way this is classified - so this is the test that actually
 * exercises the decision, the same role {@code SupportDataSubjectRightsTest} plays for {@code filed_by_subject_id}.
 */
class CommunicationDataSubjectRightsTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String MEMBER = "member-receiving";
    private static final String STRANGER = "member-elsewhere";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private CommunicationPersonalDataFootprint footprint;

    @Autowired
    private CommunicationPersonalDataExporter exporter;

    @Test
    void declaresFactsAsPersonalContentSoTheMembersExportCarriesIt() {

        var message = footprint.table("communication_message");

        assertThat(message.personal())
                .extracting(column -> column.column(), column -> column.classification(),
                        column -> column.disposition())
                .containsExactly(tuple("facts", Classification.PERSONAL_CONTENT, Disposition.EXPORT_ONLY));
        assertThat(message.exportedColumns()).contains("facts");
        assertThat(footprint.table("communication_delivery").personal())
                .extracting(column -> column.column(), column -> column.classification(),
                        column -> column.disposition())
                .containsExactly(tuple("user_id", Classification.SUBJECT, Disposition.DELETE_ROW));
    }

    /**
     * The three projections named exhaustively rather than by {@code contains}, because what this module withholds is
     * the whole point of the declaration and a {@code contains} assertion cannot see a column come back. Each absence
     * is a decision: {@code subject_ref} is an opaque session or quarter key, {@code template} is the name of the file
     * axonia rendered the mail from, and {@code attempts} and {@code claimed_at} record how hard the outbox worked
     * rather than anything the member was sent or read.
     */
    @Test
    void carriesWhatWasSentAndReadAndNoneOfTheOutboxPlumbingAroundIt() {

        assertThat(footprint.table("communication_preference").exportedColumns()).containsExactlyInAnyOrder("user_id",
                "message_type", "enabled", "created_at", "updated_at");
        assertThat(footprint.table("communication_message").exportedColumns()).containsExactlyInAnyOrder("facts",
                "message_type", "created_at", "updated_at");
        assertThat(footprint.table("communication_delivery").exportedColumns()).containsExactlyInAnyOrder("user_id",
                "status", "sent_at", "read_at", "suppressed_reason", "created_at", "updated_at");
    }

    @Test
    void exportsTheMessageFactsForEveryMemberADeliveryWasAddressedTo() {

        seedDelivery(seedMessage("session:rights-1", "rights-test-presenter"), MEMBER);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, MEMBER));

        assertThat(json(fragment)).contains("rights-test-presenter");
    }

    @Test
    void withholdsAMessageNobodyDeliveredToThisMember() {

        seedDelivery(seedMessage("session:rights-2", "somebody-elses-presenter"), STRANGER);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, MEMBER));

        assertThat(json(fragment)).as("a message sent only to somebody else is not this member's data")
                .doesNotContain("somebody-elses-presenter");
    }

    /**
     * Layer 3 used to hold this: {@code facts} was a personal column the gate required in the whole-organization
     * fragment, and that fragment's keys come from rows actually returned. {@code EXPORT_ONLY} exempts it from that
     * check, so without this test a whole-organization branch that returned nothing would ship a tenant export with no
     * message content in it and fail nothing.
     */
    @Test
    void carriesEveryRecipientsMessageInAWholeOrganizationExport() {

        seedDelivery(seedMessage("session:rights-3", "tenant-wide-presenter"), STRANGER);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "communication_message")).hasSize(1);
        assertThat(json(fragment))
                .as("the whole tenant is the subject, so no recipient's message is somebody else's data")
                .contains("tenant-wide-presenter");
    }

    private Long seedMessage(String subjectRef, String presenterName) {

        return jdbc.queryForObject(
                "INSERT INTO communication_message (organization_id, message_type, subject_ref, template, facts, "
                        + "created_at, updated_at, version) VALUES (:org, 'session_wrap_up', :subject, "
                        + "'session-template', jsonb_build_object('presenterName', :presenter), now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "subject", subjectRef, "presenter", presenterName), Long.class);
    }

    private void seedDelivery(Long messageId, String userId) {

        jdbc.update(
                "INSERT INTO communication_delivery (organization_id, message_id, user_id, status, created_at, "
                        + "updated_at, version) VALUES (:org, :message, :user, 'PENDING', now(), now(), 0)",
                Map.of("org", ORG, "message", messageId, "user", userId));
    }
}
