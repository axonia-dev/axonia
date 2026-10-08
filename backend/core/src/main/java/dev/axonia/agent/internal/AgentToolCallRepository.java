package dev.axonia.agent.internal;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

interface AgentToolCallRepository extends CrudRepository<AgentToolCallEntity, Long> {

    List<AgentToolCallEntity> findByOrganizationId(String organizationId);

    void deleteByOrganizationId(String organizationId);
}
