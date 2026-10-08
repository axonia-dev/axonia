package dev.axonia.installation.internal;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.CurrentToken;
import dev.axonia.Pageables;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.communication.MailServer;
import dev.axonia.communication.MailServerState;
import dev.axonia.installation.InstallationPermissions;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/mail-server")
@RequiredArgsConstructor
class MailServerController {

    private final AuthorizationGate gate;

    private final MailServerService service;

    private final MailTestService tests;

    private final MailServer mailServer;

    @GetMapping
    MailServerView read() {

        gate.requirePermission(InstallationPermissions.MAIL_SERVER_READ);
        return service.view(actor());
    }

    /** One bit for any member: whether mail can leave at all. */
    @GetMapping("/status")
    MailServerStatus status() {

        return new MailServerStatus(mailServer.current() instanceof MailServerState.Configured);
    }

    @PostMapping("/test")
    MailTestResult test(@Valid @RequestBody MailTestRequest request) {

        gate.requirePermission(InstallationPermissions.MAIL_SERVER_WRITE);
        return tests.test(request, actor());
    }

    /** Write does not imply read, so a caller who may not read the view learns only the revision it moved to. */
    @PutMapping
    MailServerSaved save(@Valid @RequestBody MailServerSaveRequest request) {

        gate.requirePermission(InstallationPermissions.MAIL_SERVER_WRITE);
        var view = service.save(request, actor());
        return gate.hasPermission(InstallationPermissions.MAIL_SERVER_READ)
                ? view
                : new MailServerSaved.Revision(view.revision());
    }

    /**
     * {@code baseRevision} is the revision the Clear was confirmed on, as a save's; optional here to name it in a 400.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@RequestParam(required = false) Long baseRevision) {

        gate.requirePermission(InstallationPermissions.MAIL_SERVER_WRITE);
        if (baseRevision == null) {
            throw new MailServerFieldException("baseRevision", "required.");
        }
        service.clear(baseRevision, actor());
    }

    @GetMapping("/changes")
    Page<MailServerChangeView> changes(@PageableDefault(size = 20) Pageable pageable) {

        gate.requirePermission(InstallationPermissions.MAIL_SERVER_READ);
        return service.changes(Pageables.requireSortIn(pageable, Set.of()), actor());
    }

    private static MailServerActor actor() {

        return new MailServerActor(CurrentToken.subject(), CurrentToken.organization());
    }
}
