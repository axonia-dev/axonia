package dev.axonia.privacy.application.port.out;

import java.util.List;

import dev.axonia.privacy.domain.ExportHeader;
import dev.axonia.shared.api.personaldata.ExportFragment;

/** The human-readable page an export package opens on, beside its machine-readable files. */
public interface ExportIndexPage {

    String render(ExportHeader header, List<ExportFragment> fragments);
}
