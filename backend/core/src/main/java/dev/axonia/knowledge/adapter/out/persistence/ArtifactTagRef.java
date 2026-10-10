package dev.axonia.knowledge.adapter.out.persistence;

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
