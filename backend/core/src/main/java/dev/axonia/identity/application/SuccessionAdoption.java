package dev.axonia.identity.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.spi.SuccessionAddresses;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * The directory door of a connection succession: the same rule a customer declared, applied to what SCIM pushes.
 *
 * <p>
 * A customer replacing their identity provider generally replaces the directory behind it too, and the new directory
 * issues a fresh {@code externalId} for everybody. Left alone that doubles the roster before a single person has signed
 * in, which defeats the whole point on its own - and the second row is the shape {@code UserService.upsertProfile}'s
 * guard describes as a known and accepted consequence, active and sub-less for good, with the person counted twice.
 * This is what stops that shape arising in the first place, for customers who have a succession, and for nobody else.
 * Whether one is open, and which address it maps an arrival to, is {@link SuccessionAddresses}' to answer.
 */
@Service
@RequiredArgsConstructor
public class SuccessionAdoption {

    private final SuccessionAddresses successions;
    private final UserStore users;

    /**
     * What one directory push says about a person, with nothing SCIM-shaped in it. Its own record rather than six
     * parameters, for PMD's {@code ExcessiveParameterList} threshold of 5.
     */
    @Builder
    public record DirectoryPush(String externalId, String email, String displayName, String businessUnit,
            String department, String team) {
    }

    /**
     * The member this push is really about, or empty when it is about somebody new.
     *
     * <p>
     * <strong>The adopted row keeps its {@code scim_id}</strong>, along with its {@code id}, its subject, how the
     * person arrived and when the row was born. That is the point rather than an implementation detail: reassigning a
     * resource id is the identity swap RFC 7643 forbids, and adoption is its opposite - the resource this answers with
     * is the one the member already was, so the directory's own match-by-userName pre-flight and every link it has
     * already made go on meaning what they meant.
     */
    @Transactional
    public Optional<UserAccount> adopt(String organizationId, DirectoryPush push) {

        return successions.formerAddressOf(organizationId, push.email())
                .flatMap(address -> users.findActiveByEmail(organizationId, address))
                .map(member -> users.save(member.toBuilder().email(push.email()).externalId(push.externalId())
                        .displayName(push.displayName()).businessUnit(push.businessUnit()).department(push.department())
                        .team(push.team()).build()));
    }
}
