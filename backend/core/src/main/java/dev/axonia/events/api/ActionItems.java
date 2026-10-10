package dev.axonia.events.api;

public interface ActionItems {

    ActionItem raise(String slug, RaiseActionItemRequest request);
}
