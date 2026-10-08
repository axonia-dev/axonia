package dev.axonia.privacy.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.Classification;
import dev.axonia.ColumnFootprint;
import dev.axonia.DataCategory;
import dev.axonia.Disposition;
import dev.axonia.ExportFragment;
import dev.axonia.ExportScope;
import dev.axonia.PersonalDataExporter;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.SubjectAuthentication;
import dev.axonia.TableFootprint;
import dev.axonia.identity.UserDirectory;
import dev.axonia.identity.UserDirectory.DirectoryUser;
import dev.axonia.identity.UserDirectory.MemberStatus;
import dev.axonia.media.MediaContent;
import dev.axonia.media.MediaStorage;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationService;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code identity}'s fake fragment carries a media key ({@link #LONG_MEDIA_KEY}) that is deliberately longer than a ZIP
 * local file header's fixed 30 bytes plus a short name - it sorts alphabetically before every other JSON/media entry
 * this writer produces, and {@code manifest.json}/{@code index.html} are always the last two entries regardless of
 * their own alphabetical position, so the media key is always the very first bytes written. That is what makes
 * {@link #aTruncatedStreamProducesAnArchiveThatCannotBeOpenedRatherThanAPartialOne()} reliable: a cutoff at
 * {@link #TRUNCATE_AFTER} bytes lands inside that first entry's own header, so even a single {@code getNextEntry()}
 * call fails, rather than happening to land past a short first entry's complete header and reading it back
 * successfully.
 *
 * <p>
 * The fake directory is total the way {@code UserDirectoryAdapter.byIds} is total: an id it does not hold comes back as
 * an erased member rather than as an absent row. A mock that simply returned nothing for an unknown id would let the
 * writer pass here and produce a blank name in production.
 */
class ExportPackageWriterTest {

    private static final String ORG = "acme-hub";
    private static final String ORG_NAME = "Acme Engineering";
    private static final String TARGET = "target-user";
    private static final String TARGET_NAME = "Dana Okafor";
    private static final String TARGET_EMAIL = "dana@acme.test";
    private static final String DEPARTED = "departed-user";
    private static final Instant GENERATED_AT = Instant.parse("2026-07-28T00:00:00Z");
    private static final String LONG_MEDIA_KEY = "avatars/acme-hub/target-user/original-photo.jpg";

    /** What {@link #LONG_MEDIA_KEY} is named as once the storage key stops being the filename. */
    private static final String MEDIA_ENTRY = "identity-1.jpg";
    private static final long TRUNCATE_AFTER = 64;

    private final PersonalDataExporter identity = scope -> new ExportFragment("identity.json",
            Map.of("identity_user", List.of(Map.of("user_id", TARGET, "display_name", "<script>alert(1)</script>"))),
            List.of(LONG_MEDIA_KEY));

    private final PersonalDataExporter knowledge = scope -> new ExportFragment("knowledge.json",
            Map.of("artifact", List.of()), List.of());

    private final RecordingInputStream longMediaStream = new RecordingInputStream(
            new ByteArrayInputStream("fake-image-bytes".getBytes()));

    private final MediaStorage mediaStorage = mediaStorageWithLongKeyPresent(longMediaStream);

    private final Clock clock = Clock.fixed(GENERATED_AT, ZoneOffset.UTC);

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    private final OrganizationService organizations = organizationsNaming();

    private final ExportLabels labels = labelling("acme");

    private final List<PersonalDataFootprint> footprints = footprintsDeclaringSubjects("user_id", "proposer_user_id",
            "created_by", "subject", "assignee_user_id", "subject_id");

    private final UserDirectory directory = directoryHolding(
            new DirectoryUser(TARGET, TARGET_NAME, TARGET_EMAIL, null, null, MemberStatus.PRESENT));

    private final ExportPackageWriter dataExport = writerOver(List.of(identity, knowledge));

    /** Every entry sits under this one folder, which is also what the downloaded .zip is called. */
    private static final String ROOT = "axonia-export-acme-2026-07-28/";

    @Test
    void writesOneJsonPerContextPlusAManifestAndAReadableIndex() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        var entries = entryNamesOf(bytes.toByteArray());
        assertThat(entries).contains(ROOT + "manifest.json", ROOT + "index.html", ROOT + "knowledge.json",
                ROOT + "identity.json");
    }

    @Test
    void aMemberScopeDropsEverySubjectColumnRatherThanNamingThePerson() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        var identityJson = entryContentOf(bytes.toByteArray(), ROOT + "identity.json");
        assertThat(identityJson).describedAs("the member knows who they are; their subject is a key, not an answer")
                .doesNotContain(TARGET).doesNotContain("user_id");
        assertThat(identityJson).describedAs("this withholds, it does not gut the fragment").contains("display_name");
    }

    @Test
    void aMemberScopeWithholdsTheSubjectFromTheIndexAsWellAsTheJson() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "index.html"))
                .describedAs("relabelling only the JSON would leave the index leaking every subject")
                .doesNotContain("user_id").doesNotContain(TARGET);
    }

    @Test
    void anOrganizationScopeNamesEachSubjectByDisplayNameAndAddress() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        var identityJson = entryContentOf(bytes.toByteArray(), ROOT + "identity.json");
        assertThat(identityJson).contains("\"member\":\"Dana Okafor\"", "\"member_email\":\"dana@acme.test\"")
                .doesNotContain(TARGET);
        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "index.html"))
                .describedAs("the index reads the same relabelled rows the JSON does")
                .contains("member: " + TARGET_NAME).doesNotContain(TARGET);
    }

    @Test
    void anUnresolvableSubjectIsNamedAsDepartedRatherThanLeftBlank() throws Exception {

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(rowsOf("recognition.json", "contribution_love", row("user_id", DEPARTED))))
                .writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "recognition.json"))
                .contains("\"member\":\"" + DirectoryUser.DELETED_MEMBER_LABEL + "\"").doesNotContain(DEPARTED);
    }

    /** The same column, on a {@code USER}-typed row, still resolves through the member directory as before. */
    @Test
    void aUserSubjectOnTheSameShapeStillResolvesThroughTheDirectory() throws Exception {

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(rowsOf("access.json", "role_binding",
                row("subject_type", "USER", "subject_id", TARGET, "role", "Owner"))))
                .writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "access.json"))
                .contains("\"member\":\"" + TARGET_NAME + "\"").doesNotContain(TARGET);
    }

    /**
     * A departed member's {@code role_binding} is not exempt from the same "Deleted member" naming as any other row.
     */
    @Test
    void aUserTypedRoleBindingForADepartedMemberStillReadsDeletedMember() throws Exception {

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(rowsOf("access.json", "role_binding",
                row("subject_type", "USER", "subject_id", DEPARTED, "role", "Owner"))))
                .writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "access.json"))
                .contains("\"member\":\"" + DirectoryUser.DELETED_MEMBER_LABEL + "\"").doesNotContain(DEPARTED);
    }

    /** A cleared assignee is a fact about the row, not a person to look up. */
    @Test
    void aSubjectColumnWithNoValueIsNamedAsNobodyRatherThanAsADepartedMember() throws Exception {

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(rowsOf("events.json", "action_item", row("assignee_user_id", null))))
                .writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "events.json"))
                .contains("\"assignee\":null", "\"assignee_email\":null")
                .doesNotContain(DirectoryUser.DELETED_MEMBER_LABEL);
    }

    /**
     * No module chooses what its people are called, so a column that says nothing beyond "a person" lands on
     * {@code member} and every other one keeps the role its name already states.
     */
    @Test
    void aBaseNameIsDerivedFromTheColumnRatherThanChosenByTheModule() throws Exception {

        var writer = writerOver(List.of(rowsOf("events.json", "topic",
                row("proposer_user_id", TARGET, "created_by", TARGET, "subject", TARGET))));

        var bytes = new ByteArrayOutputStream();
        writer.writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "events.json"))
                .contains("\"proposer\":\"" + TARGET_NAME + "\"", "\"proposer_email\":\"" + TARGET_EMAIL + "\"")
                .contains("\"created_by\":\"" + TARGET_NAME + "\"", "\"created_by_email\":\"" + TARGET_EMAIL + "\"")
                .contains("\"member\":\"" + TARGET_NAME + "\"").doesNotContain(TARGET);
    }

    /**
     * A subject's derived {@code _email} name can land on a real column of the same row. The real column is the fact
     * the row recorded, so it is kept, and the person is still named. It comes first in the row on purpose: a real
     * column written after the derived one would win by being written last, whatever the guard did.
     */
    @Test
    void aDerivedNameNeverDisplacesARealColumnItCollidesWith() throws Exception {

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(rowsOf("events.json", "action_item",
                row("assignee_email", "recorded-then@acme.test", "assignee_user_id", TARGET))))
                .writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "events.json"))
                .contains("\"assignee_email\":\"recorded-then@acme.test\"", "\"assignee\":\"" + TARGET_NAME + "\"")
                .doesNotContain(TARGET_EMAIL, TARGET);
    }

    /** {@code expertise_opt_out} holds nothing but the subject: the row existing is the whole fact. */
    @Test
    void aRowLeftEmptyByTheStrippingSurvivesAsAnEmptyRowRatherThanDisappearing() throws Exception {

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(rowsOf("expertise.json", "expertise_opt_out", row("user_id", TARGET))))
                .writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "expertise.json"))
                .isEqualTo("{\"expertise_opt_out\":[{}]}");
        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "index.html"))
                .describedAs("an empty row still happened, and a reader must not take it for a rendering fault")
                .contains("Recorded.");
    }

    @Test
    void theManifestNamesTheOrganizationAndTheMemberReadablyAndCarriesNoIds() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        var manifest = manifestOf(bytes.toByteArray());
        assertThat(manifest).containsEntry("organization", ORG_NAME).containsEntry("member", TARGET_NAME)
                .doesNotContainKeys("organizationId", "userId");
    }

    @Test
    void theIndexNamesTheMemberAndTheirAddressRatherThanTheirSubject() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "index.html")).contains("Organization: " + ORG_NAME)
                .contains("Member: " + TARGET_NAME + " &lt;" + TARGET_EMAIL).doesNotContain(ORG).doesNotContain(TARGET);
    }

    @Test
    void aTruncatedStreamProducesAnArchiveThatCannotBeOpenedRatherThanAPartialOne() throws Exception {

        var failing = new FailingOutputStream(afterBytes(TRUNCATE_AFTER));

        assertThatThrownBy(() -> dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), failing))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new ZipInputStream(failing.written()).getNextEntry())
                .describedAs("the central directory was never written, so the archive is invalid, not partial")
                .isInstanceOf(Exception.class);
    }

    @Test
    void bundlesMediaThatExistsAlongsideTheJsonThatReferencesIt() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        assertThat(entryNamesOf(bytes.toByteArray())).contains(ROOT + MEDIA_ENTRY);
        assertThat(entryContentOf(bytes.toByteArray(), ROOT + MEDIA_ENTRY)).isEqualTo("fake-image-bytes");
        assertThat(longMediaStream.closed()).describedAs("a media stream must be closed once its entry is written")
                .isTrue();
    }

    @Test
    void aTruncatedWriteStillClosesTheMediaStreamItHadAlreadyOpened() throws Exception {

        var failing = new FailingOutputStream(afterBytes(TRUNCATE_AFTER));

        assertThatThrownBy(() -> dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), failing))
                .isInstanceOf(IOException.class);

        assertThat(longMediaStream.closed())
                .describedAs("the media stream opened for the first, truncated entry must not leak").isTrue();
    }

    @Test
    void aMissingMediaBlobIsRecordedInTheManifestRatherThanFailingTheWholeArchive() throws Exception {

        PersonalDataExporter withADeadBlob = scope -> new ExportFragment("recognition.json", Map.of(),
                List.of("badges/missing.png"));
        var writer = writerOver(List.of(identity, knowledge, withADeadBlob));

        var bytes = new ByteArrayOutputStream();
        writer.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        assertThat(entryNamesOf(bytes.toByteArray())).doesNotContain(ROOT + "badges/missing.png");
        var manifest = manifestOf(bytes.toByteArray());
        assertThat(listOf(manifest, "missingMedia")).describedAs("recorded by the name the reader would have seen")
                .containsExactly("recognition-1.png");
    }

    @Test
    void manifestRecordsScopeGeneratedAtFileListAndTheExclusionSentence() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        var manifest = manifestOf(bytes.toByteArray());
        assertThat(manifest.get("scope")).isEqualTo("OneMember");
        assertThat(manifest.get("generatedAt")).isEqualTo(GENERATED_AT.toString());
        assertThat(listOf(manifest, "files")).contains("identity.json", "knowledge.json", "index.html");
        assertThat((String) manifest.get("note")).contains("actor or subject").contains("referenced by somebody else");
    }

    /**
     * A row's own {@code created_at}/{@code updated_at} arrives from the driver as a raw {@link Timestamp}, which
     * {@link String#valueOf} renders in whatever timezone the JVM happens to run under, with no marker saying so -
     * unlike the JSON sibling, which the JSON mapper always renders in UTC. The index must read the same time the JSON
     * does, not the JVM operator's own local clock.
     */
    @Test
    void indexHtmlRendersARowTimestampInUtcWithAnExplicitMarkerRatherThanTheJvmZone() throws Exception {

        var previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"));
        try {
            var timestamp = Timestamp.from(Instant.parse("2026-07-28T08:30:58Z"));
            var bytes = new ByteArrayOutputStream();
            writerOver(List.of(rowsOf("access.json", "role_binding", row("updated_at", timestamp))))
                    .writeTo(new ExportScope.WholeOrganization(ORG), bytes);

            assertThat(entryContentOf(bytes.toByteArray(), ROOT + "index.html"))
                    .describedAs("same instant as the JSON, marked as UTC, never the JVM's own local offset")
                    .contains("2026-07-28 08:30:58 UTC").doesNotContain("10:30:58");
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    /**
     * A Postgres {@code date} column - {@code action_item.due_date} among others - arrives from the driver as a
     * {@link java.sql.Date}, not a {@link Timestamp}. It carries no time of day and no zone, so it must render as a
     * plain calendar date rather than crash: {@code java.sql.Date.toInstant()} throws
     * {@link UnsupportedOperationException} unconditionally, which is exactly what {@code instanceof Date} matched
     * before this test existed.
     */
    @Test
    void indexHtmlRendersASqlDateCellAsAPlainCalendarDateRatherThanCrashing() throws Exception {

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(rowsOf("events.json", "action_item", row("due_date", java.sql.Date.valueOf("2026-07-28")))))
                .writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "index.html")).contains("due_date: 2026-07-28");
    }

    @Test
    void indexHtmlEscapesMemberAuthoredFreeTextInsteadOfShippingAStoredXssVector() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        var indexHtml = entryContentOf(bytes.toByteArray(), ROOT + "index.html");
        assertThat(indexHtml).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
    }

    @Test
    void manifestUsesTheWholeOrganizationScopeAndItsOwnNoteForAWholeOrganizationExport() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        var manifest = manifestOf(bytes.toByteArray());
        assertThat(manifest.get("scope")).isEqualTo("WholeOrganization");
        assertThat(manifest.get("organization")).isEqualTo(ORG_NAME);
        assertThat(manifest).doesNotContainKey("member");
        assertThat((String) manifest.get("note")).contains("every member's data across the Organization")
                .doesNotContain("actor or subject");
    }

    @Test
    void indexHtmlOmitsTheMemberLineForAWholeOrganizationExport() throws Exception {

        var bytes = new ByteArrayOutputStream();
        dataExport.writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        var indexHtml = entryContentOf(bytes.toByteArray(), ROOT + "index.html");
        assertThat(indexHtml).contains("Organization: " + ORG_NAME).doesNotContain("Member:");
    }

    @Test
    void indexHtmlFailsWithADiagnosticMessageWhenAFragmentPayloadIsNotShapedAsRows() {

        PersonalDataExporter malformed = scope -> new ExportFragment("malformed.json",
                Map.of("not_a_table", "not-a-list-of-rows"), List.of());

        assertThatThrownBy(() -> writerOver(List.of(malformed)).writeTo(new ExportScope.OneMember(ORG, TARGET),
                new ByteArrayOutputStream())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("malformed.json").hasMessageContaining("not_a_table");
    }

    @Test
    void indexHtmlFailsWithADiagnosticMessageWhenARowIsNotAMap() {

        PersonalDataExporter malformed = scope -> new ExportFragment("malformed.json",
                Map.of("some_table", List.of("not-a-row")), List.of());

        assertThatThrownBy(() -> writerOver(List.of(malformed)).writeTo(new ExportScope.OneMember(ORG, TARGET),
                new ByteArrayOutputStream())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("malformed.json").hasMessageContaining("some_table");
    }

    @Test
    void indexHtmlDiagnosticNamesNullWhenAFragmentPayloadValueIsAbsent() {

        var payloadWithANullValue = new HashMap<String, Object>();
        payloadWithANullValue.put("missing_table", null);
        PersonalDataExporter malformed = scope -> new ExportFragment("malformed.json", payloadWithANullValue,
                List.of());

        assertThatThrownBy(() -> writerOver(List.of(malformed)).writeTo(new ExportScope.OneMember(ORG, TARGET),
                new ByteArrayOutputStream())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("malformed.json").hasMessageContaining("missing_table")
                .hasMessageContaining("null");
    }

    /**
     * A media key can no longer reach the entry name at all, which is a stronger guarantee than refusing the ones that
     * look dangerous: the name is derived from the fragment, and only the extension survives from the key, reduced to
     * alphanumerics. So a key that tries to climb out of the root is neutralised rather than rejected, and nothing
     * resembling it appears in the archive. The refusal itself is still live for fragment file names, which modules do
     * choose - {@link #aFragmentFileNameThatClimbsOutOfThePackageRootIsRefusedRatherThanWritten()} covers that.
     */
    @Test
    void aMediaKeyThatClimbsOutOfThePackageRootCannotReachTheEntryNameAtAll() throws Exception {

        PersonalDataExporter escaping = scope -> new ExportFragment("escape.json", Map.of(),
                List.of("../../etc/passwd"));
        when(mediaStorage.open("../../etc/passwd")).thenReturn(
                Optional.of(new MediaContent(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), -1)));

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(escaping)).writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        assertThat(entryNamesOf(bytes.toByteArray())).contains(ROOT + "escape-1")
                .allSatisfy(name -> assertThat(name).doesNotContain("..").doesNotContain("etc/passwd"));
    }

    /** A module chooses its own fragment file name, so that path is the one still worth refusing outright. */
    @Test
    void aFragmentFileNameThatClimbsOutOfThePackageRootIsRefusedRatherThanWritten() {

        PersonalDataExporter escaping = scope -> new ExportFragment("../../etc/passwd.json",
                Map.of("escape", List.of()), List.of());

        assertThatThrownBy(() -> writerOver(List.of(escaping)).writeTo(new ExportScope.OneMember(ORG, TARGET),
                new ByteArrayOutputStream())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside the package root");
    }

    /** A label is preferred, but an Organization without one is named after itself, never after its tenant id. */
    @Test
    void anOrganizationWithNoLabelIsNamedAfterItselfRatherThanAfterItsIdentifier() throws Exception {

        var writer = new ExportPackageWriter(List.of(knowledge), mediaStorage, clock, jsonMapper,
                new ExportPackageNames(organizations, labelling(null)), footprints, directory);

        var bytes = new ByteArrayOutputStream();
        writer.writeTo(new ExportScope.WholeOrganization(ORG), bytes);

        assertThat(entryNamesOf(bytes.toByteArray()))
                .allSatisfy(name -> assertThat(name).startsWith("axonia-export-Acme-Engineering-2026-07-28/"));
    }

    /**
     * A person with no Organization gets a package named for no Organization, carrying only the modules that hold
     * something at the person. It is read under the person's own scope, which is what every person-rooted table is
     * isolated on, and nothing about it is looked up in an Organization.
     */
    @Test
    void aPersonsPackageCarriesOnlyWhatIsRootedAtThePersonUnderTheirOwnScope() throws Exception {

        var scopes = new ArrayList<Object>();
        PersonalDataExporter organizationRooted = scope -> ExportFragment.nothing("knowledge.json");
        PersonalDataExporter personRooted = scope -> {
            scopes.add(SecurityContextHolder.getContext().getAuthentication());
            return new ExportFragment("standing.json",
                    Map.of("standing", List.of(row("subject", TARGET, "ring", "GOLD"))), List.of());
        };
        var organizationsNeverAsked = mock(OrganizationService.class);
        var labelsNeverAsked = mock(ExportLabels.class);

        var bytes = new ByteArrayOutputStream();
        new ExportPackageWriter(List.of(organizationRooted, personRooted), mediaStorage, clock, jsonMapper,
                new ExportPackageNames(organizationsNeverAsked, labelsNeverAsked), footprints, directory)
                .writeTo(new ExportScope.Person(TARGET), bytes);

        var root = "axonia-export-personal-2026-07-28/";
        assertThat(entryNamesOf(bytes.toByteArray())).containsExactly(root + "standing.json", root + "index.html",
                root + "manifest.json");
        assertThat(entryContentOf(bytes.toByteArray(), root + "standing.json")).contains("GOLD").doesNotContain(TARGET);
        assertThat(entryContentOf(bytes.toByteArray(), root + "manifest.json")).contains("\"scope\":\"Person\"")
                .contains("apart from any Organization");
        assertThat(scopes).singleElement().isInstanceOfSatisfying(SubjectAuthentication.class,
                authentication -> assertThat(authentication.subject()).isEqualTo(TARGET));
        verifyNoInteractions(organizationsNeverAsked, labelsNeverAsked);
    }

    /**
     * A person's identity is held by two modules and still reads as one file. The second contributor's table lands in
     * the same {@code identity.json}, the index shows that file once, and the first contributor's media keeps its name.
     */
    @Test
    void twoModulesWritingOneFileShipOneFileHoldingBothTables() throws Exception {

        var account = rowsOf("identity.json", "platform_account", row("email", "dana@elsewhere.test"));

        var bytes = new ByteArrayOutputStream();
        writerOver(List.of(identity, knowledge, account)).writeTo(new ExportScope.OneMember(ORG, TARGET), bytes);

        assertThat(entryNamesOf(bytes.toByteArray())).filteredOn(name -> name.endsWith("identity.json"))
                .containsExactly(ROOT + "identity.json");
        assertThat(entryNamesOf(bytes.toByteArray())).contains(ROOT + MEDIA_ENTRY);
        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "identity.json")).contains("\"identity_user\"",
                "\"platform_account\"", "dana@elsewhere.test");
        assertThat(entryContentOf(bytes.toByteArray(), ROOT + "index.html")).containsOnlyOnce("<h2>identity.json</h2>")
                .contains("<h3>platform_account</h3>");
    }

    /** The index reads in the archive's own order, whatever order the exporter beans are wired in. */
    @Test
    void indexHtmlListsItsSectionsInFileNameOrderWhateverOrderTheExportersRunIn() throws Exception {

        var zebra = rowsOf("zebra.json", "zebra_table", row("note", "last"));
        var alpha = rowsOf("alpha.json", "alpha_table", row("note", "first"));
        var middle = rowsOf("middle.json", "middle_table", row("note", "between"));

        for (var exporters : List.of(List.of(zebra, alpha, middle), List.of(middle, zebra, alpha))) {
            var bytes = new ByteArrayOutputStream();
            writerOver(exporters).writeTo(new ExportScope.WholeOrganization(ORG), bytes);

            var sections = Pattern.compile("<h2>([^<]+)</h2>")
                    .matcher(entryContentOf(bytes.toByteArray(), ROOT + "index.html")).results()
                    .map(match -> match.group(1)).toList();
            assertThat(sections).containsExactly("alpha.json", "middle.json", "zebra.json");
        }
    }

    /** Either winner would silently drop somebody's rows from their own export, so a shared table fails the export. */
    @Test
    void twoModulesWritingTheSameTableToOneFileFailTheExport() {

        var duplicate = rowsOf("identity.json", "identity_user", row("display_name", "Someone Else"));
        var writer = writerOver(List.of(identity, duplicate));

        assertThatThrownBy(() -> writer.writeTo(new ExportScope.OneMember(ORG, TARGET), new ByteArrayOutputStream()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Two exporters both wrote table 'identity_user' to 'identity.json'");
    }

    private ExportPackageWriter writerOver(List<PersonalDataExporter> exporters) {

        return new ExportPackageWriter(exporters, mediaStorage, clock, jsonMapper,
                new ExportPackageNames(organizations, labels), footprints, directory);
    }

    private static PersonalDataExporter rowsOf(String fileName, String table, Map<String, Object> row) {

        return scope -> new ExportFragment(fileName, Map.of(table, List.of(row)), List.of());
    }

    /** Column then value, in order, because a row's own order is what the relabelled JSON preserves. */
    private static Map<String, Object> row(Object... columnsAndValues) {

        var row = new LinkedHashMap<String, Object>();
        for (var i = 0; i < columnsAndValues.length; i += 2) {
            row.put(String.valueOf(columnsAndValues[i]), columnsAndValues[i + 1]);
        }
        return row;
    }

    /**
     * One table declaring each column as a subject, which is all the writer reads off a footprint: the set of column
     * names any module has classified {@link Classification#SUBJECT} and still exports.
     */
    private static List<PersonalDataFootprint> footprintsDeclaringSubjects(String... columns) {

        var personal = Arrays.stream(columns)
                .map(column -> new ColumnFootprint(column, Classification.SUBJECT, Disposition.DELETE_ROW)).toList();
        var table = TableFootprint.builder().table("fake").category(DataCategory.WHO_YOU_ARE).personal(personal)
                .notPersonal(Set.of()).build();
        return List.of(() -> List.of(table));
    }

    private static UserDirectory directoryHolding(DirectoryUser... people) {

        var known = Arrays.stream(people).collect(Collectors.toMap(DirectoryUser::userId, Function.identity()));
        var directory = mock(UserDirectory.class);
        when(directory.byIds(eq(ORG), anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(1);
            return ids.stream().distinct().map(id -> known.getOrDefault(id, erased(id))).toList();
        });
        return directory;
    }

    private static DirectoryUser erased(String userId) {

        return new DirectoryUser(userId, DirectoryUser.DELETED_MEMBER_LABEL, null, null, null, MemberStatus.ERASED);
    }

    private static OrganizationService organizationsNaming() {

        var organizations = mock(OrganizationService.class);
        when(organizations.organization(ORG))
                .thenReturn(Optional.of(Organization.builder().id(ORG).name(ORG_NAME).build()));
        return organizations;
    }

    private static ExportLabels labelling(String label) {

        return new ExportLabels(
                List.of(organizationId -> Optional.ofNullable(ORG.equals(organizationId) ? label : null)));
    }

    private static MediaStorage mediaStorageWithLongKeyPresent(InputStream mediaStream) {

        var storage = mock(MediaStorage.class);
        when(storage.open(LONG_MEDIA_KEY)).thenReturn(Optional.of(new MediaContent(mediaStream, -1)));
        return storage;
    }

    private static long afterBytes(long n) {

        return n;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> manifestOf(byte[] zipBytes) throws IOException {

        return jsonMapper.readValue(entryContentOf(zipBytes, ROOT + "manifest.json"), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Map<String, Object> manifest, String key) {

        return (List<Object>) manifest.get(key);
    }

    private static List<String> entryNamesOf(byte[] zipBytes) throws IOException {

        var names = new ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static String entryContentOf(byte[] zipBytes, String name) throws IOException {

        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("No entry named '" + name + "'");
    }

    /** Fails once {@code limit} bytes have been written, capturing exactly what made it through before that. */
    private static final class FailingOutputStream extends OutputStream {

        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final long limit;
        private long count;

        FailingOutputStream(long limit) {

            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {

            if (count >= limit) {
                throw new IOException("Simulated write failure after " + limit + " bytes");
            }
            captured.write(b);
            count++;
        }

        InputStream written() {

            return new ByteArrayInputStream(captured.toByteArray());
        }
    }

    /**
     * Wraps a delegate so a test can prove a stream was actually closed rather than trusting that reading its content
     * back out of the archive implies it. A {@link ByteArrayInputStream}'s own {@code close()} is a no-op, which is
     * exactly how a real leak - closing skipped entirely on a failure path - can hide behind a passing test.
     */
    private static final class RecordingInputStream extends FilterInputStream {

        private boolean closed;

        RecordingInputStream(InputStream delegate) {

            super(delegate);
        }

        @Override
        public void close() throws IOException {

            closed = true;
            super.close();
        }

        boolean closed() {

            return closed;
        }
    }
}
