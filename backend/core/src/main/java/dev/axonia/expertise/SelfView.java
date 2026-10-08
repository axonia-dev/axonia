package dev.axonia.expertise;

import java.util.List;

public record SelfView(boolean optedOut, List<SelfArea> areas, List<SelfDeclaration> declarations,
        List<DeclarableCommunity> declarableCommunities) {
}
