package dev.axonia.identity.scim;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.identity.internal.UserEntity;

@Component
public class ScimUserMapper {

    public ScimUser toScim(UserEntity entity) {

        String email = entity.getEmail();
        String userName = email != null ? email : entity.getExternalId();
        List<ScimEmail> emails = email != null ? List.of(new ScimEmail(email, "work", true)) : List.of();
        boolean active = !Boolean.FALSE.equals(entity.getActive());
        ScimMeta meta = new ScimMeta("User", null, null, null);

        // Axonia → SCIM: business_unit→organization, department→division, team→department.
        ScimEnterpriseUser enterprise = null;
        if (entity.getBusinessUnit() != null || entity.getDepartment() != null || entity.getTeam() != null) {
            enterprise = new ScimEnterpriseUser(entity.getBusinessUnit(), entity.getDepartment(), entity.getTeam());
        }

        return ScimUser.builder().id(entity.getScimId()).externalId(entity.getExternalId()).userName(userName)
                .displayName(entity.getDisplayName()).emails(emails).active(active).meta(meta).enterprise(enterprise)
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
