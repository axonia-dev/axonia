package dev.axonia.organization;

/**
 * Outbound port: how many people an Organization's user directory currently holds.
 *
 * <p>
 * It is the default answer to how many members an Organization has (see {@link MemberCountPolicy}): an enterprise
 * tenant's members arrive through the customer's directory, so asking that directory is the truthful count, and this
 * port is how the organization module asks without reaching into the identity module's tables.
 */
public interface DirectoryHeadcount {

    /**
     * People present in the given Organization's directory: those who exist and have not been deactivated. Deactivated
     * (SCIM {@code active = false}) and erased people are excluded, so this matches the "active members" figure any
     * other report of the same tenant uses.
     */
    long presentMembers(String organizationId);
}
