package dev.axonia.events.adapter.out.persistence;

import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@Table("topic_tag")
public class TopicTagRef {

    private String tag;
}
