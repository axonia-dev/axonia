package dev.axonia.bookmarks.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.Classification;
import dev.axonia.ColumnFootprint;
import dev.axonia.DataCategory;
import dev.axonia.Disposition;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.TableFootprint;

/**
 * Bookmarks holds two subjects on one table and they are not the same kind of thing. {@code member_user_id} is the
 * person who saved something: their row, deleted with them. {@code source_author_user_id} is a denormalised copy of the
 * author on somebody else's saved row, so it is anonymised in place - the row belongs to another member and must
 * survive - and it is never exported, because answering one person's access request with three colleagues' saving
 * behaviour would hand out other people's data.
 */
@Component
public class BookmarksPersonalDataFootprint implements PersonalDataFootprint {

    @Override
    public List<TableFootprint> tables() {

        return List.of(TableFootprint.builder().table("bookmark").category(DataCategory.WHAT_YOU_SAVED)
                .personal(List.of(new ColumnFootprint("member_user_id", Classification.SUBJECT, Disposition.DELETE_ROW),
                        new ColumnFootprint("source_author_user_id", Classification.SUBJECT, Disposition.ANONYMISE)))
                .notPersonal(Set.of("id", "organization_id", "saveable_type", "saveable_id", "community_id", "helped",
                        "helped_at", "created_at", "updated_at", "version"))
                .exportedContext(Set.of("saveable_type", "helped", "helped_at", "created_at", "updated_at"))
                .exportedLabels(Set.of("community", "title")).build());
    }
}
