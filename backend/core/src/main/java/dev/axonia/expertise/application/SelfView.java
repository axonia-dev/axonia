package dev.axonia.expertise.application;

import java.util.List;

import dev.axonia.expertise.domain.DeclarableCommunity;
import dev.axonia.expertise.domain.SelfDeclaration;

public record SelfView(boolean optedOut, boolean paused, List<SelfArea> areas, List<SelfDeclaration> declarations,
        List<DeclarableCommunity> declarableCommunities) {
}
