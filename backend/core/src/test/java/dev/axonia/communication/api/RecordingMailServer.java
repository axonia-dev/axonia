package dev.axonia.communication.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.axonia.communication.api.spi.MailServer;

/** A mail server whose answer a test sets, and which keeps every outcome delivery reports to it. */
public final class RecordingMailServer implements MailServer {

    public static final MailServerSettings LOCAL = MailServerSettings.builder().host("localhost").port(1025)
            .security(MailSecurity.NONE).fromAddress("axonia@localhost").build();

    private final List<DeliveryOutcome> reports = new CopyOnWriteArrayList<>();
    private volatile MailServerState state;
    private volatile RuntimeException recordingFailure;

    private RecordingMailServer(MailServerState state) {

        this.state = state;
    }

    public static RecordingMailServer configured(MailServerSettings settings) {

        return new RecordingMailServer(MailServerState.configured(settings));
    }

    public void answer(MailServerState next) {

        state = next;
    }

    public void failRecordingWith(RuntimeException failure) {

        recordingFailure = failure;
    }

    public List<MailOutcome> outcomes() {

        return reports.stream().map(DeliveryOutcome::outcome).toList();
    }

    public List<DeliveryOutcome> reports() {

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
