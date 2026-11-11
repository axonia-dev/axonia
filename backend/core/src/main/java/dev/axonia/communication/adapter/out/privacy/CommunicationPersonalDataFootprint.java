package dev.axonia.communication.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.shared.api.personaldata.Classification;
import dev.axonia.shared.api.personaldata.ColumnFootprint;
import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.Disposition;
import dev.axonia.shared.api.personaldata.ExportTreatment;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * Three tables with three different shapes. {@code communication_preference} and {@code communication_delivery} key on
 * {@code user_id} and describe one person's relationship with the spine, so both go with them at erasure.
 * {@code communication_message} is one message shared by every recipient of it, and it is removed when its last
 * delivery is, never because any one recipient goes. A message about a member is the exception: it exists only to say
 * something about that person, so it goes with them, for every recipient.
 *
 * <p>
 * {@code subject_ref}, on the message and on the record retention leaves of it, is a handle the sending module owns.
 * For most types it names a thing - a session id, a quarter key - but for some it ends with the id of the member the
 * message is about: an open-to-questions notice is {@code <communityId>:<area>:<declarerId>}. It is therefore declared
 * {@link Classification#SUBJECT} with {@link Disposition#DELETE_ROW}, and {@link ExportTreatment#WITHHELD}: a key into
 * the outbox, not a fact, and the person it can name is the person the export is for. The live rows go through
 * {@code CommunicationMemberDataEraser}, which deletes every message naming the member under
 * {@link OutboundMessage#ABOUT_USER_FACT}; the expired records go through the sending module's own eraser, which
 * forgets the subjects ending with the member's id through {@link dev.axonia.communication.api.ExpiredSubjects}.
 *
 * <p>
 * {@code communication_message.facts} is classified {@link Classification#PERSONAL_CONTENT} and exported rather than
 * withheld: it is the content a recipient already received, or is about to, in their own inbox, and returning it in
 * their own subject access request discloses nothing the mail itself did not already tell them. A message about a
 * member is in that member's export too: its facts are what axonia told others about them. Who received it is not,
 * since the recipients are other people, and a recipient list would reveal what each of them asked to learn.
 *
 * <p>
 * {@code communication_production_watermark} carries no personal data at all - see
 * {@link #communicationProductionWatermark} for the reasoning - so it is declared here with an empty {@code personal}
 * list, every column under {@code notPersonal}, the same shape {@code role} and {@code communities} already use
 * elsewhere in the schema.
 */
@Component
public class CommunicationPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(communicationPreference(), communicationMessage(), communicationDelivery(),
                communicationProductionWatermark(), communicationExpiredMessage());
    }

    private TableFootprint communicationPreference() {

        return TableFootprint.builder().table("communication_preference")
                .category(DataCategory.WHEN_AXONIA_CONTACTS_YOU)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "message_type", "enabled", "created_at", "updated_at",
                        "version"))
                .exportedContext(Set.of("message_type", "enabled", "created_at", "updated_at")).build();
    }

    /**
     * {@code facts} stays {@link Classification#PERSONAL_CONTENT} but its disposition is no longer
     * {@link Disposition#DELETE_ROW}: one message row is now shared by every recipient of it, so deleting it with any
     * one of them would destroy the other recipients' record of what they were sent. The row goes only when its last
     * delivery does, which {@code CommunicationMemberDataEraser} enforces rather than the disposition, so this column
     * is declared {@link Disposition#EXPORT_ONLY} - it is carried into a subject access request, and erased by the
     * delivery-driven cleanup rather than by a per-column rule.
     *
     * <p>
     * {@code subject_ref} is the handle the class javadoc describes, withheld from every export. {@code template} stays
     * out of {@code exportedContext}: it is a template file name, which describes how axonia rendered the mail rather
     * than anything about the person it was sent to.
     */
    private TableFootprint communicationMessage() {

        return TableFootprint.builder().table("communication_message").category(DataCategory.WHEN_AXONIA_CONTACTS_YOU)
                .personal(
                        List.of(new ColumnFootprint("facts", Classification.PERSONAL_CONTENT, Disposition.EXPORT_ONLY),
                                subjectRef()))
                .notPersonal(Set.of("id", "organization_id", "message_type", "template", "created_at", "updated_at",
                        "version"))
                .exportedContext(Set.of("message_type", "created_at", "updated_at")).build();
    }

    /**
     * Pure delivery bookkeeping for one recipient, naming nobody but the recipient in {@code user_id}. {@code channel}
     * joins {@code notPersonal}: a chat row's {@code user_id} is {@code NULL} - it names a community's room, not a
     * person - so {@code channel} is what a reader needs to tell that row apart from an ordinary mail row, not personal
     * data of its own.
     *
     * <p>
     * {@code attempts} and {@code claimed_at} stay out of {@code exportedContext}: they are outbox retry plumbing,
     * recording how hard the sender worked rather than what the member was sent or when they read it.
     */
    private TableFootprint communicationDelivery() {

        return TableFootprint.builder().table("communication_delivery").category(DataCategory.WHEN_AXONIA_CONTACTS_YOU)
                .personal(List.of(new ColumnFootprint("user_id", Classification.SUBJECT, Disposition.DELETE_ROW)))
                .notPersonal(Set.of("id", "organization_id", "message_id", "channel", "chat_destination_id", "status",
                        "attempts", "claimed_at", "sent_at", "read_at", "suppressed_reason", "created_at", "updated_at",
                        "version"))
                .exportedContext(
                        Set.of("status", "sent_at", "read_at", "suppressed_reason", "created_at", "updated_at"))
                .build();
    }

    /**
     * A producer's own bookkeeping of how far it has claimed, keyed on {@code organization_id} and a
     * {@code producer_key} constant such as {@code "session-wrap-up"}, never on a person. {@code produced_through} is a
     * clock instant the sweep itself computed, not anything a member wrote or that identifies one; nothing in this row
     * names, or could be used to single out, an individual. It is claimed here, under {@code WHEN_AXONIA_CONTACTS_YOU},
     * because it exists solely to drive the same outbound-mail machinery {@code communication_message},
     * {@code communication_delivery} and {@code communication_preference} belong to, not because it holds anything
     * personal - {@code personal} is deliberately empty and {@code notPersonal} covers every column.
     */
    private TableFootprint communicationProductionWatermark() {

        return TableFootprint.builder().table("communication_production_watermark")
                .category(DataCategory.WHEN_AXONIA_CONTACTS_YOU).personal(List.of())
                .notPersonal(Set.of("organization_id", "producer_key", "produced_through", "updated_at", "version"))
                .build();
    }

    /**
     * What retention leaves behind when a message's content and per-recipient rows are deleted: the subject it was
     * about and five integers. No {@code user_id}, no facts, no template; only {@code subject_ref}, which for some
     * types names the member the message was about, as the class javadoc says. A record whose subject names nobody
     * outlives erasure deliberately: a count of how many people were mailed singles nobody out, and destroying it would
     * let the outbox send an expired message a second time. One whose subject names the erased member goes with them,
     * and with it the guard, which only matters for a message about a person no longer there to be about.
     */
    private TableFootprint communicationExpiredMessage() {

        return TableFootprint.builder().table("communication_expired_message")
                .category(DataCategory.WHEN_AXONIA_CONTACTS_YOU).personal(List.of(subjectRef()))
                .notPersonal(Set.of("id", "organization_id", "message_type", "pending_count", "sent_count",
                        "suppressed_count", "unreachable_count", "abandoned_count", "expired_at"))
                .build();
    }

    private static ColumnFootprint subjectRef() {

        return new ColumnFootprint("subject_ref", Classification.SUBJECT, Disposition.DELETE_ROW,
                ExportTreatment.WITHHELD);
    }
}
