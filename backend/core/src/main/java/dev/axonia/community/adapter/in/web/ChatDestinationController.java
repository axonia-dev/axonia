package dev.axonia.community.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.community.application.ChatDestination;
import dev.axonia.community.application.ChatDestinationService;
import dev.axonia.community.application.CreateChatDestinationRequest;
import dev.axonia.community.application.UpdateChatDestinationRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/axonia/communities/{slug}/chat-destinations")
@RequiredArgsConstructor
public class ChatDestinationController {

    private final ChatDestinationService chatDestinationService;

    @GetMapping
    List<ChatDestination> list(@PathVariable String slug) {

        return chatDestinationService.list(slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ChatDestination create(@PathVariable String slug, @Valid @RequestBody CreateChatDestinationRequest request) {

        return chatDestinationService.create(slug, request);
    }

    @PatchMapping("/{id}")
    ChatDestination update(@PathVariable String slug, @PathVariable long id,
            @Valid @RequestBody UpdateChatDestinationRequest request) {

        return chatDestinationService.update(slug, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable String slug, @PathVariable long id) {

        chatDestinationService.remove(slug, id);
    }

    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void test(@PathVariable String slug, @PathVariable long id) {

        chatDestinationService.test(slug, id);
    }
}
