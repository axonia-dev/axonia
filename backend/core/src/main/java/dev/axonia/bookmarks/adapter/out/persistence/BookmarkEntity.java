package dev.axonia.bookmarks.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.bookmarks.domain.SaveableType;
import dev.axonia.shared.api.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("bookmark")
class BookmarkEntity implements Entity {

    @Id
    private Long id;
    private String organizationId;
    private Long communityId;
    private SaveableType saveableType;
    private Long saveableId;
    private String memberUserId;
    private String sourceAuthorUserId;
    private Boolean helped;
    private Instant helpedAt;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    @Version
    private Long version;
}
