package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.domain.PlannedSection;
import dev.axonia.events.domain.SeriesRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcSeriesStore implements SeriesStore {

    private final SeriesRepository seriesRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SeriesRecord> findByOrganizationIdAndId(String organizationId, Long id) {

        return seriesRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcSeriesStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeriesRecord> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids) {

        return seriesRepository.findByOrganizationIdAndIdIn(organizationId, ids).stream().map(JdbcSeriesStore::toRecord)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeriesRecord> findByOrganizationIdAndCommunityId(String organizationId, Long communityId) {

        return seriesRepository.findByOrganizationIdAndCommunityId(organizationId, communityId).stream()
                .map(JdbcSeriesStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SeriesRecord> findByOrganizationIdAndCommunityId(String organizationId, Long communityId,
            Pageable pageable) {

        return seriesRepository.findByOrganizationIdAndCommunityId(organizationId, communityId, pageable)
                .map(JdbcSeriesStore::toRecord);
    }

    @Override
    @Transactional
    public SeriesRecord save(SeriesRecord series) {

        return toRecord(seriesRepository.save(toEntity(series)));
    }

    static SeriesRecord toRecord(SeriesEntity entity) {

        return SeriesRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .communityId(entity.getCommunityId()).title(entity.getTitle()).cadenceCount(entity.getCadenceCount())
                .cadenceUnit(entity.getCadenceUnit()).startsOn(entity.getStartsOn())
                .preferredTime(entity.getPreferredTime()).timeZone(entity.getTimeZone())
                .durationMinutes(entity.getDurationMinutes()).status(entity.getStatus()).location(entity.getLocation())
                .joinUrl(entity.getJoinUrl()).joiningDetails(entity.getJoiningDetails())
                .defaultStructure(RecordMapping.list(entity.getDefaultStructure(),
                        ref -> PlannedSection.builder().sectionTypeId(ref.getSectionTypeId()).title(ref.getTitle())
                                .durationMinutes(ref.getDurationMinutes()).build()))
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static SeriesEntity toEntity(SeriesRecord series) {

        return SeriesEntity.builder().id(series.getId()).organizationId(series.getOrganizationId())
                .communityId(series.getCommunityId()).title(series.getTitle()).cadenceCount(series.getCadenceCount())
                .cadenceUnit(series.getCadenceUnit()).startsOn(series.getStartsOn())
                .preferredTime(series.getPreferredTime()).timeZone(series.getTimeZone())
                .durationMinutes(series.getDurationMinutes()).status(series.getStatus()).location(series.getLocation())
                .joinUrl(series.getJoinUrl()).joiningDetails(series.getJoiningDetails())
                .defaultStructure(RecordMapping.list(series.getDefaultStructure(),
                        planned -> SectionTypeRef.builder().sectionTypeId(planned.getSectionTypeId())
                                .title(planned.getTitle()).durationMinutes(planned.getDurationMinutes()).build()))
                .createdAt(series.getCreatedAt()).updatedAt(series.getUpdatedAt()).version(series.getVersion()).build();
    }
}
