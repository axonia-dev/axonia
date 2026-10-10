package dev.axonia.events.adapter.in.web;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.events.api.CreateOneOffRequest;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.Session;
import dev.axonia.events.application.CloseSessionRequest;
import dev.axonia.events.application.FollowUpRequest;
import dev.axonia.events.application.MessagePreview;
import dev.axonia.events.application.OccurrenceSkipService;
import dev.axonia.events.application.ScheduleService;
import dev.axonia.events.application.SessionReleaseService;
import dev.axonia.events.application.SessionService;
import dev.axonia.events.application.UpcomingReleaseRequest;
import dev.axonia.events.application.UpdateSessionRequest;
import dev.axonia.events.application.WrapUpReleaseRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SessionReleaseService sessionReleaseService;
    private final OccurrenceSkipService occurrenceSkipService;
    private final ScheduleService scheduleService;

    @PostMapping("/communities/{slug}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    Session createOneOff(@PathVariable String slug, @Valid @RequestBody CreateOneOffRequest request) {

        return sessionService.createOneOff(slug, request);
    }

    @GetMapping("/communities/{slug}/sessions")
    Page<Session> upcoming(@PathVariable String slug,
            @PageableDefault(size = 20, sort = "scheduled_at", direction = Sort.Direction.ASC) Pageable pageable) {

        return sessionService.upcoming(slug, pageable);
    }

    @GetMapping("/communities/{slug}/sessions/past")
    Page<Session> past(@PathVariable String slug, @ModelAttribute PastSessionsFilter filter,
            @PageableDefault(size = 20, sort = "scheduled_at", direction = Sort.Direction.DESC) Pageable pageable) {

        return sessionService.past(filter.toRequest(slug, pageable));
    }

    @PostMapping("/series/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    Session materialize(@PathVariable Long id) {

        return sessionService.materialize(id);
    }

    @PostMapping("/series/{id}/occurrences")
    @ResponseStatus(HttpStatus.CREATED)
    Session ensure(@PathVariable Long id, @Valid @RequestBody EnsureOccurrenceRequest request) {

        return sessionService.ensure(id, request.occurrenceDate());
    }

    @PostMapping("/series/{id}/occurrences/skip")
    Session skip(@PathVariable Long id, @Valid @RequestBody EnsureOccurrenceRequest request) {

        return occurrenceSkipService.skip(id, request.occurrenceDate());
    }

    @PostMapping("/series/{id}/occurrences/unskip")
    Session unskip(@PathVariable Long id, @Valid @RequestBody EnsureOccurrenceRequest request) {

        return occurrenceSkipService.unskip(id, request.occurrenceDate());
    }

    @GetMapping("/sessions/{id}")
    Session get(@PathVariable Long id) {

        return sessionService.getDetail(id);
    }

    @PutMapping("/sessions/{id}")
    Session update(@PathVariable Long id, @Valid @RequestBody UpdateSessionRequest request) {

        return sessionService.update(id, request);
    }

    @PostMapping("/sessions/{id}/cancel")
    Session cancel(@PathVariable Long id) {

        return sessionService.cancel(id);
    }

    @PostMapping("/sessions/{id}/follow-up")
    Session followUp(@PathVariable Long id, @Valid @RequestBody FollowUpRequest request) {

        return sessionService.recordFollowUp(id, request);
    }

    @PostMapping("/sessions/{id}/close")
    Session close(@PathVariable Long id, @Valid @RequestBody CloseSessionRequest request) {

        return sessionService.close(id, request);
    }

    @PutMapping("/sessions/{id}/wrap-up-release")
    Session wrapUpRelease(@PathVariable Long id, @RequestBody WrapUpReleaseRequest request) {

        return sessionReleaseService.setWrapUpRelease(id, request);
    }

    @PutMapping("/sessions/{id}/upcoming-release")
    Session upcomingRelease(@PathVariable Long id, @RequestBody UpcomingReleaseRequest request) {

        return sessionReleaseService.setUpcomingRelease(id, request);
    }

    /**
     * One preview endpoint taking the message type, not one endpoint per type. The guarantee - a preview renders
     * through the same {@code MailRenderer} and the same facts assembly a real send does, so it cannot diverge from
     * what recipients get - then extends to a second message type instead of having to be re-established for it, and a
     * third endpoint could never quietly drift from the first two.
     *
     * <p>
     * {@code type} is required and carries the wire code {@code MessageType} uses everywhere else on this API
     * ({@code session_upcoming}, {@code session_wrap_up}), never the Java constant name. Anything else is refused - see
     * {@code ReleasableMessage#of}.
     */
    @GetMapping("/communities/{slug}/sessions/{id}/message-preview")
    MessagePreview messagePreview(@PathVariable String slug, @PathVariable Long id, @RequestParam String type) {

        return sessionReleaseService.preview(slug, id, type);
    }

    @GetMapping("/communities/{slug}/schedule")
    List<ScheduleEntry> schedule(@PathVariable String slug, @RequestParam Instant from, @RequestParam Instant to) {

        return scheduleService.schedule(slug, from, to);
    }

    @GetMapping("/communities/{slug}/schedule/activity")
    List<String> scheduleActivity(@PathVariable String slug) {

        return scheduleService.activeMonths(slug);
    }
}
