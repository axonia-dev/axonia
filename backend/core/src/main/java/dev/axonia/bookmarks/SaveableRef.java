package dev.axonia.bookmarks;

public record SaveableRef(SaveableType type, long id) {

    public static SaveableRef artifact(long id) {
        return new SaveableRef(SaveableType.ARTIFACT, id);
    }

    public static SaveableRef session(long id) {
        return new SaveableRef(SaveableType.SESSION, id);
    }
}
