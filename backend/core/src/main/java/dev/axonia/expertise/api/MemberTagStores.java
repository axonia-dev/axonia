package dev.axonia.expertise.api;

/** Opens the {@link MemberTagStore} of a table of {@link MemberTag}s another context owns. */
public interface MemberTagStores {

    <T extends MemberTag> MemberTagStore<T> of(MemberTagKind kind, MemberTagStore.Factory<T> factory);
}
