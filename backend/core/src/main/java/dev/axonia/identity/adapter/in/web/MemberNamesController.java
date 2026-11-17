package dev.axonia.identity.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.identity.application.MemberNameSettings;
import dev.axonia.identity.application.MemberNameSettings.PreviewRow;
import dev.axonia.identity.application.MemberNamesRefusedException;
import lombok.RequiredArgsConstructor;

/** The caller's Organization's member name patterns. Permission enforcement is {@link MemberNameSettings}'. */
@RestController
@RequestMapping("/${spring.application.name}/member-names")
@RequiredArgsConstructor
class MemberNamesController {

    private final MemberNameSettings settings;

    @GetMapping
    PatternsBody patterns() {
        return new PatternsBody(settings.patterns());
    }

    @PostMapping("/preview")
    PreviewResponse preview(@Valid @RequestBody PatternsBody request) {
        return new PreviewResponse(settings.preview(request.patterns()));
    }

    @PutMapping
    SaveResponse save(@Valid @RequestBody PatternsBody request) {
        return new SaveResponse(settings.save(request.patterns()));
    }

    /** Names the refused entry, so a client marks the pattern at fault rather than the whole list. */
    @ExceptionHandler(MemberNamesRefusedException.class)
    ProblemDetail handleRefused(MemberNamesRefusedException refusal) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refusal.getMessage());
        problem.setProperty("code", refusal.getCode());
        problem.setProperty("field", refusal.getField());
        return problem;
    }

    record PatternsBody(@NotNull List<String> patterns) {
    }

    record PreviewResponse(List<PreviewRow> rows) {
    }

    record SaveResponse(int changed) {
    }
}
