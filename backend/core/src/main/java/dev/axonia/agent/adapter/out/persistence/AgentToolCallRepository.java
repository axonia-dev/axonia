package dev.axonia.agent.adapter.out.persistence;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

interface AgentToolCallRepository extends CrudRepository<AgentToolCallEntity, Long> {

    List<AgentToolCallEntity> findByOrganizationId(String organizationId);

    void deleteByOrganizationId(String organizationId);
}
