package dev.axonia.installation.internal;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import dev.axonia.ConflictException;
import lombok.RequiredArgsConstructor;

/**
 * A save and a clear write in a transaction of their own and tell {@link StoredMailServer} only once it has committed:
 * its reload then runs on a fresh connection, holds no row lock, and never caches a save that rolled back.
 */
@Service
@RequiredArgsConstructor
class MailServerService {

    private final MailServerResolver resolver;

    private final MailServerCandidates candidates;

    private final MailServerSettingsTable table;

    private final SettingsChangeHistory history;

    private final MailServerViews views;

    private final TestTokens tokens;

    private final TestedFingerprints fingerprints;

    private final StoredMailServer mailServer;

    private final TransactionOperations transactions;

    private final Clock clock;

    @Transactional(readOnly = true)
    public MailServerView view(MailServerActor actor) {

        return views.view(resolver.resolve(), actor.organizationId());
    }

    public MailServerView save(MailServerSaveRequest request, MailServerActor actor) {

        var view = Objects.requireNonNull(transactions.execute(status -> saved(request, actor)));
        mailServer.reload();
        return view;
    }

    /** Clears what is stored; the deployment's locked fields stay as their variables set them. */
    public void clear(long baseRevision, MailServerActor actor) {

        transactions.executeWithoutResult(status -> cleared(baseRevision, actor));
        mailServer.reload();
    }

    @Transactional(readOnly = true)
    public Page<MailServerChangeView> changes(Pageable pageable, MailServerActor actor) {

        return views.changes(pageable, actor.organizationId());
    }

    /** The revision is checked before the token, so a stale form reads "reload", not "test again". */
    private MailServerView saved(MailServerSaveRequest request, MailServerActor actor) {

        var resolved = resolver.resolve();
        var revision = resolved.row().revision();
        if (request.baseRevision() != revision) {
            throw staleRevision();
        }
        var requested = request.values().values();
        var fingerprint = candidates.settings(requested, resolved).fingerprint();
        if (!tokens.verify(request.testToken(), new TestTokens.Tested(fingerprint, revision, actor.subject()))) {
            throw new UntestedValuesException();
        }
        write(resolved.row(), candidates.next(requested, resolved, fingerprints.digest(fingerprint)), actor);
        return views.view(resolver.resolve(), actor.organizationId());
    }

    private void cleared(long baseRevision, MailServerActor actor) {

        var before = table.read();
        if (before.revision() != baseRevision) {
            throw staleRevision();
        }
        var after = MailServerRow.builder().revision(before.revision()).build();
        if (!MailServerChanges.between(before, after).isEmpty()) {
            write(before, after, actor);
        }
    }

    private void write(MailServerRow before, MailServerRow after, MailServerActor actor) {

        var at = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!table.write(after, at)) {
            throw staleRevision();
        }
        var changes = MailServerChanges.between(before, after);
        if (!changes.isEmpty()) {
            history.append(actor.subject(), at, changes);
        }
    }

    private static ConflictException staleRevision() {

        return new ConflictException("stale_revision", "The mail server was changed since it was read. Reload it.");
    }
}
