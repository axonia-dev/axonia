package dev.axonia.access.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.domain.SubjectBinding;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcRoleBindingStore implements RoleBindingStore {

    private final RoleBindingRepository bindingRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SubjectBinding> find(Long id) {

        return bindingRepository.findById(id).map(JdbcRoleBindingStore::toBinding);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectBinding> listIn(String organizationId) {

        return toBindings(bindingRepository.findByOrganizationId(organizationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectBinding> boundTo(Long roleId) {

        return toBindings(bindingRepository.findByRoleId(roleId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectBinding> findBySubject(String organizationId, String subjectType, String subjectId) {

        return toBindings(bindingRepository.findByOrganizationIdAndSubjectTypeAndSubjectId(organizationId, subjectType,
                subjectId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String organizationId, String subjectType, String subjectId, Long roleId) {

        return bindingRepository.existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId(organizationId, subjectType,
                subjectId, roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean namesSubject(String organizationId, String subjectType, String subjectId) {

        return bindingRepository.existsByOrganizationIdAndSubjectTypeAndSubjectId(organizationId, subjectType,
                subjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> subjectsHolding(String organizationId, String subjectType, String permissionCode) {

        return bindingRepository.findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission(organizationId,
                subjectType, permissionCode);
    }

    @Override
    @Transactional
    public SubjectBinding save(SubjectBinding binding) {

        return toBinding(bindingRepository.save(toEntity(binding)));
    }

    @Override
    @Transactional
    public void delete(SubjectBinding binding) {

        bindingRepository.delete(toEntity(binding));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {

        bindingRepository.deleteById(id);
    }

    private static List<SubjectBinding> toBindings(List<RoleBindingEntity> entities) {

        return entities.stream().map(JdbcRoleBindingStore::toBinding).toList();
    }

    static SubjectBinding toBinding(RoleBindingEntity entity) {

        return SubjectBinding.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .subjectType(entity.getSubjectType()).subjectId(entity.getSubjectId())
                .subjectLabel(entity.getSubjectLabel()).roleId(entity.getRoleId()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static RoleBindingEntity toEntity(SubjectBinding binding) {

        return RoleBindingEntity.builder().id(binding.id()).organizationId(binding.organizationId())
                .subjectType(binding.subjectType()).subjectId(binding.subjectId()).subjectLabel(binding.subjectLabel())
                .roleId(binding.roleId()).createdAt(binding.createdAt()).updatedAt(binding.updatedAt())
                .version(binding.version()).build();
    }
}
