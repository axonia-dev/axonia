package dev.axonia.learning.application.port.out;

import java.util.List;
import java.util.Map;

import dev.axonia.learning.api.OpenFace;

/**
 * Who is open to questions in the community, which the expertise context decides: per canonical area, in the order it
 * lists them. An area missing from it has nobody open. Reads the caller's organization from the request.
 */
public interface OpenPeople {

    Map<String, List<OpenFace>> byArea(String slug);
}
