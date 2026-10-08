package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A structural guard against one recurring, silent defect: writing a community-owned id that arrived in a request body
 * without checking it belongs to the community in the path.
 *
 * <p>
 * Six sites had it at once, and none of them looked wrong: each checked membership of the community it was called for,
 * then wrote an id or a free-text person reference from the body. Organization-scoped Row-Level Security cannot catch
 * it, because both communities are in the same Organization; the ids are visible across it on purpose, and they are
 * sequential. So the only thing standing between a member of one community and another community's session page is a
 * guard somebody remembered to call. As of this test, all six original sites are closed, including
 * {@code ResourceService.attach}, whose {@code segmentId} arrived as a bare {@code Long} parameter rather than
 * {@code request.segmentId()} until {@code ResourceController} was changed to forward the whole request, matching every
 * sibling controller.
 *
 * <p>
 * <strong>What it covers.</strong> Every {@code .java} file under {@code src/main} that references one of the write
 * mechanisms this codebase actually uses - see {@link #WRITE_MECHANISM_SYMBOL} - is split into its top-level members
 * (methods, constructors, nested-type bodies), after comments and string/char/text-block content are masked out so
 * neither can immunise a member by accident. Any member whose masked text contains a write of
 * {@code request.sessionId()}, {@code request.originSessionId()}, {@code request.segmentId()},
 * {@code request.topicId()}, {@code request.sectionTypeId()}, {@code request.facilitatorUserId()},
 * {@code provenance.sessionId()}, {@code provenance.segmentId()}, or the nested-record shape a lambda over a
 * request-carried list uses for {@code sectionTypeId()}, must also contain a call to one of the known guards - in that
 * SAME member, in a private member it directly calls, or in a private member that directly calls it (one hop, see
 * {@link #guardScope}), not merely the same file: a file-wide check let {@code SlotService}'s unrelated
 * {@code communityAccess.resolveMember} call (there to resolve a presenter, nothing to do with the topic queued three
 * methods away) satisfy the whole file, and the gap shipped anyway.
 *
 * <p>
 * <strong>It is a net with a known mesh, not a proof, in this order of importance:</strong>
 * <ol>
 * <li>A guard call found in scope proves only that a method named like a guard was called somewhere in that scope, not
 * that it was called ON THE OFFENDING VALUE. {@code requireSessionInCommunity(pathSessionId, communityId)} sitting next
 * to an unguarded {@code repository.save(request.topicId())} in the same member reads as guarded to this scanner and is
 * not - it is exactly the shape the six original sites had, minus the coincidence of the guard call existing at all.
 * This is the sharpest limit of a text scanner that never reads argument lists: believing this guard proves an id was
 * checked, rather than that a check merely ran nearby, is the one mistake most likely to matter.
 * <li>A body id whose field name is not one of the six above, or whose receiver is not named {@code request} /
 * {@code provenance}, is invisible - the accessor list is a floor, not a definition, and it has already lagged reality
 * once, at {@code facilitatorUserId}. {@code src/main} carries both halves of this gap today:
 * {@code RecognitionService.java:149} writes {@code .contributionId(r.contributionId())}, a body id named
 * {@code contributionId} arriving through a parameter named {@code r}; request records also declare {@code typeId},
 * {@code kindId}, {@code definitionId}, {@code sourceId}, {@code roleId} and {@code artifactId}, and eleven
 * {@code @RequestBody} parameters are named {@code patch}, {@code group} or {@code user} rather than {@code request}.
 * None of this is a defect discovered and left open; it is the honest boundary of matching by name.
 * <li>A guard reached through a second hop of delegation - a helper that itself calls the real guard, one hop further
 * out than {@link #guardScope} follows - passes too, whether or not that second helper is actually reachable from the
 * write.
 * <li>{@link #callsMethod}, which builds the one-hop call graph, is receiver-blind: {@code \bname\s*\(} matches
 * {@code anything.name(}, so a same-named but unrelated method on a different receiver can create a call-graph edge
 * that should not exist. Not load-bearing today - every accessor-bearing member in the tree is satisfied by its own
 * text or a genuine edge - but the edge is not verified to be the real one.
 * <li>{@link #members} treats a nested type as one member together with all of its own methods, reinstating the
 * original file-wide hole at nested-type granularity for any nested type large enough to carry both an unrelated guard
 * mention and an unguarded write. No nested type in this codebase currently does.
 * <li>{@link #NESTED_SECTION_TYPE_ID}'s {@code [^;]*} stops at the first {@code ;}, so it only sees a nested
 * {@code sectionTypeId()} in a lambda's first statement; a second statement in a block lambda body is invisible to it.
 * <li>{@link #memberName} can misread a parameter annotation's own arguments as the member's name when they are the
 * last thing before the body brace - a webhook's {@code receive} is read as {@code RequestHeader} because of
 * {@code @RequestHeader(value = ..., required = false)}, and {@code @Qualifier(...)} does the same to five
 * constructors. The failure is conservative: a mid-read member only loses its ability to be found as a caller or
 * callee, which can produce a false positive (flagging something that is actually one hop from its guard) but never a
 * false negative.
 * </ol>
 * The guard names, the one-hop depth and the accessor list are all chosen by what the code shipped in this codebase
 * actually needed, not by argument order, intent, or a general theory of every shape the defect could take.
 *
 * <p>
 * <strong>What it deliberately does not cover.</strong> A file that references none of the write mechanisms in
 * {@link #WRITE_MECHANISM_SYMBOL} - a pure adapter that forwards a request to a service, e.g.
 * {@code ResourceController} - is exempt, because it cannot itself perform the write this guard cares about; the write
 * and its guard live one member in, in the service, which is still fully scanned. The exemption is keyed on that
 * property rather than on being named {@code *Controller}, so a controller that starts writing through one of those
 * mechanisms re-enters scope on its own - {@link #theRepositoryExemptionLetsAWritingControllerReenterScope()} proves it
 * on a controller that writes, and {@code CloudCrossCommunityReferenceGuardTest} on the tree's one controller that
 * writes through a repository directly. {@link #noExemptFileExecutesRawInsertOrUpdateSql()} guards the symbol set
 * itself: a file this test exempts must not contain raw {@code insert into} / {@code update ... set} SQL text, checked
 * unmasked because {@link SourceMasking#mask} would otherwise blank the very SQL string literals this check exists to
 * read.
 *
 * <p>
 * Each build unit scans its own {@code src/main}: this class core's, {@code CloudCrossCommunityReferenceGuardTest}
 * cloud's, with its own calibrations.
 */
class CrossCommunityReferenceGuardTest {

    private static final Path MAIN = Path.of("src", "main", "java", "dev", "axonia");

    private static final Pattern DIRECT_ID = Pattern
            .compile("\\w*[Rr]equest\\.(originSessionId|sessionId|segmentId|topicId|sectionTypeId|facilitatorUserId"
                    + "|checkpointSessionId)\\(\\)" + "|provenance\\.(sessionId|segmentId)\\(\\)");

    /**
     * The second shape the defect took: a lambda over a list the request carries maps each element - a nested record,
     * not {@code request} itself - to its own {@code sectionTypeId()}, as {@code SessionService.createOneOff} and
     * {@code SeriesService.toRefs} both do. {@link #DIRECT_ID} cannot see it: the receiver is the lambda's own
     * parameter. Scoped to {@code sectionTypeId} specifically and verified narrow by
     * {@link #theLambdaPatternMatchesOnlyTheKnownNestedSites()} against every {@code .sectionTypeId()} call site in the
     * tree: a receiver-agnostic version of this pattern for every accessor name lights up read paths in five other
     * modules that map an already-trusted id onto a response DTO, which is a different, unguarded concern this test
     * does not own.
     */
    private static final Pattern NESTED_SECTION_TYPE_ID = Pattern.compile("->[^;]*\\.sectionTypeId\\(\\)");

    /**
     * The write mechanisms actually used in {@code src/main}: a Spring Data repository interface, or raw SQL through
     * {@code NamedParameterJdbcTemplate}/{@code JdbcClient}. A bare {@code "Repository"} check missed the second family
     * entirely - {@code SignalWriter}, {@code TagPreferenceWriter}, {@code ArtifactOpeningWriter}, several
     * {@code *MemberDataEraser}s and {@code ArtifactLoveCountListener} all write through a plain {@code Jdbc*} field
     * and were silently exempt. {@link #noExemptFileExecutesRawInsertOrUpdateSql()} is the standing check that a fourth
     * family never repeats this: it reads unmasked source (raw SQL text lives inside string and text-block literals,
     * which {@link SourceMasking#mask} blanks) and fails if any file this symbol set exempts contains an
     * {@code insert into} or {@code update ... set}.
     */
    private static final Pattern WRITE_MECHANISM_SYMBOL = Pattern.compile("Repository|JdbcTemplate|JdbcClient");

    /** Shaped like a controller, and writing through a repository, so its name exempts nothing. */
    private static final String A_WRITING_CONTROLLER = """
            @RestController
            class SampleController {

                private final ThingRepository repository;

                @PostMapping
                void create(@RequestBody ThingRequest request) {

                    repository.save(new Thing(request.name()));
                }
            }
            """;

    private static final Pattern RAW_SQL_WRITE = Pattern.compile("insert\\s+into|update\\s+\\S+\\s+set",
            Pattern.CASE_INSENSITIVE);

    /**
     * Every one qualified to its actual call site, the way {@code sectionTypeCatalog.require(} always was: a bare
     * {@code resolveMember} sits in nine {@code src/main} files, mostly resolving a presenter, an assignee or a
     * contributor - a membership lookup the vulnerable code already performed, not a check that the id in question
     * belongs to the community in the path.
     */
    private static final List<String> GUARDS = List.of("requireSessionInCommunity", "requireSegmentInSession",
            "requireOriginSessionInCommunity", "requireUpcomingSessionInCommunity", "requireMayQueueTopics",
            "sectionTypeCatalog.require(", "communityAccess.resolveMember(", "facilitatorResolver.resolve(");

    @Test
    void everyMemberThatWritesACommunityOwnedIdFromARequestAlsoGuardsIt() throws IOException {

        assertThat(offenders()).as("""
                A session, segment, topic, section-type or facilitator id that arrived in a request body is \
                org-visible and sequential, so it says nothing about which community may write to it. Run \
                it through SessionProvenanceGuard, requireOriginSessionInCommunity, requireMayQueueTopics, \
                SectionTypeCatalog.require, CommunityAccess.resolveMember or FacilitatorResolver.resolve, from that \
                same method or a private helper one call away, before it reaches a row.""").isEmpty();
    }

    /**
     * Calibration for {@link #DIRECT_ID} and {@link #NESTED_SECTION_TYPE_ID}: the matched strings, not a count - a
     * count passes if the regex matches five wrong things as readily as the five right ones.
     */
    @Test
    void theScannerFindsEveryShapeOfTheDefectItExistsToPrevent() {

        var asShipped = """
                builder.provenanceSessionId(provenance.sessionId()).provenanceSegmentId(provenance.segmentId())
                .originSessionId(request.originSessionId())
                var topicId = attachTopic(organizationId, session.getCommunityId(), request.topicId());
                SectionEntity.builder().sectionTypeId(request.sectionTypeId())
                facilitatorResolver.resolve(communityId, stored, request.facilitatorUserId());
                request.sections().forEach(t -> sectionTypeCatalog.require(organizationId, t.sectionTypeId()));
                """;

        assertThat(accessorMatches(SourceMasking.mask(asShipped))).containsExactlyInAnyOrder("provenance.sessionId()",
                "provenance.segmentId()", "request.originSessionId()", "request.topicId()", "request.sectionTypeId()",
                "request.facilitatorUserId()", "-> sectionTypeCatalog.require(organizationId, t.sectionTypeId()");
    }

    /**
     * Calibration for the comment mask: the exact hatch the file-wide {@code source::contains} check used to leave open
     * - a comment naming a guard - no longer immunises a member that never calls one.
     */
    @Test
    void aGuardNameInACommentDoesNotImmuniseTheMember() {

        var member = """
                // TODO: requireOriginSessionInCommunity
                Topic propose(TopicRequest request) {

                    return topics.save(new Topic(request.originSessionId()));
                }
                """;

        assertThat(GUARDS.stream().noneMatch(SourceMasking.mask(member)::contains)).isTrue();
        assertThat(accessorMatches(SourceMasking.mask(member))).containsExactly("request.originSessionId()");
    }

    /**
     * Calibration for {@link #NESTED_SECTION_TYPE_ID}: the open tree has exactly two call sites shaped this way, and
     * both are the known writers. A receiver-agnostic version would additionally match {@code WriteTools},
     * {@code RecognitionService} and {@code EvidenceEnricher}, none of which write anything - they read an id already
     * on a persisted row back onto a response.
     */
    @Test
    void theLambdaPatternMatchesOnlyTheKnownNestedSites() throws IOException {

        var hits = new ArrayList<Path>();
        for (var file : sources()) {
            if (NESTED_SECTION_TYPE_ID.matcher(SourceMasking.mask(Files.readString(file))).find()) {
                hits.add(file);
            }
        }

        assertThat(hits).containsExactlyInAnyOrderElementsOf(knownNestedSectionTypeSites());
    }

    /** Calibration for the repository exemption: the known writers stay in scope. */
    @Test
    void theRepositoryExemptionLeavesEveryKnownWriterInScope() throws IOException {

        assertThat(sources().stream().filter(this::isScanned).toList()).containsAll(knownWriters());
    }

    /**
     * Calibration for the repository exemption's other side: a controller that writes through a repository is scanned
     * whatever it is called, so a name-based exemption cannot hide it the way it would have hidden
     * {@code ResourceController} for the opposite reason. A controller that touches no repository stays exempt.
     */
    @Test
    void theRepositoryExemptionLetsAWritingControllerReenterScope() throws IOException {

        assertThat(isScanned(A_WRITING_CONTROLLER)).isTrue();
        assertThat(sources().stream().filter(path -> !isScanned(path)).toList()).contains(aForwardingController());
    }

    /**
     * Keeps {@link #WRITE_MECHANISM_SYMBOL} honest against a mechanism nobody has added to it yet: no file this test
     * exempts may contain a raw SQL write. Reads the RAW source, not {@link SourceMasking#mask}'s output - the SQL text
     * this looks for lives inside string and text-block literals, which masking blanks on purpose for every other check
     * in this class.
     */
    @Test
    void noExemptFileExecutesRawInsertOrUpdateSql() throws IOException {

        var offendingExemptFiles = new ArrayList<String>();
        for (var file : sources().stream().filter(path -> !isScanned(path)).toList()) {
            if (RAW_SQL_WRITE.matcher(Files.readString(file)).find()) {
                offendingExemptFiles.add(file.toString());
            }
        }

        assertThat(offendingExemptFiles).as("""
                A file this test exempts must not itself write to the database - if it does, it is a fourth write \
                mechanism WRITE_MECHANISM_SYMBOL does not name yet, and it must either re-enter scope through that \
                symbol set or be named here deliberately, not fall through silently.""").isEmpty();
    }

    protected List<Path> knownNestedSectionTypeSites() {

        return List.of(source("events", "SessionService.java"), source("events", "SeriesService.java"));
    }

    protected List<Path> knownWriters() {

        return List.of(source("knowledge", "ArtifactService.java"), source("events", "TopicService.java"),
                source("events", "SlotService.java"), source("events", "SessionService.java"));
    }

    protected Path aForwardingController() {

        return source("knowledge", "ResourceController.java");
    }

    static Path source(String... belowTheRootPackage) {

        return MAIN.resolve(Path.of("", belowTheRootPackage));
    }

    private List<Path> sources() throws IOException {

        try (Stream<Path> tree = Files.walk(MAIN)) {
            return tree.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted().toList();
        }
    }

    private List<String> offenders() throws IOException {

        var found = new ArrayList<String>();
        for (var file : sources().stream().filter(this::isScanned).toList()) {
            var masked = SourceMasking.mask(Files.readString(file));
            var members = members(masked);
            for (var member : members) {
                var matches = accessorMatches(member.text());
                if (matches.isEmpty()) {
                    continue;
                }
                if (GUARDS.stream().noneMatch(guardScope(member, members)::contains)) {
                    found.add(file + ":" + member.startLine() + ": " + matches);
                }
            }
        }
        return found;
    }

    /**
     * A member's guard scope is its own text plus any other member in the same file it directly calls, or that directly
     * calls it - one hop, not a full transitive closure. Both directions are real: {@code SlotService.addSlot} carries
     * the risky read and reaches its guard by calling the private {@code attachTopic}, while
     * {@code ArtifactService.autoAttach} carries the risky read and is reached BY {@code capture}, which is where the
     * guard actually runs before {@code autoAttach} is even called. Stopping at one hop, not chasing every caller's own
     * callers in turn, is deliberate: a shared trivial callee - checking whether a session is cancelled, say - links
     * most of a class's methods together, and chasing it would let a guard three calls away immunise a write the same
     * way {@code SlotService}'s unrelated {@code communityAccess.resolveMember} once did, just one call further out.
     */
    private static String guardScope(Member member, List<Member> members) {

        var scope = new StringBuilder(member.text());
        for (var other : members) {
            if (other == member) {
                continue;
            }
            if (member.name() != null && callsMethod(other.text(), member.name())) {
                scope.append(' ').append(other.text());
            } else if (other.name() != null && callsMethod(member.text(), other.name())) {
                scope.append(' ').append(other.text());
            }
        }
        return scope.toString();
    }

    private static boolean callsMethod(String text, String methodName) {

        return Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(").matcher(text).find();
    }

    /**
     * A file is in scope only if it references one of the write mechanisms this codebase actually uses. This is a
     * best-effort proxy for "this file can perform a write", not a proof of it -
     * {@link #noExemptFileExecutesRawInsertOrUpdateSql()} is the check that keeps the proxy honest as the codebase
     * grows.
     */
    private boolean isScanned(Path file) {

        try {
            return isScanned(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isScanned(String source) {

        return WRITE_MECHANISM_SYMBOL.matcher(SourceMasking.mask(source)).find();
    }

    private static List<String> accessorMatches(String text) {

        var found = new ArrayList<String>();
        DIRECT_ID.matcher(text).results().forEach(result -> found.add(result.group()));
        NESTED_SECTION_TYPE_ID.matcher(text).results().forEach(result -> found.add(result.group()));
        return found;
    }

    /**
     * {@code name} is null for a member {@link #memberName} cannot read one off, e.g. a field or an instance
     * initializer; such a member is still scanned for offenses, it just cannot be found as anyone else's callee or
     * caller.
     */
    private record Member(int startLine, String name, String text) {
    }

    private static final Pattern IDENTIFIER_BEFORE_PAREN = Pattern.compile("(\\w+)\\s*\\(");

    /**
     * Splits a masked source into its top-level members - each method, constructor and nested-type body - by tracking
     * brace depth: a member starts where depth first reaches 2 (one level inside the class body) and ends where it
     * returns to 1. Braces inside a member's own control flow (an {@code if}, a lambda, a switch) push depth past 2 and
     * back without ending it, because only the exact transition back to 1 closes a member. A nested type's own methods
     * are not split further and are scanned as one member together with it; this codebase has none of those carrying
     * the shapes this test looks for.
     */
    private static List<Member> members(String masked) {

        var members = new ArrayList<Member>();
        int depth = 0;
        int line = 1;
        int startOffset = -1;
        int startLine = -1;
        int headerStart = 0;
        for (int i = 0; i < masked.length(); i++) {
            var c = masked.charAt(i);
            if (c == '\n') {
                line++;
            }
            if (c == '{') {
                depth++;
                if (depth == 2) {
                    startOffset = i + 1;
                    startLine = line;
                }
            } else if (c == '}') {
                if (depth == 2) {
                    var header = masked.substring(headerStart, startOffset - 1);
                    members.add(new Member(startLine, memberName(header), masked.substring(startOffset, i)));
                    headerStart = i + 1;
                }
                depth--;
            }
        }
        return members;
    }

    /**
     * The method or constructor name a member's signature declares, read as the last identifier immediately followed by
     * {@code (} before the body's opening brace. Usually the parameter list's own name, not an annotation's like
     * {@code @McpToolParam(...)} earlier in the signature - but not always: a parameter annotation whose own arguments
     * are the last thing before the brace, such as {@code @RequestHeader(value = ..., required = false)} or
     * {@code @Qualifier("...")} on the final parameter, is misread as the member's name instead. See the class javadoc
     * for why this failure is conservative rather than dangerous.
     */
    private static String memberName(String header) {

        String name = null;
        var matcher = IDENTIFIER_BEFORE_PAREN.matcher(header);
        while (matcher.find()) {
            name = matcher.group(1);
        }
        return name;
    }

}
