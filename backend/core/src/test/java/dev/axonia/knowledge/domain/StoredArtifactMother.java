package dev.axonia.knowledge.domain;

import java.util.Set;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StoredArtifactMother {

    public StoredArtifact.StoredArtifactBuilder any() {

        return StoredArtifact.builder().organizationId("default").communityId(1L).typeId(1L)
                .title("Resilience patterns talk").description("How we handle retries and timeouts")
                .url("https://example.com/talk").contributorUserId("dev-user").status("ACTIVE")
                .searchDocument("Resilience patterns talk How we handle retries and timeouts resilience")
                .tags(Set.of("resilience"));
    }
}
