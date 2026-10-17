package dev.axonia.recognition.application.port.out;

import java.util.List;

import dev.axonia.recognition.domain.NewLoveTally;

public interface NewLoveTallies {

    /** Loves by others on the given contributions, after each community's effective marker and up to asOf. */
    List<NewLoveTally> since(NewLoveQuery query);
}
