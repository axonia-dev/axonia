package dev.axonia.access.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.axonia.access.domain.SubjectBinding;

public interface RoleBindingStore {

    Optional<SubjectBinding> find(Long id);

    List<SubjectBinding> listIn(String organizationId);

    List<SubjectBinding> boundTo(Long roleId);

    List<SubjectBinding> findBySubject(String organizationId, String subjectType, String subjectId);

    boolean exists(String organizationId, String subjectType, String subjectId, Long roleId);

    /** Whether any binding in the Organization names this subject, whatever role it grants. */
    boolean namesSubject(String organizationId, String subjectType, String subjectId);

    /** The distinct subjects of this type bound to a role carrying the permission. */
    List<String> subjectsHolding(String organizationId, String subjectType, String permissionCode);

    SubjectBinding save(SubjectBinding binding);

    void delete(SubjectBinding binding);

    void deleteById(Long id);
}
