package dev.axonia.privacy.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import dev.axonia.ExportFragment;
import dev.axonia.ExportScope;
import dev.axonia.OrganizationScope;
import dev.axonia.PersonalDataExporter;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.SubjectScope;
import dev.axonia.identity.UserDirectory;
import dev.axonia.identity.UserDirectory.DirectoryUser;
import dev.axonia.media.MediaStorage;
import dev.axonia.privacy.DataExport;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds an export package straight into the caller's {@link OutputStream} - one {@link ZipOutputStream}, one entry per
 * fragment, media streamed from {@link MediaStorage} as each key is reached. Nothing is written to a temp file first,
 * so a failure partway through simply leaves the stream truncated before the ZIP central directory is ever written: the
 * caller gets an archive that cannot be opened at all, rather than one that opens looking complete while silently
 * missing whatever came after the failure. Media alone is genuinely lazy, though: every fragment's JSON is serialized
 * into memory up front by {@link #plan}, so a whole-Organization export holds all of it before the first ZIP byte
 * leaves. Streaming that too is a real improvement and a deliberately separate change.
 *
 * <p>
 * Every entry sits under one root folder named by {@link ExportPackageName}, which is also what the {@code .zip} itself
 * is called. Two exports extracted into the same directory therefore cannot overwrite each other, and a media key is
 * checked to resolve inside that root before it becomes an entry name - keys are server-generated today, so the check
 * exists to make the guarantee structural rather than a property of who happens to write them.
 *
 * <p>
 * Fragments that name the same file are merged into one first, by {@link ExportFiles}, so two modules can hold parts of
 * one record without the reader seeing two files. Every fragment's JSON file and every media key it references share
 * one {@link TreeMap}, sorted by the name each will get in the archive, so the layout does not depend on the order
 * {@link PersonalDataExporter} beans happen to be wired in. A media key's entry is only opened via
 * {@link MediaStorage#open} at the moment its turn in that sorted order comes up to be written, and closed immediately
 * after - never before, and never all at once up front. A {@link ExportScope.WholeOrganization} export can carry one
 * media key per member; opening every one of them before writing the first byte would hold that many storage
 * connections open simultaneously for the entire, potentially slow, remainder of the write. {@code manifest.json} and
 * {@code index.html} are always the last two entries, regardless of where their names would otherwise sort, because the
 * manifest can only be built once every media key has been tried and it is known which ones actually resolved.
 *
 * <p>
 * Nothing that reaches the archive names a person or a tenant by an identifier. The assembled fragments pass through
 * {@link ExportSubjectNaming} before either the JSON entries or {@code index.html} see them, so both read the same
 * rows, and the manifest, the index header and the package's own name are built from the Organization's name and the
 * member's rather than from the ids the request arrived with. Doing that once here, on the assembled package, is the
 * whole point: the reasoning is in {@link ExportSubjectNaming}.
 *
 * <p>
 * Runs the whole read entirely inside {@link OrganizationScope#runAs} for an Organization's export, or
 * {@link SubjectScope#runAs} for a {@link ExportScope.Person}'s, never inside a transaction: each
 * {@link PersonalDataExporter} manages its own short-lived reads, so no database connection is held open for the full,
 * potentially slow, duration of streaming a response to a client.
 */
@Component
@RequiredArgsConstructor
class ExportPackageWriter implements DataExport {

    private static final String MANIFEST_FILE = "manifest.json";
    private static final String INDEX_FILE = "index.html";

    private static final String JSON_SUFFIX = ".json";

    private final List<PersonalDataExporter> exporters;
    private final MediaStorage mediaStorage;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final ExportPackageNames packageNames;
    private final List<PersonalDataFootprint> footprints;
    private final UserDirectory directory;

    @Override
    public void writeTo(ExportScope scope, OutputStream out) throws IOException {

        try {
            switch (scope) {
                case ExportScope.InOrganization organization ->
                    OrganizationScope.runAs(organization.organizationId(), () -> writeUnchecked(scope, out));
                case ExportScope.Person person ->
                    SubjectScope.runAs(person.subject(), () -> writeUnchecked(scope, out));
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private void writeUnchecked(ExportScope scope, OutputStream out) {

        try {
            write(scope, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(ExportScope scope, OutputStream out) throws IOException {

        var naming = packageNames.of(scope);
        var fragments = ExportSubjectNaming.appliedTo(scope, ExportFiles.mergedByFileName(exporters.stream()
                .map(exporter -> exporter.export(scope)).filter(fragment -> !fragment.holdsNothing()).toList()),
                footprints, directory);
        var plan = plan(fragments);
        var missingMedia = new ArrayList<String>();
        var generatedAt = clock.instant();
        var header = headerOf(scope, naming.organizationName().orElse(null), generatedAt);
        var root = naming.root(LocalDate.ofInstant(generatedAt, ZoneOffset.UTC));

        try (var zip = new ZipOutputStream(out)) {
            for (var planned : plan.entrySet()) {
                writeEntry(zip, root, planned, missingMedia);
            }

            var files = new ArrayList<>(plan.keySet());
            files.removeAll(missingMedia);
            files.add(INDEX_FILE);

            writeBytes(zip, root, INDEX_FILE,
                    ExportIndexHtml.render(header, fragments).getBytes(StandardCharsets.UTF_8));
            writeBytes(zip, root, MANIFEST_FILE,
                    jsonMapper.writeValueAsBytes(manifestOf(scope, header, files, missingMedia)));
        }
    }

    /**
     * The member is resolved through the directory port even though the request already carries their id, because the
     * package must name them the way a reader would. The port is total, so a subject it cannot resolve still yields a
     * label rather than leaving the header blank.
     */
    private ExportHeader headerOf(ExportScope scope, String organizationName, Instant generatedAt) {

        var member = memberOf(scope);
        return new ExportHeader(organizationName, member.map(DirectoryUser::displayName).orElse(null),
                member.map(DirectoryUser::email).orElse(null), generatedAt);
    }

    private Optional<DirectoryUser> memberOf(ExportScope scope) {

        if (scope instanceof ExportScope.OneMember member) {
            return Optional.of(directory.byIds(member.organizationId(), List.of(member.userId())).getFirst());
        }
        return Optional.empty();
    }

    /**
     * Resolves an entry's name inside the archive's root folder and refuses anything that climbs back out of it. The
     * manifest lists names relative to that root, which is what a reader of the extracted folder actually sees.
     */
    private static String entryName(String root, String name) {

        var resolved = Path.of(root).resolve(name).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalStateException("Export entry '" + name + "' resolves outside the package root");
        }
        return resolved.toString();
    }

    private TreeMap<String, PlannedEntry> plan(List<ExportFragment> fragments) {

        var plan = new TreeMap<String, PlannedEntry>();
        for (var fragment : fragments) {
            plan.put(fragment.fileName(), new PlannedEntry.Json(jsonMapper.writeValueAsBytes(fragment.payload())));
            var index = 0;
            for (var mediaKey : fragment.mediaKeys()) {
                plan.put(mediaEntryName(fragment.fileName(), ++index, mediaKey), new PlannedEntry.Media(mediaKey));
            }
        }
        return plan;
    }

    /**
     * A storage key is not a filename. An avatar's key is {@code avatars/<organizationId>/<uuid>.<ext>}, so naming the
     * archive entry after it would print the tenant identifier on every export of a member who has a picture - inside
     * the ZIP's own directory and again in {@code manifest.json}, the two places nothing else in this change reaches.
     * The entry is named for the fragment that asked for it and its position in that fragment, which is stable, says
     * what the file is, and is derived from nothing the reader may not see. Only the extension survives from the key,
     * reduced to alphanumerics, because it is what makes the file open when double-clicked.
     *
     * <p>
     * {@code identity-1.jpg} rather than {@code media/identity-1.jpg}: it sits beside the {@code identity.json} that
     * references it, and {@code -} sorts before {@code .}, so a fragment's media still precedes the fragment itself in
     * the archive. {@code ExportPackageWriterTest} depends on that ordering to truncate inside the first entry.
     */
    private static String mediaEntryName(String fileName, int index, String mediaKey) {

        var base = fileName.endsWith(JSON_SUFFIX)
                ? fileName.substring(0, fileName.length() - JSON_SUFFIX.length())
                : fileName;
        return "%s-%d%s".formatted(base, index, extensionOf(mediaKey));
    }

    private static String extensionOf(String mediaKey) {

        var dot = mediaKey.lastIndexOf('.');
        if (dot < 0 || dot < mediaKey.lastIndexOf('/') || dot == mediaKey.length() - 1) {
            return "";
        }
        var reduced = mediaKey.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
        return reduced.isEmpty() ? "" : "." + reduced;
    }

    private void writeEntry(ZipOutputStream zip, String root, Map.Entry<String, PlannedEntry> planned,
            List<String> missingMedia) throws IOException {

        switch (planned.getValue()) {
            case PlannedEntry.Json json -> writeBytes(zip, root, planned.getKey(), json.bytes());
            case PlannedEntry.Media media -> {
                if (!writeMedia(zip, root, planned.getKey(), media.key())) {
                    missingMedia.add(planned.getKey());
                }
            }
        }
    }

    /**
     * Opens the key only now, and closes it before returning - never held open past its own entry. The whole
     * archive-writing sequence, including {@code putNextEntry} itself, runs inside the try-with-resources: a truncated
     * underlying stream fails exactly there (the local file header, written by {@code putNextEntry}, is where a cutoff
     * this early always lands), and the resource must still close on that path, not only when {@code transferTo} is
     * what throws.
     *
     * <p>
     * Returns whether the blob was there, rather than taking the {@code missingMedia} list as a fifth parameter: the
     * caller already holds the list and the entry's name, and five parameters is where this repo's PMD ruleset draws
     * the line.
     */
    private boolean writeMedia(ZipOutputStream zip, String root, String name, String key) throws IOException {

        var entryName = entryName(root, name);
        var opened = mediaStorage.open(key);
        if (opened.isEmpty()) {
            return false;
        }
        try (var in = opened.get().stream()) {
            zip.putNextEntry(new ZipEntry(entryName));
            in.transferTo(zip);
            zip.closeEntry();
        }
        return true;
    }

    private void writeBytes(ZipOutputStream zip, String root, String name, byte[] data) throws IOException {

        zip.putNextEntry(new ZipEntry(entryName(root, name)));
        zip.write(data);
        zip.closeEntry();
    }

    private Map<String, Object> manifestOf(ExportScope scope, ExportHeader header, List<String> files,
            List<String> missingMedia) {

        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("scope", scope.getClass().getSimpleName());
        manifest.put("organization", header.organization());
        if (header.member() != null) {
            manifest.put("member", header.member());
        }
        manifest.put("generatedAt", header.generatedAt().toString());
        manifest.put("files", files);
        manifest.put("missingMedia", missingMedia);
        manifest.put("note", exclusionNoteFor(scope));
        return manifest;
    }

    /**
     * The honest sentence from {@link dev.axonia.ExportFragment}'s javadoc: a member's export is not symmetric with
     * their erasure. Erasure reaches rows where somebody else merely referenced them; export never does, because those
     * rows are that other person's data, not theirs. A whole-Organization export has no such asymmetry - every module
     * reads every row for that Organization, filtered by nobody's identity - so it gets a different note.
     */
    private String exclusionNoteFor(ExportScope scope) {

        return switch (scope) {
            case ExportScope.OneMember _ -> "This export carries rows where this person is the actor or subject. It "
                    + "deliberately does not carry rows where they are merely referenced by somebody else's action - "
                    + "those are other people's data.";
            case ExportScope.WholeOrganization _ -> "This export carries every member's data across the "
                    + "Organization, with no filtering by contributor identity.";
            case ExportScope.Person _ -> "This export carries the data held about this person apart from any "
                    + "Organization: their account, their standing and their onboarding. Data held inside an "
                    + "Organization is exported from within that Organization.";
        };
    }

    private sealed interface PlannedEntry {

        record Json(byte[] bytes) implements PlannedEntry {
        }

        record Media(String key) implements PlannedEntry {
        }
    }
}
