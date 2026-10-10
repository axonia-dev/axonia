package dev.axonia.identity.application.scim;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.SuccessionAdoption;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserArrival;

@Service
public class ScimUserService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final Pattern EQ_FILTER = Pattern.compile("^(\\w+)\\s+eq\\s+\"([^\"]+)\"$",
            Pattern.CASE_INSENSITIVE);
    private static final String ENT = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:";

    private final UserStore userStore;
    private final ScimUserMapper mapper;
    private final SuccessionAdoption successions;

    public ScimUserService(UserStore userStore, ScimUserMapper mapper, SuccessionAdoption successions) {

        this.userStore = userStore;
        this.mapper = mapper;
        this.successions = successions;
    }

    @Transactional
    public ScimUser create(ScimPrincipal p, ScimUser in) {

        String externalId = mapper.getExternalId(in);
        String email = mapper.getUserName(in);

        if (externalId != null) {
            userStore.findByOrganizationIdAndExternalId(p.organizationId(), externalId).ifPresent(existing -> {
                throw ScimException.uniquenessConflict("User with externalId already exists: " + externalId);
            });
        }

        // A replacement directory issues fresh externalIds for everybody, so without this a customer who runs SCIM
        // gets a doubled roster before anybody has signed in. Under a succession their administrator declared, the
        // address this push carries is the same person the old directory pushed, so the row is adopted rather than
        // duplicated - which is also what keeps UserService.upsertProfile's second-row consequence from arising at all
        // for these customers.
        //
        // After the externalId conflict rather than before it: a directory re-pushing an id it already provisioned is
        // not migrating anybody, and adopting there would stamp that id onto a second row for
        // uq_identity_user_organization_external to refuse as a 500 instead of the uniqueness conflict a directory can
        // act on. Before the address conflict, because that conflict is exactly what adoption exists to answer
        // differently.
        var adopted = successions.adopt(p.organizationId(),
                SuccessionAdoption.DirectoryPush.builder().externalId(externalId).email(email)
                        .displayName(mapper.getDisplayName(in)).businessUnit(mapper.getBusinessUnit(in))
                        .department(mapper.getDepartment(in)).team(mapper.getTeam(in)).build());
        if (adopted.isPresent()) {
            return mapper.toScim(adopted.get());
        }

        if (email != null) {
            userStore.findByOrganizationIdAndEmail(p.organizationId(), email).ifPresent(existing -> {
                throw ScimException.uniquenessConflict("User with userName already exists: " + email);
            });
        }

        String scimId = UUID.randomUUID().toString();

        UserAccount toSave = UserAccount.builder().organizationId(p.organizationId()).scimId(scimId)
                .externalId(externalId).displayName(mapper.getDisplayName(in)).email(email).active(true)
                .arrival(UserArrival.SYNCED).businessUnit(mapper.getBusinessUnit(in))
                .department(mapper.getDepartment(in)).team(mapper.getTeam(in)).build();

        UserAccount saved = userStore.save(toSave);

        return mapper.toScim(saved);
    }

    @Transactional(readOnly = true)
    public ScimUser get(ScimPrincipal p, String scimId) {

        UserAccount entity = userStore.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM user found: " + scimId));

        return mapper.toScim(entity);
    }

    @Transactional(readOnly = true)
    public ScimListResponse<ScimUser> list(ScimPrincipal p, String filter) {

        List<UserAccount> users;

        if (filter == null || filter.isBlank()) {
            users = userStore.findByOrganizationId(p.organizationId(), DEFAULT_PAGE_SIZE);
        } else {
            users = applyFilter(p.organizationId(), filter);
        }

        List<ScimUser> scimUsers = users.stream().map(mapper::toScim).toList();

        return ScimListResponse.of(scimUsers.size(), 1, scimUsers.size(), scimUsers);
    }

    @Transactional
    public ScimUser replace(ScimPrincipal p, String scimId, ScimUser in) {

        UserAccount existing = userStore.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM user found: " + scimId));

        boolean nowActive = mapper.getActive(in);

        UserAccount updated = existing.toBuilder().displayName(mapper.getDisplayName(in)).email(mapper.getUserName(in))
                .externalId(mapper.getExternalId(in)).active(nowActive).businessUnit(mapper.getBusinessUnit(in))
                .department(mapper.getDepartment(in)).team(mapper.getTeam(in)).build();

        return mapper.toScim(userStore.save(updated));
    }

    @Transactional
    public ScimUser patch(ScimPrincipal p, String scimId, ScimPatchRequest patch) {

        UserAccount existing = userStore.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM user found: " + scimId));

        UserAccount.UserAccountBuilder builder = existing.toBuilder();

        for (ScimPatchOperation op : patch.operations()) {
            if (!"replace".equalsIgnoreCase(op.op())) {
                continue;
            }
            applyReplaceOp(builder, op.path(), op.value());
        }

        return mapper.toScim(userStore.save(builder.build()));
    }

    public void delete(ScimPrincipal p, String scimId) {

        UserAccount existing = userStore.findByOrganizationIdAndScimId(p.organizationId(), scimId)
                .orElseThrow(() -> ScimException.notFound("No SCIM user found: " + scimId));

        userStore.save(existing.toBuilder().active(false).build());
    }

    // --- private helpers ---

    private void applyReplaceOp(UserAccount.UserAccountBuilder builder, String path, Object value) {

        if ("active".equalsIgnoreCase(path)) {
            // null value means no state change (RFC 7644 §3.5.2)
            if (value != null) {
                builder.active(toBoolean(value));
            }
        } else if ("displayName".equalsIgnoreCase(path)) {
            builder.displayName(value != null ? value.toString() : null);
        } else {
            applyEnterpriseOp(builder, path, value);
        }
    }

    private void applyEnterpriseOp(UserAccount.UserAccountBuilder builder, String path, Object value) {

        String str = value != null ? value.toString() : null;
        if ((ENT + "organization").equalsIgnoreCase(path)) {
            builder.businessUnit(str);
        } else if ((ENT + "division").equalsIgnoreCase(path)) {
            builder.department(str);
        } else if ((ENT + "department").equalsIgnoreCase(path)) {
            builder.team(str);
        }
    }

    private List<UserAccount> applyFilter(String organizationId, String filter) {

        Matcher m = EQ_FILTER.matcher(filter.trim());
        if (!m.matches()) {
            return List.of();
        }

        String attr = m.group(1);
        String value = m.group(2);

        if ("userName".equalsIgnoreCase(attr)) {
            return userStore.findByOrganizationIdAndEmail(organizationId, value).map(List::of).orElse(List.of());
        } else if ("externalId".equalsIgnoreCase(attr)) {
            return userStore.findByOrganizationIdAndExternalId(organizationId, value).map(List::of).orElse(List.of());
        }

        return List.of();
    }

    private boolean toBoolean(Object value) {

        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value != null ? value.toString() : "false");
    }
}
