package dev.axonia.identity.api.spi;

import java.util.List;

/**
 * Who in an organization holds a given permission, asked of whichever module owns roles.
 *
 * <p>
 * Declared here and implemented in {@code access}, because {@code access} already imports this module and the reverse
 * would be a cycle {@code ApplicationModules.verify()} refuses.
 *
 * <p>
 * The permission travels as its stable {@code code} rather than as an enum, because the enum lives in {@code access}
 * and importing it is the very edge this port exists to avoid. The adapter resolves it and answers an empty list for a
 * code no permission carries, rather than throwing: a producer asking for the wrong permission should reach nobody, not
 * abort a whole organization's tick.
 */
public interface PermissionHolders {

    List<String> userIdsWith(String organizationId, String permissionCode);
}
