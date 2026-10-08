package dev.axonia.communication.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.Classification;
import dev.axonia.ColumnFootprint;
import dev.axonia.DataCategory;
import dev.axonia.Disposition;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * Three tables with three different shapes. {@code communication_preference} and {@code communication_delivery} key on
 * {@code user_id} and describe one person's relationship with the spine, so both go with them at erasure.
 * {@code communication_message} keys on nobody: it is one message shared by every recipient of it, and it is removed
 * when its last delivery is, never because of any single person.
 *
 * <p>
 * {@code communication_message.facts} is classified {@link Classification#PERSONAL_CONTENT} and exported rather than
 * withheld: it is the content a recipient already received, or is about to, in their own inbox, and returning it in
 * their own subject access request discloses nothing the mail itself did not already tell them.
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
     * {@code subject_ref} and {@code template} stay out of {@code exportedContext}: {@code subject_ref} is an opaque
     * handle - a session id for one message type and a quarter key for another, see
     * {@link dev.axonia.communication.MessageSubjectPolicy} - so it names a thing the reader cannot resolve, and
     * {@code template} is a template file name, which describes how axonia rendered the mail rather than anything about
     * the person it was sent to.
     */
    private TableFootprint communicationMessage() {

        return TableFootprint.builder().table("communication_message").category(DataCategory.WHEN_AXONIA_CONTACTS_YOU)
                .personal(
                        List.of(new ColumnFootprint("facts", Classification.PERSONAL_CONTENT, Disposition.EXPORT_ONLY)))
                .notPersonal(Set.of("id", "organization_id", "message_type", "subject_ref", "template", "created_at",
                        "updated_at", "version"))
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
     * about and five integers. It names nobody - no {@code user_id}, no facts, no template - so {@code personal} is
     * empty and every column sits under {@code notPersonal}, the same shape {@link #communicationProductionWatermark}
     * uses. It outlives erasure deliberately: a count of how many people were mailed singles nobody out, and destroying
     * it would let the outbox send an expired message a second time.
     */
    private TableFootprint communicationExpiredMessage() {

        return TableFootprint.builder().table("communication_expired_message")
                .category(DataCategory.WHEN_AXONIA_CONTACTS_YOU).personal(List.of())
                .notPersonal(Set.of("id", "organization_id", "message_type", "subject_ref", "pending_count",
                        "sent_count", "suppressed_count", "unreachable_count", "abandoned_count", "expired_at"))
                .build();
    }
}
