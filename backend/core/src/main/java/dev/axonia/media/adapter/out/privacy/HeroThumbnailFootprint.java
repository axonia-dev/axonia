package dev.axonia.media.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * {@code hero_thumbnail} names nobody: it is an image composed from a community asset. It is claimed under
 * {@link DataCategory#WHEN_AXONIA_CONTACTS_YOU} because it exists solely to drive outbound mail, not because anything
 * in it is personal.
 */
@Component
public class HeroThumbnailFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("hero_thumbnail").category(DataCategory.WHEN_AXONIA_CONTACTS_YOU)
                .personal(List.of()).notPersonal(Set.of("id", "organization_id", "community_id", "hero_key",
                        "slice_index", "accent_hex", "media_key", "created_at"))
                .build());
    }
}
