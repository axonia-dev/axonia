package dev.axonia.recognition.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import dev.axonia.recognition.application.port.out.RecognitionStore;
import dev.axonia.recognition.domain.Recognition;
import lombok.RequiredArgsConstructor;

/** The recognized contributions a member is credited with today: slots they present and artifacts they captured. */
@Component
@RequiredArgsConstructor
public class CreditedRecognitions {

    private final RecognitionStore recognitionStore;
    private final SlotSourceAccess slotSourceAccess;
    private final ArtifactSourceAccess artifactSourceAccess;

    @Transactional(readOnly = true)
    public List<Recognition> of(String organizationId, String userId) {

        var credited = new ArrayList<Recognition>();
        credited.addAll(recognizedOf(organizationId, ContributionType.SLOT_PRESENTED,
                slotSourceAccess.slotsPresentedBy(organizationId, userId)));
        credited.addAll(recognizedOf(organizationId, ContributionType.ARTIFACT_CAPTURED,
                artifactSourceAccess.artifactsCapturedBy(organizationId, userId)));
        return credited;
    }

    private List<Recognition> recognizedOf(String organizationId, ContributionType type, List<Long> contributionIds) {

        if (contributionIds.isEmpty()) {
            return List.of();
        }
        return recognitionStore.findAll(organizationId, type, contributionIds);
    }
}
