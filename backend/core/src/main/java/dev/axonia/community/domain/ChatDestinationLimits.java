package dev.axonia.community.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ChatDestinationLimits {

    public static final int NAME_MAX = 80;
    public static final int WEBHOOK_URL_MAX = 2048;

    /** Shared between the service's name check and the store's constraint race, so the two copies cannot drift. */
    public static final String NAME_TAKEN_MESSAGE = "This community already has a destination with that name.";
}
