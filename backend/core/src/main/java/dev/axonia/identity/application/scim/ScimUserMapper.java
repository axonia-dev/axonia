package dev.axonia.identity.application.scim;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.identity.domain.UserAccount;

@Component
public class ScimUserMapper {

    public ScimUser toScim(UserAccount entity) {

        String email = entity.email();
        String userName = email != null ? email : entity.externalId();
        List<ScimEmail> emails = email != null ? List.of(new ScimEmail(email, "work", true)) : List.of();
        boolean active = !Boolean.FALSE.equals(entity.active());
        ScimMeta meta = new ScimMeta("User", null, null, null);

        // Axonia → SCIM: business_unit→organization, department→division, team→department.
        ScimEnterpriseUser enterprise = null;
        if (entity.businessUnit() != null || entity.department() != null || entity.team() != null) {
            enterprise = new ScimEnterpriseUser(entity.businessUnit(), entity.department(), entity.team());
        }

        return ScimUser.builder().id(entity.scimId()).externalId(entity.externalId()).userName(userName)
                .displayName(entity.displayName()).emails(emails).active(active).meta(meta).enterprise(enterprise)
                .build();
    }

    public String getUserName(ScimUser scimUser) {

        return scimUser.userName();
    }

    public String getDisplayName(ScimUser scimUser) {

        return scimUser.displayName();
    }

    public boolean getActive(ScimUser scimUser) {

        return scimUser.active();
    }

    public String getExternalId(ScimUser scimUser) {

        return scimUser.externalId();
    }

    /** SCIM enterprise {@code organization} → Axonia {@code business_unit}. */
    public String getBusinessUnit(ScimUser scimUser) {

        return scimUser.enterprise() != null ? scimUser.enterprise().organization() : null;
    }

    /** SCIM enterprise {@code division} → Axonia {@code department}. */
    public String getDepartment(ScimUser scimUser) {

        return scimUser.enterprise() != null ? scimUser.enterprise().division() : null;
    }

    /**
     * SCIM enterprise {@code department} → Axonia {@code team} (SCIM has no native 'team'; its finest is department).
     */
    public String getTeam(ScimUser scimUser) {

        return scimUser.enterprise() != null ? scimUser.enterprise().department() : null;
    }
}
