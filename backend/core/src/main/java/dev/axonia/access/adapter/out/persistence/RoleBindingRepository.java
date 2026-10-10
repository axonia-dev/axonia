package dev.axonia.access.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface RoleBindingRepository extends CrudRepository<RoleBindingEntity, Long> {
    List<RoleBindingEntity> findByOrganizationId(String organizationId);
    List<RoleBindingEntity> findByRoleId(Long roleId);
    boolean existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId(String organizationId, String subjectType,
            String subjectId, Long roleId);
    boolean existsByOrganizationIdAndSubjectTypeAndSubjectId(String organizationId, String subjectType,
            String subjectId);
    List<RoleBindingEntity> findByOrganizationIdAndSubjectTypeAndSubjectId(String organizationId, String subjectType,
            String subjectId);

    /**
     * The distinct subjects of one type - {@code USER} ids or {@code GROUP} reconciliation keys - bound in this
     * organization to a role carrying the given permission. Deduplicated in the query, not in Java: a subject bound
     * through two roles that both carry the permission must be named once, and a naive join names them once per
     * matching role.
     */
    @Query("""
            SELECT DISTINCT rb.subject_id FROM role_binding rb
            JOIN role_permission rp ON rp.role_id = rb.role_id
            WHERE rb.organization_id = :organizationId AND rb.subject_type = :subjectType
              AND rp.permission = :permissionCode
            """)
    List<String> findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission(
            @Param("organizationId") String organizationId, @Param("subjectType") String subjectType,
            @Param("permissionCode") String permissionCode);
}
