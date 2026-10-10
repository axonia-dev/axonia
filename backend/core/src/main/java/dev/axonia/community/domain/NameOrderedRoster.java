package dev.axonia.community.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The roster in the order a request asked for it. Display names live in {@code identity}, so the directory answers
 * ascending and knows nothing of the request: honouring a descending sort is this class's job, and serving ascending
 * rows under a {@code display_name,desc} sort would be a wrong answer carrying a 200.
 */
public final class NameOrderedRoster {

    public static final String SORT_KEY = "display_name";

    private NameOrderedRoster() {
    }

    /** Whether the caller asked for the roster by name at all. */
    public static boolean isRequested(Pageable pageable) {

        return pageable.getSort().getOrderFor(SORT_KEY) != null;
    }

    /** {@code ascending} as the directory gave it, reversed when the caller asked for {@code display_name,desc}. */
    public static List<String> inRequestedDirection(List<String> ascending, Pageable pageable) {

        var order = pageable.getSort().getOrderFor(SORT_KEY);
        if (order == null || order.getDirection() != Sort.Direction.DESC) {
            return ascending;
        }
        var reversed = new ArrayList<>(ascending);
        Collections.reverse(reversed);
        return reversed;
    }
}
