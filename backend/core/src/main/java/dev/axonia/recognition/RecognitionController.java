package dev.axonia.recognition;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/recognition")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

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
}
