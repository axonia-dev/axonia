package dev.axonia.recognition.adapter.in.web;

import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

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

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.ImpactReplayService;
import dev.axonia.recognition.application.LoveRequest;
import dev.axonia.recognition.application.LoveSummary;
import dev.axonia.recognition.application.MarkSeenRequest;
import dev.axonia.recognition.application.NewLoves;
import dev.axonia.recognition.application.RecognitionService;
import dev.axonia.recognition.application.RecognizedContribution;
import dev.axonia.recognition.domain.Love;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/recognition")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;
    private final ImpactReplayService impactReplayService;

    @GetMapping("/loves")
    LoveSummary summary(@RequestParam ContributionType contributionType, @RequestParam Long contributionId) {

        return recognitionService.summary(contributionType, contributionId);
    }

    @PostMapping("/loves")
    @ResponseStatus(HttpStatus.CREATED)
    Love love(@RequestBody @Valid LoveRequest request) {

        return recognitionService.love(request);
    }

    @DeleteMapping("/loves")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlove(@RequestParam ContributionType contributionType, @RequestParam Long contributionId) {

        recognitionService.unlove(contributionType, contributionId);
    }

    @GetMapping("/contributions/me")
    List<RecognizedContribution> myContributions() {

        return recognitionService.myContributions();
    }

    @GetMapping("/contributions/me/new-loves")
    NewLoves newLoves(@RequestParam Optional<Long> communityId) {

        return impactReplayService.newLoves(communityId);
    }

    @PutMapping("/contributions/me/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markSeen(@RequestBody @Valid MarkSeenRequest request) {

        impactReplayService.markSeen(request);
    }
}
