package dev.axonia.identity.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.axonia.identity.api.DirectoryUser;
import dev.axonia.identity.api.OrganizationMembershipView;
import dev.axonia.identity.application.AvatarOverrideService;
import dev.axonia.identity.application.MyOrganizationsService;
import dev.axonia.identity.application.User;
import dev.axonia.identity.application.UserService;
import dev.axonia.media.api.ImageUploads;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_DIRECTORY_IDS = 100;

    private final UserService userService;
    private final MyOrganizationsService myOrganizationsService;
    private final AvatarOverrideService avatarOverrideService;

    /** The caller's own profile (provisions on the spot if absent). */
    @GetMapping("/me")
    User me() {

        return userService.me();
    }

    /**
     * The member's own picture. Returns the refreshed profile so the caller re-renders from one response.
     *
     * <p>
     * The blob {@code set} superseded is deleted here, after {@code set} has returned - its transaction has by then
     * already committed, so a commit failure never leaves the profile pointing at a blob that is already gone.
     */
    @PutMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    User setAvatar(@RequestParam("file") MultipartFile file) {

        var previousKey = avatarOverrideService.set(ImageUploads.from(file));
        avatarOverrideService.deletePreviousBlob(previousKey);
        return userService.me();
    }

    /** Same after-commit ordering as {@link #setAvatar(MultipartFile)}, for the blob {@code clear} superseded. */
    @DeleteMapping("/me/avatar")
    User clearAvatar() {

        var previousKey = avatarOverrideService.clear();
        avatarOverrideService.deletePreviousBlob(previousKey);
        return userService.me();
    }

    /**
     * The member's own clock. Returns the refreshed profile so the caller re-renders from one response, the same shape
     * {@link #setAvatar(MultipartFile)} has.
     */
    @PutMapping("/me/time-zone")
    User declareTimeZone(@Valid @RequestBody TimeZoneRequest request) {

        return userService.declareTimeZone(request.zone());
    }

    /** Drops it, falling back to whatever this member's browser last reported. */
    @DeleteMapping("/me/time-zone")
    User clearTimeZone() {

        return userService.clearDeclaredTimeZone();
    }

    /**
     * The browser reporting itself. The frontend sends this only when its reading has drifted from the recorded one.
     */
    @PutMapping("/me/time-zone/observed")
    User observeTimeZone(@Valid @RequestBody TimeZoneRequest request) {

        return userService.observeTimeZone(request.zone());
    }

    /**
     * The Organizations the caller's identity can switch into; authorizes on {@code sub} only, ignores the {@code org}
     * claim.
     */
    @GetMapping("/me/organizations")
    List<OrganizationMembershipView> myOrganizations() {

        return myOrganizationsService.myOrganizations();
    }

    /**
     * Batch resolve profiles for display; organization-scoped, minimized projection. Total, not partial: an id that
     * resolves to no row still comes back, as an erased entry under the port's neutral label, rather than being omitted
     * from the batch.
     */
    @GetMapping("/users")
    List<DirectoryUser> resolve(@RequestParam List<String> ids) {

        if (ids.size() > MAX_DIRECTORY_IDS) {
            throw new ValidationException("too_many_ids",
                    "At most " + MAX_DIRECTORY_IDS + " ids can be resolved per request");
        }
        return userService.resolve(ids);
    }
}
