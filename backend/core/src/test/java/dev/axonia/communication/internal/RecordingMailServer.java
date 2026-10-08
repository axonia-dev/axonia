package dev.axonia.communication.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.axonia.communication.DeliveryOutcome;
import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailSecurity;
import dev.axonia.communication.MailServer;
import dev.axonia.communication.MailServerSettings;
import dev.axonia.communication.MailServerState;

/** A mail server whose answer a test sets, and which keeps every outcome delivery reports to it. */
final class RecordingMailServer implements MailServer {

    static final MailServerSettings LOCAL = MailServerSettings.builder().host("localhost").port(1025)
            .security(MailSecurity.NONE).fromAddress("axonia@localhost").build();

    private final List<DeliveryOutcome> reports = new CopyOnWriteArrayList<>();
    private volatile MailServerState state;
    private volatile RuntimeException recordingFailure;

    private RecordingMailServer(MailServerState state) {

        this.state = state;
    }

    static RecordingMailServer configured(MailServerSettings settings) {

        return new RecordingMailServer(MailServerState.configured(settings));
    }

    void answer(MailServerState next) {

        state = next;
    }

    void failRecordingWith(RuntimeException failure) {

        recordingFailure = failure;
    }

    List<MailOutcome> outcomes() {

        return reports.stream().map(DeliveryOutcome::outcome).toList();
    }

    List<DeliveryOutcome> reports() {

        return List.copyOf(reports);
    }

    @Override
    public MailServerState current() {

        return state;
    }

    @Override
    public void recordOutcome(DeliveryOutcome outcome) {

        if (recordingFailure != null) {
            throw recordingFailure;
        }
        reports.add(outcome);
    }
}
