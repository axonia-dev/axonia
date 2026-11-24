package dev.axonia.bookmarks.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.bookmarks.application.BookmarkService;
import dev.axonia.bookmarks.application.SavedItemView;
import dev.axonia.bookmarks.domain.ItemSaveState;
import dev.axonia.bookmarks.domain.SaveState;
import dev.axonia.bookmarks.domain.SaveableRef;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    /** One request for a whole page of cards, rather than one per card: {@code ?ids=1,2,3}. */
    @GetMapping("/artifacts/save-states")
    List<ItemSaveState> artifactStates(@RequestParam List<Long> ids) {
        return bookmarkService.artifactSaveStates(ids);
    }

    @GetMapping("/artifacts/{id}/save")
    SaveState artifactState(@PathVariable Long id) {
        return bookmarkService.saveState(SaveableRef.artifact(id));
    }

    @PostMapping("/artifacts/{id}/save")
    @ResponseStatus(HttpStatus.CREATED)
    SaveState saveArtifact(@PathVariable Long id) {
        return bookmarkService.save(SaveableRef.artifact(id));
    }

    @DeleteMapping("/artifacts/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsaveArtifact(@PathVariable Long id) {
        bookmarkService.unsave(SaveableRef.artifact(id));
    }

    @PutMapping("/artifacts/{id}/helpful")
    SaveState helpful(@PathVariable Long id, @RequestBody HelpfulRequest request) {
        return bookmarkService.markHelpful(id, request.helped());
    }

    @GetMapping("/sessions/{id}/save")
    SaveState sessionState(@PathVariable Long id) {
        return bookmarkService.saveState(SaveableRef.session(id));
    }

    @PostMapping("/sessions/{id}/save")
    @ResponseStatus(HttpStatus.CREATED)
    SaveState saveSession(@PathVariable Long id) {
        return bookmarkService.save(SaveableRef.session(id));
    }

    @DeleteMapping("/sessions/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsaveSession(@PathVariable Long id) {
        bookmarkService.unsave(SaveableRef.session(id));
    }

    @GetMapping("/me/saved")
    List<SavedItemView> mySaved() {
        return bookmarkService.mySaved();
    }
}
