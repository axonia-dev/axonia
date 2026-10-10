package dev.axonia.recognition.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.application.port.out.LoveStore;
import dev.axonia.recognition.domain.GivenLove;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcLoveStore implements LoveStore {

    private final LoveRepository loveRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<GivenLove> find(String organizationId, Long recognizedContributionId, String loverUserId) {
        return loveRepository.findByOrganizationIdAndRecognizedContributionIdAndLoverUserId(organizationId,
                recognizedContributionId, loverUserId).map(JdbcLoveStore::toGivenLove);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GivenLove> ofRecognition(String organizationId, Long recognizedContributionId) {
        return loveRepository.findByOrganizationIdAndRecognizedContributionId(organizationId, recognizedContributionId)
                .stream().map(JdbcLoveStore::toGivenLove).toList();
    }

    @Override
    @Transactional
    public Optional<GivenLove> add(GivenLove love) {
        try {
            return Optional.of(toGivenLove(loveRepository.save(toEntity(love))));
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void remove(GivenLove love) {
        loveRepository.delete(toEntity(love));
    }

    static GivenLove toGivenLove(LoveEntity entity) {
        return GivenLove.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .recognizedContributionId(entity.getRecognizedContributionId()).loverUserId(entity.getLoverUserId())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static LoveEntity toEntity(GivenLove love) {
        return LoveEntity.builder().id(love.id()).organizationId(love.organizationId())
                .recognizedContributionId(love.recognizedContributionId()).loverUserId(love.loverUserId())
                .createdAt(love.createdAt()).updatedAt(love.updatedAt()).version(love.version()).build();
    }
}
