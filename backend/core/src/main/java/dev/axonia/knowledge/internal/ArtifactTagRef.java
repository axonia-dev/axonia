package dev.axonia.knowledge.internal;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@Table("artifact_tag")
public class ArtifactTagRef {

    private String tag;
}
