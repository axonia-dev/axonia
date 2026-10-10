package dev.axonia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.tenancy.OrganizationScope;
import dev.axonia.shared.api.tenancy.SubjectScope;
import dev.axonia.shared.configuration.TenantDiscoveryPool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A public method on a Spring-managed bean that calls a repository and carries no {@code @Transactional} gets one
 * connection per repository call, and every connection pays {@code OrganizationAwareDataSource}'s up to four RLS round
 * trips - a {@code set_config} and a matching {@code RESET} for each of organization and subject, only when that scope
 * is present. At 214 such methods, an ordinary page of the API cost 686 connections against a pool of 16, and eight
 * concurrent readers returned eight HTTP 500s.
 *
 * <p>
 * <strong>Known mesh.</strong> This is a text scan, not a proof, and it errs in both directions. Every gap below lets a
 * real untransacted read path through unless it is marked as over-flagging.
 * <ul>
 * <li><strong>Other files.</strong> It only opens files whose text contains {@code @Service} or {@code @Component}, so
 * a repository call from an unannotated helper class, or from a bean carrying some other stereotype, is invisible. And
 * it never resolves a call to its target: a repository reached through an interface whose implementation lives in
 * another file is not seen, and neither is that target's own annotation. Only the text present in the file being
 * scanned is ever read. Within one file the scan follows helpers transitively, whatever their visibility - see
 * <em>Repository reach</em> below - so within a file this gap really is a file boundary; across files it is still a
 * hard wall, and a repository reached one hop into another class is invisible however that class is annotated.
 * <li><strong>Void methods.</strong> A signature containing {@code " void "} is skipped outright: this guard is about
 * read paths, and a void method is a command rather than a query. A void method that fans N repository reads out before
 * it writes is therefore never named here, and has to be found by reading.
 * <li><strong>Static methods.</strong> {@link #PUBLIC_METHOD}'s return-type character class swallows the word
 * {@code static}, so a {@code public static} method scans exactly like an instance one - and on a static method
 * {@code @Transactional} is permanently inert, because Spring's proxy-based AOP has no instance to intercept. This has
 * already cost one round trip: an earlier revision of this guard made a {@code public static String} carry an
 * annotation that could never fire, reverted in commit {@code a751e84b}. If this guard ever names a {@code static}
 * method, the fix is to move the repository work off the static method, never to annotate it.
 * <li><strong>Receiver-blind name matching.</strong> Nothing here resolves overloads. A helper is matched by its bare
 * name, restricted only to calls that are unqualified or written on {@code this} (see {@link #callsAnyOf}), so two
 * overloads, or a private and a public method sharing a name, are one name to this scan. That is deliberate - resolving
 * Java properly is not this test's job. Since {@link #methodBodies} merges rather than replaces, every declaration
 * under a name contributes its calls, so the collision attributes one name's calls too WIDELY and never too narrowly.
 * It was the other way round until bodies were merged, which is the shape this bullet used to describe as merely "one
 * name too coarse".
 * <p>
 * Too widely is <em>not</em> the harmless direction it was long claimed to be here ("it can ask for an annotation a
 * method does not need, and cannot excuse one that does"). {@link #methodsReaching} is used twice, so a name whose
 * calls are attributed too widely helps the indictment and <strong>hurts the exemption</strong>: one of two overloads
 * calling {@code runAs} put the whole name into the {@code runAs} reach and excused a caller of the OTHER overload that
 * read the repository in its own body. That is the same member and the same direction as two regressions this file has
 * already shipped, so it is closed rather than documented: a name this file declares more than once
 * ({@link #namesDeclaredMoreThanOnce}) can never confer the {@code runAs} exemption, though it is still indicted on the
 * merged body like any other. It was not live when it was found, and
 * {@code doesNotLetAnOverloadedNameCarryTheRunAsExemptionToACallerThatReadsForItself} keeps it that way.
 * <li><strong>Decoration lookback.</strong> A method reads as untransacted when the contiguous run of annotation and
 * javadoc lines directly above its signature holds no {@code @Transactional} (see {@link #annotationBlockAbove}). An
 * annotation split across two lines by its own arguments would be missed and the method flagged for one it already has.
 * So would a public method of an anonymous inner class declared inside an annotated method, whose enclosing transaction
 * the flat window used to reach - nothing in {@code src/main} writes that shape today. Those two are the over-flagging
 * shapes, and the list is meant to be exhaustive: the flat six-line window this replaced under-flagged instead,
 * reaching over a member boundary onto the previous method's annotation.
 * </ul>
 *
 * <p>
 * <strong>Receivers resolve by declared type, not by what the field is called.</strong> {@link #REPOSITORY_CALL} is a
 * name pattern - {@code epository}, {@code jdbc}, {@code Template} - so a repository held in a field named for the rows
 * it holds was invisible to it: 31 fields across 22 files, live in seven read paths at once, among them
 * {@code ReactionService.tally}. The boundary it drew tracked the regex rather than any decision, leaving
 * {@code RecognitionService} and {@code ReactionCatalogService} annotated beside an unannotated
 * {@code ReactionService}. So {@link #receiverDeclaration} reads each file's field and constructor-parameter
 * declarations, and adds every receiver whose declared TYPE is a repository or a JDBC template to that file's own call
 * pattern ({@link #repositoryCallIn}).
 *
 * <p>
 * One shape is still invisible and deliberately left so: a method reference, {@code repository::findById}, defeats
 * {@code REPOSITORY_CALL}'s literal {@code \.}. Widening for it surfaces zero offenders in {@code src/main} today, so
 * it stays a documented gap rather than an untested widening of the pattern that governs every future read path.
 *
 * <p>
 * Comments and string/char/text-block literals are masked first (via {@link SourceMasking#mask}, shared verbatim with
 * {@link CrossCommunityReferenceGuardTest}), so neither can immunise or falsely indict a method by accident.
 *
 * <p>
 * <strong>Class-level {@code @Transactional}.</strong> {@link #hasClassLevelTransactional} treats every public method
 * in a file as covered when a {@code class} declaration in that file carries {@code @Transactional} in the decoration
 * block above it. A class-level annotation really does cover every method below it, and a method's own lookback stops
 * at its own member boundary and so would never find it, which is why the check exists. It is dead defensive code today
 * - no file in {@code src/main} carrying {@code @Service} or {@code @Component} declares {@code @Transactional} on the
 * type - and it is kept so that adopting the class-level style later does not silently produce a file's worth of false
 * indictments.
 *
 * <p>
 * It used a flat six-line window until this was written, and that was the same member-boundary defect removed from the
 * method lookback, with a strictly larger blast radius: {@link #CLASS_DECLARATION} matches a NESTED {@code class} /
 * {@code final class} / {@code abstract class} too, so an ordinary <em>method-level</em> {@code @Transactional} landing
 * within six lines above a nested type returned true and blanketed the whole file as covered. No class-level annotation
 * was needed to fire it. It was safe only because no {@code @Service} or {@code @Component} file in {@code src/main}
 * currently holds a second class declaration - and the shape is already written elsewhere in the tree
 * ({@code ValidMeetingUrl}, {@code ValidTimeZone} each nest a {@code Validator}), so it was one nested validator inside
 * a bean away from silently covering that bean's every read.
 * {@code doesNotLetANestedClassTurnAMethodAnnotationIntoAWholeFileExemption} pins it.
 *
 * <p>
 * <strong>Method extent.</strong> {@code REPOSITORY_CALL}, {@code RUN_AS_CALL} and every call-name match are searched
 * only within a method's own body - from the character after its opening brace to its matching close, found by
 * brace-balancing (see {@link #bodyOf} and {@link #methodExtentEnd}) - never by a flat line count that could run past
 * the method's own closing brace into whatever comes next, and never including the signature line itself, so a method
 * cannot match its own name and exempt itself as its own callee. An earlier revision used a flat 45-line lookahead
 * instead, and it biased in both directions at once: {@code REPOSITORY_CALL} over-flagged a short, repository-free
 * method followed closely by one that does call a repository, while {@code RUN_AS_CALL} under-flagged in exactly the
 * same shape: a method that never calls {@code runAs} itself read as exempt whenever some later method within the
 * window did. Bounding the patterns to the real method body fixed both. Bounding the extent did NOT, on its own, make
 * the scan honest: it narrowed the indictment to a method's own body while the {@code runAs} exemption was following
 * private helpers transitively, so roughly twenty-one real read paths whose repository work sat in a private helper of
 * the same file fell out of the offender list silently. That asymmetry is what <em>Repository reach</em> below exists
 * to close.
 *
 * <p>
 * <strong>Repository reach.</strong> A public method touches a repository when its own body does, OR when a method it
 * calls on {@code this} does, transitively within the same file - the exact rule the {@code runAs} exemption already
 * used, applied to the indictment as well as to the excuse, through the same fixed point (see {@link #methodsReaching}
 * and {@link #callsAnyOf}). Without it, {@code EventsPersonalDataExporter.export} (six round trips through private
 * helpers), {@code OrganizationOwnership.isSoleOwner} and its siblings (two each) and more all read as clean.
 * {@code REPOSITORY_CALL} also tolerates whitespace around the receiver's dot, because the body is joined with newlines
 * and the formatter routinely breaks a fluent call between the receiver and its first {@code .}.
 *
 * <p>
 * The helper set is <strong>every</strong> method declared in the file, not only the private ones
 * ({@link #ANY_METHOD}). A same-file call on {@code this} is a self-invocation, and Spring's proxy-based AOP cannot
 * intercept one: the callee's own {@code @Transactional} does not apply on that path, so the caller is the only place
 * an annotation can take effect, and the caller therefore has to answer for the callee's repository work. Restricting
 * the set to {@code private} left a live path unseen: {@code DefaultAuthorizationGate.hasPermission} self-invoked the
 * annotated {@code currentPermissions()} on <em>every gated request</em>, so that annotation was inert there and each
 * check cost two to four uncollapsed checkouts. It is annotated at the proxy boundary now. Note that widening the set
 * widens the {@code runAs} exemption in the same breath, which is correct for the same reason and in the same
 * direction: if a public method reaches {@code runAs} through a same-file public method, annotating its caller is
 * exactly as wrong as it would be through a private one.
 *
 * <p>
 * <strong>Pools that no transaction manager spans are out of scope.</strong> This guard is about connection-per-call on
 * the primary, {@code @Primary}, {@code OrganizationAwareDataSource}-wrapped pool - the only one a
 * {@code PlatformTransactionManager} covers. {@link TenantDiscoveryPool} builds a second pool, on a deliberately
 * cross-organization role, and exposes it as the {@code tenantDiscoveryJdbcClient} bean with no manager over it.
 * {@code JdbcTemplate} looks for a transaction-bound connection <em>for its own DataSource</em>, so a
 * {@code @Transactional} method that queries only through that client consolidates nothing: every query still checks a
 * connection out of that pool and returns it, and the annotation additionally pins one connection of the
 * <em>primary</em> pool - the pool of 16 this whole plan exists to stop exhausting - for the method's whole duration,
 * for nothing. So calls on a receiver a file declares under a qualifier in {@link #unspannedPoolQualifiers} are not
 * repository calls as far as this guard is concerned (see {@link #unspannedPoolReceivers} and {@link #blankReceiver}).
 * This is deliberately narrow: it excludes named receivers in the files that declare them, never a file, a package or a
 * type, so a class that also reads a primary-pool repository is still indicted for that. Each qualifier is named and
 * reasoned, never a pattern over {@code JdbcClient}, which would take every future qualifier out of this guard's
 * subject in silence. The qualifier is matched against the RAW source rather than the masked copy, because
 * {@link SourceMasking#mask} blanks the string literal the qualifier is named by. If a pool ever gets a transaction
 * manager of its own, its qualifier leaves the set and its readers are annotated against that manager by name;
 * {@link #noTransactionManagerBeanIsDeclaredOverAnUnspannedPool} fails the build on each configuration this half owns
 * ({@link #ownedPoolConfigurations}).
 *
 * <p>
 * <strong>The {@code runAs} exemption (RULING R9).</strong> {@link OrganizationScope#runAs} and
 * {@link SubjectScope#runAs} set the Postgres RLS GUC ({@code app.current_org} / the subject equivalent) by opening a
 * connection and running {@code set_config} on it. If a method carrying {@code @Transactional} calls one of those
 * inside its own body, the transaction has already acquired its connection - and therefore already chosen its RLS scope
 * - before {@code runAs} ever runs, so the scope it sets is silently ignored for that transaction and every repository
 * call inside it runs under whatever scope (or lack of one) the connection already had. This fails with no exception
 * and no failing assertion in this codebase's tests, because the tests connect to Testcontainers Postgres as the schema
 * owner, who bypasses RLS by ownership; in production it denies every row. So a method whose masked body calls
 * {@code OrganizationScope.runAs} or {@code SubjectScope.runAs} must NEVER carry {@code @Transactional} - not even to
 * satisfy this guard - and this guard treats such a method as covered on that basis, not because it is safe to ignore,
 * but because leaving it unannotated is the only correct wiring: {@code runAs} has to wrap the transactional boundary,
 * not sit inside it, and the transactional boundary belongs in a separate bean invoked from inside the {@code runAs}
 * call, not in this method.
 *
 * <p>
 * The exemption follows same-file calls transitively, not just one hop, and follows them whatever the callee's
 * visibility: a method that never writes the word {@code runAs} itself is still exempt if it calls (or references,
 * {@code this::name}) a same-file method that reaches {@code runAs}, however many hops deep. Annotating a caller two
 * hops above {@code runAs} would open a transaction before the {@code runAs} below it ever ran, the exact ordering
 * hazard R9 exists to prevent, regardless of how many frames sit in between. A cap at one hop - the distance
 * {@link CrossCommunityReferenceGuardTest#guardScope} trusts for a different check - would flag such a caller for an
 * annotation that is just as wrong two hops down as it is one hop down, so this exemption follows the chain to a fixed
 * point instead: name matching only, never resolving which overload, exactly as one hop already did.
 *
 * <p>
 * <strong>When a transaction is worth annotating for, and when call-count is not the argument.</strong> The default is
 * to annotate: a read that touches a repository at all gets {@code @Transactional(readOnly = true)}, including one that
 * makes a single call. Seven of the annotated reads make exactly one - {@code OrganizationOwnership
 * .isOwnerRoleBinding}, {@code SessionInstant.of}, {@code BookmarkService.saveState}, {@code ReactionCatalogService
 * .mine}, {@code DefaultAuthorizationGate.currentRoleIds} and both {@code RoundupSettingsService} reads - and that is
 * deliberate, for two reasons: a caller already inside a transaction joins rather than opening a second one, and a
 * uniform rule is the only kind a structural guard can hold, since "how many calls does this really make" is not a
 * question a text scan can answer. Which is the second point: counting the calls in a method's own body understates the
 * real cost, often several-fold. {@code GroupBindingService.list} reads as "one repository call" and in fact fans out
 * to roughly four checkouts once {@code gate.requirePermission} and
 * {@code DirectoryGroupConfirmation.keysAwaitingConfirmation} are counted. So call-count is never a reason to skip an
 * annotation on its own, and never a staleness trigger; it is at most one term weighed against a concrete, named cost
 * on the other side - which, for the named exemption below, is a pooled connection pinned across a network round trip.
 *
 * <p>
 * <strong>Named exemptions (RULING R11).</strong> A method whose {@code @Transactional} would be actively wrong, for a
 * reason none of the patterns above expresses, is exempted by fully-qualified name: one entry, one reason, never a
 * broadened pattern. There is no narrow, non-gameable textual marker for such a hazard the way
 * {@code OrganizationScope.runAs(} is one for the RLS-ordering hazard, so each new case earns a named entry with its
 * own reasons. This half holds one, {@link #GROUP_BINDING_SERVICE_LIST}, which would otherwise pin a pooled connection
 * across a Microsoft Graph exchange; {@link #namedExemptionsStillMakeTheOutboundCallTheirReasonNames} fails the build
 * if the call its reason names goes.
 *
 * <p>
 * Each build unit scans its own {@code src/main/java}: this class core's, with its exemptions and pool; the hosted
 * edition's own guard covers its modules.
 */
class ReadPathTransactionGuardTest {

    private static final Pattern PUBLIC_METHOD = Pattern
            .compile("^\\s*public\\s+(?!class|interface|record|enum)[\\w<>,\\[\\]. ?]+\\s+(\\w+)\\s*\\(");

    private static final Pattern REPOSITORY_CALL = Pattern.compile("(epository|jdbc|Template)\\s*\\.\\s*\\w+\\(");

    private static final Pattern IMPLEMENTS = Pattern.compile("\\bimplements\\s+([^{;]+?)\\s*\\{");

    private static final Pattern INNERMOST_TYPE_ARGUMENTS = Pattern.compile("<[^<>]*>");

    private static final Path PORTS = Path.of("application", "port", "out");

    private static final Path DRIVEN_ADAPTERS = Path.of("adapter", "out");

    private static final Pattern BARE_RECEIVER_DECLARATION = repositoryField(Set.of());

    /**
     * A field or constructor-parameter declaration whose <strong>type</strong> is a repository or a JDBC template,
     * capturing whatever name that declaration gives it. {@link #REPOSITORY_CALL} matches a receiver by what it is
     * called, so a repository held in a field named for the rows it holds - {@code rates}, {@code readings},
     * {@code cards} - was invisible to it; this is what makes the receiver resolve by declared type instead. See the
     * class javadoc's <em>Receivers resolve by declared type</em> paragraph for the seven live read paths that gap hid.
     *
     * <p>
     * The optional {@code <...>} lets a receiver declared as {@code CrudRepository<Foo, Long> repo} match too, and the
     * trailing character class is what keeps a method declaration out: {@code public ThingRepository rates()} is
     * followed by {@code (}, never by one of {@code ; , ) =}. Matched against the masked copy, so a type name written
     * inside a comment or a string cannot invent a receiver.
     */
    private final Pattern receiverDeclaration = repositoryField(persistencePorts());

    /**
     * {@link #receiverDeclaration}'s types: the repository and JDBC-template types, plus every outbound port its module
     * reads its rows through. An application service holds a port, not a repository, so without the ports the read that
     * crosses one was invisible here, whatever adapter it reached.
     */
    private static Pattern repositoryField(Set<String> persistencePorts) {

        var types = Stream
                .concat(Stream.of("\\w*Repository", "JdbcClient", "NamedParameterJdbcTemplate", "JdbcTemplate",
                        "JdbcAggregateTemplate"), persistencePorts.stream().sorted().map(Pattern::quote))
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?<![\\w$])(?:" + types + ")(?:<[^>]*>)?\\s+(\\w+)\\s*[;,)=]");
    }

    /**
     * The {@code application.port.out} interfaces, at any depth under {@code port.out}, implemented by a class under
     * {@code adapter.out} that declares a repository or JDBC receiver on a pool a transaction manager spans: the ports
     * whose calls reach the database. Read from what the implementer holds, not from the package it sits in, so a
     * driven adapter placed in {@code adapter.out.jdbc} counts as one in {@code adapter.out.persistence} does. A port
     * to another module or to an outside system is not one of them, so a service reaching it is not held to this guard
     * by that call alone.
     */
    Set<String> persistencePorts() {

        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            var sources = files.filter(f -> f.toString().endsWith(".java")).toList();
            var ports = sources.stream().filter(f -> within(f, PORTS))
                    .map(f -> f.getFileName().toString().replace(".java", "")).collect(Collectors.toSet());
            var implemented = new HashSet<String>();
            sources.stream().filter(f -> within(f, DRIVEN_ADAPTERS)).map(ReadPathTransactionGuardTest::read)
                    .filter(this::holdsASpannedReceiver).forEach(text -> implemented.addAll(implementedTypes(text)));
            implemented.retainAll(ports);
            return implemented;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean holdsASpannedReceiver(String text) {

        var unspanned = unspannedPoolReceivers(text, unspannedPoolQualifiers());
        var receiver = BARE_RECEIVER_DECLARATION.matcher(SourceMasking.mask(text));
        while (receiver.find()) {
            if (!unspanned.contains(receiver.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean within(Path file, Path layer) {

        for (var dir = file.getParent(); dir != null; dir = dir.getParent()) {
            if (dir.endsWith(layer)) {
                return true;
            }
        }
        return false;
    }

    /** The simple names in every {@code implements} clause of the source, type arguments of any depth dropped. */
    static Set<String> implementedTypes(String text) {

        var names = new HashSet<String>();
        var clause = IMPLEMENTS.matcher(SourceMasking.mask(text));
        while (clause.find()) {
            var types = clause.group(1);
            while (INNERMOST_TYPE_ARGUMENTS.matcher(types).find()) {
                types = INNERMOST_TYPE_ARGUMENTS.matcher(types).replaceAll("");
            }
            Stream.of(types.split(",")).map(name -> name.replace("[]", "").trim().replaceAll(".*\\.", ""))
                    .filter(name -> !name.isEmpty()).forEach(names::add);
        }
        return names;
    }

    private static final Pattern RUN_AS_CALL = Pattern.compile("(OrganizationScope|SubjectScope)\\.runAs\\(");

    /** A balanced run of type arguments, nested up to three levels deep: the only place a type may hold a comma. */
    private static final String GENERIC_ARGUMENTS = "<(?:[^<>]|<(?:[^<>]|<[^<>]*>)*>)*>";

    /**
     * Any method declaration in the file, whatever its visibility - the helper set both fixed points walk. Deliberately
     * not restricted to {@code private}: a same-file <em>public</em> helper invoked on {@code this} is a
     * self-invocation, which Spring's proxy cannot intercept, so its own {@code @Transactional} does not apply on that
     * path and the caller has to answer for the repository work. The leading negative lookahead rules out statements
     * that would otherwise parse as a declaration - {@code return new Foo(} reads as "type {@code return new}, name
     * {@code Foo}" without it - and the second rules out type declarations.
     *
     * <p>
     * Two details here were each got wrong once, and it is worth being exact about which of them still carries weight.
     * <ol>
     * <li><strong>The keyword lookahead earns its place.</strong> Without it a {@code return} of a call that opens a
     * lambda block registers as a declaration named for that call, and is handed the lambda's block as a body, so every
     * caller of the real method inherits whatever that block touches.
     * {@code doesNotTreatABareStatementCallAsADeclaration} fails if it is removed.
     * <li><strong>The type must begin with a non-space</strong> ({@code [\w\[\]?]} or a type-argument list), because a
     * space is otherwise a legal first character of the type and a bare statement {@code turnRenewalOn(subject);}
     * parses as "type = the indentation, name = {@code turnRenewalOn}". Same test, same failure.
     * <li><strong>Placing the lookahead before {@code \s*} rather than after it is redundant, given 2.</strong> It was
     * a real bug once - with the indentation matched first the engine backtracks the whitespace by one character, the
     * lookahead then reads a leading space instead of the keyword and passes, and {@code return heldBy(subject)}
     * registered as a declaration of {@code heldBy}, which is exactly how a live self-invocation stayed hidden on the
     * first attempt at this widening. But every position that backtracking can reach leaves the type starting on
     * whitespace, which 2 rejects outright, so with 2 in place the two orderings are provably equivalent and moving the
     * lookahead back after {@code \s*} breaks no test. It stays where it is as belt and braces, not because it is
     * load-bearing: this javadoc claimed it was, and that claim was untrue.
     * <li><strong>A comma belongs to the type only inside angle brackets.</strong> A top-level comma in the type let a
     * wrapped argument line such as {@code Owner.KEY, account(id)} parse as "type {@code Owner.KEY,}, name
     * {@code account}". That is a second declaration of {@code account}, and a name declared twice can never confer the
     * {@code runAs} exemption, so the public caller of a real {@code account} that reaches {@code runAs} was indicted
     * for the repository read a sibling helper made. {@link #GENERIC_ARGUMENTS} admits the commas of up to three levels
     * of nested type arguments ({@code Map<String, List<Map<String, Object>>>}) and nothing else.
     * {@code doesNotTreatAWrappedArgumentAsADeclaration} fails if the comma is let back in.
     * </ol>
     */
    private static final Pattern ANY_METHOD = Pattern.compile(
            "^(?!\\s*(?:return|new|throw|if|for|while|else|case" + "|switch|catch|do|yield|assert|super|this)\\b)"
                    + "\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|default)\\s+)*"
                    + "(?!class\\b|interface\\b|record\\b|enum\\b)" + "(?:[\\w\\[\\]?]|" + GENERIC_ARGUMENTS + ")"
                    + "(?:[\\w\\[\\]. ?]|" + GENERIC_ARGUMENTS + ")*?\\s+(\\w+)\\s*\\(");

    /** A plain call ({@code name(}) or a method reference ({@code ::name}), either of which names a callee. */
    private static final Pattern CALL_OR_REFERENCE = Pattern.compile("(\\w+)\\s*\\(|::\\s*(\\w+)");

    /**
     * A method declaration returning a transaction manager - see
     * {@link #noTransactionManagerBeanIsDeclaredOverAnUnspannedPool}.
     */
    private static final Pattern TRANSACTION_MANAGER_BEAN = Pattern.compile("\\w*TransactionManager\\s+\\w+\\s*\\(");

    private static final Pattern CLASS_DECLARATION = Pattern
            .compile("^\\s*(?:public\\s+)?(?:final\\s+)?(?:abstract\\s+)?class\\s+\\w+");

    /** The named exemption this half holds. Never a pattern here - only another reasoned entry. */
    protected Set<String> namedExemptions() {

        return Set.of(GROUP_BINDING_SERVICE_LIST);
    }

    /** Each named exemption resting on an outbound call, by the call that is its whole reason. */
    protected Map<String, String> outboundCallsOfNamedExemptions() {

        return Map.of(GROUP_BINDING_SERVICE_LIST, "directory.listGroups(");
    }

    /**
     * Every bean qualifier naming a {@code JdbcClient} over a pool no {@code PlatformTransactionManager} spans, whose
     * calls this half's scan blanks. Each entry named and reasoned, never a pattern: see the class javadoc's <em>second
     * and third datasources</em> paragraph for why a {@code @Transactional} on a method that queries only through one
     * of these makes the program strictly worse. Another pool earns another named entry and a paragraph of its own,
     * never a wildcard on {@code JdbcClient} - which would silently take every future qualifier, primary-pool ones
     * included, out of this guard's subject.
     */
    protected Set<String> unspannedPoolQualifiers() {

        return Set.of("tenantDiscoveryJdbcClient");
    }

    /**
     * The configurations this half owns that build one of those pools. A half may query a pool the other half builds,
     * as closed modules query the tenant-discovery one, but it holds only its own configurations to the no transaction
     * manager rule.
     */
    protected Set<String> ownedPoolConfigurations() {

        return Set.of("src/main/java/dev/axonia/shared/configuration/TenantDiscoveryPool.java");
    }

    /**
     * {@code DefaultAuthorizationGate.requirePermission} carries {@code @Transactional}, pinned here by name because
     * the main scan cannot see it: the signature contains {@code " void "} and {@link #offendersIn} skips those
     * outright, so deleting this annotation is a silent regression while deleting {@code hasPermission}'s next to it is
     * caught. It is worth a pin of its own rather than a widening of the scan: this is the authorization boundary every
     * gated request in the application enters through, and it is a proxy boundary - {@code requirePermission} calls
     * {@code hasPermission} on {@code this}, so the annotation below it does not fire on this path.
     */
    private static final String DEFAULT_AUTHORIZATION_GATE_REQUIRE_PERMISSION = "src/main/java/dev/axonia/access/application/DefaultAuthorizationGate.java#requirePermission";

    /** The void methods this half pins as {@code @Transactional}, as {@code file#method}. */
    protected Set<String> transactionalPins() {

        return Set.of(DEFAULT_AUTHORIZATION_GATE_REQUIRE_PERMISSION);
    }

    /**
     * A named exemption on its own reasons - never generalised with the Cloud half's outbound exemptions into a "holds
     * a connection across network I/O" pattern, which is exactly the broadened rule RULING R11 forbids:
     * <ol>
     * <li>Annotating {@code list} would hold a pooled connection across {@code directory.listGroups(organizationId)}, a
     * synchronous Microsoft Graph exchange - {@code RoutingIdentityProviderDirectory.entraOrNone} to
     * {@code EntraDirectoryAdapter}, whose {@code GraphGroups} issues
     * {@code GET https://graph.microsoft.com/v1.0/groups} over a {@code RestClient} that first mints a
     * client-credentials token whenever it holds none. Spring's {@code DataSourceTransactionManager.doBegin} acquires
     * the connection eagerly and {@code OrganizationAwareDataSource} runs its {@code set_config} on checkout, so the
     * connection would be bound from method entry through the whole Graph round trip, including its timeout when Graph
     * does not answer. That is the pool-exhaustion vector this guard exists to remove.
     * <li>On the other side of that trade the win is modest, though not zero - and an earlier revision of this entry
     * had it badly wrong, claiming the method "makes exactly ONE repository call" so a transaction "would buy nothing".
     * True of the body, false of the method: {@code list} also calls {@code gate.requirePermission}, which self-invokes
     * through to a further two-to-four checkouts, and {@code DirectoryGroupConfirmation.keysAwaitingConfirmation}, a
     * second bean's own transaction. Annotating would collapse roughly four checkouts, not zero. It still should not be
     * annotated, because reason 1 stands on its own: no number of saved checkouts is worth pinning a connection across
     * a Graph call and its timeout.
     * </ol>
     * Reason 1 is the whole of this exemption, so its staleness trigger is reason 1's: if {@code list} ever stops
     * calling the directory port, delete this entry and annotate the method like any other read this guard names.
     * Deliberately NOT triggered on "grows a second repository call", the trigger this entry used to carry: that is
     * keyed to a measure which already understated the true cost about fourfold.
     * {@link #namedExemptionsStillMakeTheOutboundCallTheirReasonNames} fails the build if the Graph call goes.
     */
    private static final String GROUP_BINDING_SERVICE_LIST = "src/main/java/dev/axonia/access/application/GroupBindingService.java#list";

    /**
     * A {@code @Bean} method returning a transaction manager, anywhere in {@code src/main} - the one change that makes
     * the unspanned-pool scope-out above stale, and it would make it stale silently. Two things would happen at once:
     * the excluded pool could be spanned by a transaction (so its reads should be annotated against that manager rather
     * than excluded), and Spring Boot's own {@code DataSourceTransactionManagerAutoConfiguration}, which is
     * {@code @ConditionalOnMissingBean(TransactionManager.class)}, would back off the primary one. So this assertion is
     * not tidiness: it is the trigger on promises that were prose until now.
     *
     * <p>
     * Every configuration is named, not just one. {@link #ownedPoolConfigurations} names each configuration this half
     * owns, and each one's staleness has to be build-failing on its own: a transaction manager appearing over one pool
     * alone must not leave the other pool's half of this assertion green.
     */
    @Test
    void noTransactionManagerBeanIsDeclaredOverAnUnspannedPool() {

        for (var config : ownedPoolConfigurations()) {
            assertThat(SourceMasking.mask(read(Path.of(config))))
                    .as(config + " must declare no transaction manager - see this guard's paragraph on pools that no "
                            + "transaction manager spans")
                    .doesNotContain("TransactionManager");
        }

        assertThat(transactionManagerBeans())
                .as("no @Bean anywhere in src/main may return a TransactionManager: Boot's own auto-configured "
                        + "manager is @ConditionalOnMissingBean(TransactionManager.class), so declaring one silently "
                        + "removes it, and it would make this guard's unspanned-pool scope-out stale")
                .isEmpty();
    }

    /**
     * Each outbound named exemption still makes the outbound call its reason names. Every such entry rests entirely on
     * reason 1 - a pooled connection pinned across network I/O - so if that call goes, the exemption is stale and the
     * method should be annotated like any other read. That was a sentence in three javadocs and nothing more; it is a
     * build failure now.
     */
    @Test
    void namedExemptionsStillMakeTheOutboundCallTheirReasonNames() {

        outboundCallsOfNamedExemptions().forEach((exemption, outboundCall) -> assertThat(exemptedMethodBody(exemption))
                .as(exemption + " is exempted because IT holds a connection across " + outboundCall + "...); if that "
                        + "call is gone from this method, delete the exemption and annotate the method - moving the "
                        + "call to a sibling in the same file does not keep the exemption alive")
                .contains(outboundCall));
    }

    /**
     * The body of the one method a named exemption names, not its whole file. The exemption is method-scoped, so the
     * assertion on its reason has to be too: moving the outbound call out of the exempted method into a sibling in the
     * same file would leave a file-scoped check green while the exemption excused a method that no longer earns it.
     * Every declaration of that name contributes, since this scan cannot tell overloads apart - the same reason
     * {@link #methodBodies} merges.
     */
    static String exemptedMethodBody(String exemption) {

        var name = exemption.split("#")[1];
        var lines = SourceMasking.mask(read(Path.of(exemption.split("#")[0]))).split("\n");
        var body = new StringBuilder();
        for (var i = 0; i < lines.length; i++) {
            var signature = ANY_METHOD.matcher(lines[i]);
            if (signature.find() && signature.group(1).equals(name)) {
                body.append(bodyOf(lines, i)).append('\n');
            }
        }
        return body.toString();
    }

    /** Each {@link #transactionalPins} entry, a void method the main scan skips, still carries its annotation. */
    @Test
    void pinnedVoidMethodsStayTransactionalEvenThoughTheScanCannotSeeThem() {

        for (var pin : transactionalPins()) {
            var name = pin.split("#")[1];
            var lines = SourceMasking.mask(read(Path.of(pin.split("#")[0]))).split("\n");
            var annotated = false;
            for (var i = 0; i < lines.length; i++) {
                var signature = ANY_METHOD.matcher(lines[i]);
                if (signature.find() && name.equals(signature.group(1)) && lines[i].contains(" void ")) {
                    annotated = annotationBlockAbove(lines, i).contains("@Transactional");
                }
            }
            assertThat(annotated).as(pin + " must stay @Transactional - it is void, so the main scan skips it")
                    .isTrue();
        }
    }

    /**
     * A half reads only its own files: every path its hooks name exists in the build unit it runs in, so an entry left
     * behind by a file that moved to the other build unit, or was deleted, fails here rather than exempting nothing.
     */
    @Test
    void everyFileThisHalfNamesExistsInItsBuildUnit() {

        var named = Stream.of(namedExemptions(), outboundCallsOfNamedExemptions().keySet(), transactionalPins(),
                ownedPoolConfigurations()).flatMap(Set::stream).map(entry -> Path.of(entry.split("#")[0]));

        assertThat(named.filter(path -> !Files.isRegularFile(path)).distinct().toList())
                .as("files this half's hooks name that are not in its build unit - move the entry to the half whose "
                        + "build unit holds the file")
                .isEmpty();
    }

    private List<String> transactionManagerBeans() {

        var found = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            files.filter(f -> f.toString().endsWith(".java")).forEach(f -> {
                var lines = SourceMasking.mask(read(f)).split("\n");
                for (var i = 0; i < lines.length; i++) {
                    var above = String.join("\n", List.of(lines).subList(Math.max(0, i - 6), i));
                    if (TRANSACTION_MANAGER_BEAN.matcher(lines[i]).find() && above.contains("@Bean")) {
                        found.add(f + ":" + (i + 1));
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    /**
     * A few ports each half reads its rows through, so a narrowed derivation cannot drop the guard's scope silently.
     */
    protected Set<String> knownPersistencePorts() {

        return Set.of("BookmarkStore", "RoleStore", "OrganizationStore", "RecognitionStore");
    }

    @Test
    void thePersistencePortsIncludeTheKnownOnes() {

        assertThat(persistencePorts()).containsAll(knownPersistencePorts());
    }

    @Test
    void everyPublicServiceReadThatTouchesARepositoryIsTransactional() {

        assertThat(offenders()).as("public service methods calling a repository with no @Transactional - "
                + "see ReadPathTransactionGuardTest.namedExemptions' javadoc for the deliberate exceptions "
                + "and how to add another").isEmpty();
    }

    private List<String> offenders() {

        var found = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> found.addAll(offendersIn(p.toString(), read(p))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    /**
     * The whole scanner, over one file's source text, addressed by {@code label} - the path in a real run, a synthetic
     * name in the mechanics tests below. Kept callable on a string rather than only on a {@link Path} so the mechanics
     * that have actually regressed here can be exercised against known-bad and known-good inputs, instead of only
     * against whatever {@code src/main} happens to contain on the day. It reads this half's exemptions and pools.
     */
    List<String> offendersIn(String label, String text) {

        var found = new ArrayList<String>();
        if (!text.contains("@Service") && !text.contains("@Component")) {
            return found;
        }
        var unspannedReceivers = unspannedPoolReceivers(text, unspannedPoolQualifiers());
        var masked = withUnspannedPoolsBlanked(SourceMasking.mask(text), unspannedReceivers);
        var lines = masked.split("\n");
        var classLevelTransactional = hasClassLevelTransactional(lines);
        var bodies = methodBodies(lines);
        var repositoryCall = repositoryCallIn(masked, unspannedReceivers);
        var reachRunAs = methodsReaching(bodies, RUN_AS_CALL, namesDeclaredMoreThanOnce(lines));
        var reachRepository = methodsReaching(bodies, repositoryCall, Set.of());
        for (var i = 0; i < lines.length; i++) {
            var signature = PUBLIC_METHOD.matcher(lines[i]);
            if (!signature.find() || lines[i].contains(" void ")) {
                continue;
            }
            var above = annotationBlockAbove(lines, i);
            var body = bodyOf(lines, i);
            var covered = classLevelTransactional || above.contains("@Transactional")
                    || RUN_AS_CALL.matcher(body).find() || callsAnyOf(body, reachRunAs)
                    || namedExemptions().contains(label + "#" + signature.group(1));
            if (!covered && (repositoryCall.matcher(body).find() || callsAnyOf(body, reachRepository))) {
                found.add(label + ":" + (i + 1) + " " + signature.group(1));
            }
        }
        return found;
    }

    /**
     * Every receiver name this file declares for one of the {@link #unspannedPoolQualifiers}. A file may declare more
     * than one, so this collects them all rather than trusting a single {@code find()}. Matched against the raw source,
     * not the masked copy, because masking blanks the string literal inside the qualifier.
     */
    private static Set<String> unspannedPoolReceivers(String text, Set<String> qualifiers) {

        var receivers = new HashSet<String>();
        var matcher = Pattern
                .compile("@Qualifier\\(\"(?:" + String.join("|", qualifiers) + ")\"\\)\\s+JdbcClient\\s+(\\w+)")
                .matcher(text);
        while (matcher.find()) {
            receivers.add(matcher.group(1));
        }
        return receivers;
    }

    private static String withUnspannedPoolsBlanked(String masked, Set<String> receivers) {

        var blanked = masked;
        for (var receiver : receivers) {
            blanked = blankReceiver(blanked, receiver);
        }
        return blanked;
    }

    /**
     * {@link #REPOSITORY_CALL}, widened with every receiver this file declares with a repository or JDBC-template TYPE
     * - minus the receivers over a pool no transaction manager spans, which stay outside this guard's subject however
     * they are named. Built per file rather than held as a constant, because the receiver names are the file's own.
     * Falls back to the bare name pattern when the file declares no such field, which is what the mechanics tests that
     * declare no field at all rely on.
     */
    private Pattern repositoryCallIn(String masked, Set<String> unspannedReceivers) {

        var receivers = new ArrayList<String>();
        var field = receiverDeclaration.matcher(masked);
        while (field.find()) {
            if (!unspannedReceivers.contains(field.group(1)) && !receivers.contains(field.group(1))) {
                receivers.add(field.group(1));
            }
        }
        if (receivers.isEmpty()) {
            return REPOSITORY_CALL;
        }
        var named = receivers.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile(REPOSITORY_CALL.pattern() + "|(?<![\\w$])(?:" + named + ")\\s*\\.\\s*\\w+\\(");
    }

    /**
     * Every method name this file declares more than once. Only the {@code runAs} exemption consults it, and only to
     * refuse to let such a name confer that exemption - see the class javadoc's <em>Receiver-blind name matching</em>
     * bullet, and {@code doesNotLetAnOverloadedNameCarryTheRunAsExemptionToACallerThatReadsForItself}.
     */
    private static Set<String> namesDeclaredMoreThanOnce(String[] lines) {

        var seen = new HashSet<String>();
        var repeated = new HashSet<String>();
        for (var line : lines) {
            var signature = ANY_METHOD.matcher(line);
            if (signature.find() && !seen.add(signature.group(1))) {
                repeated.add(signature.group(1));
            }
        }
        return repeated;
    }

    /**
     * The contiguous run of annotation lines, and of the blanks a masked javadoc leaves behind, directly above a
     * signature - everything that really decorates THIS member, stopping at the first line belonging to anything else.
     *
     * <p>
     * Replaces a flat six-line lookback, which reached straight over a member boundary. With this repository's
     * formatting a minimal annotated method is five lines, so an {@code @Transactional} on one method sat inside the
     * six lines above the NEXT method's signature and silently covered it - an annotated read followed by an
     * unannotated one scanned clean. That was recorded in the mesh as an over-flagging gap "that costs nothing worse
     * than a redundant edit"; it was the opposite, and the only reason a mechanics test here did not trip over it was a
     * javadoc line inserted to push the two apart.
     */
    private static String annotationBlockAbove(String[] lines, int signatureIndex) {

        var start = signatureIndex;
        while (start > 0) {
            var previous = lines[start - 1].strip();
            if (!previous.isEmpty() && !previous.startsWith("@")) {
                break;
            }
            start--;
        }
        return String.join("\n", List.of(lines).subList(start, signatureIndex));
    }

    /**
     * Replaces {@code receiver} with underscores wherever it is the receiver of a call, so {@code REPOSITORY_CALL}
     * cannot match through it. Length-preserving and newline-preserving, exactly as {@link SourceMasking#mask} is, so
     * reported line numbers and the brace balancing in {@link #methodExtentEnd} both stay valid.
     */
    private static String blankReceiver(String masked, String receiver) {

        return Pattern.compile("(?<![\\w$])" + Pattern.quote(receiver) + "(?=\\s*\\.)").matcher(masked)
                .replaceAll("_".repeat(receiver.length()));
    }

    /**
     * Every method declared in this file, by name, mapped to its body text. See {@link #ANY_METHOD}.
     *
     * <p>
     * Bodies are <strong>merged</strong> under one name, never replaced, and that is load-bearing in two ways. Java
     * allows <em>overloads</em>, and this scan resolves nothing but names: two declarations of {@code isMember} are one
     * key here, so {@code put} would trust only whichever body came last and lose the other's repository calls
     * entirely. Eight live files have same-named declarations ({@code MembershipService.isMember},
     * {@code ScheduleService.schedule}, {@code SessionMapper.toSession} among them) where nothing is hidden today by
     * luck rather than by structure. And {@link #ANY_METHOD}, being a line regex and not a parser, occasionally reads a
     * <em>statement</em> as a declaration - a ternary continuation, {@code ? load(id)}, is the live shape, six files
     * write it - whose {@code bodyOf} is empty; under {@code put} that empty body <strong>overwrote the real
     * helper's</strong> and the caller silently stopped being flagged. Merging makes both harmless: a spurious empty
     * body adds nothing to a real one, and every overload's calls are seen. It cannot hide a call, only ever attribute
     * one name's calls a little too widely, which is the over-flagging direction.
     */
    private static Map<String, String> methodBodies(String[] lines) {

        var bodies = new LinkedHashMap<String, String>();
        for (var i = 0; i < lines.length; i++) {
            var signature = ANY_METHOD.matcher(lines[i]);
            if (signature.find()) {
                bodies.merge(signature.group(1), bodyOf(lines, i), (a, b) -> a + "\n" + b);
            }
        }
        return bodies;
    }

    /**
     * Every method in this file that reaches {@code marker} - directly, or transitively through another method in the
     * same file, whatever either one's visibility. Used twice, and symmetrically: with {@code RUN_AS_CALL} it is the
     * set a caller's {@code @Transactional} exemption is checked against, and with {@code REPOSITORY_CALL} it is the
     * set that makes a caller count as touching a repository. One fixed point serves both, so the exemption's reach and
     * the indictment's reach can never drift apart again - they did once, and roughly twenty-one real read paths fell
     * out of the offender list on that asymmetry alone.
     *
     * <p>
     * Computed as a fixed point: seed with every method whose own body matches {@code marker} directly, then repeatedly
     * add any method that calls (or references) a name already in the set, until a pass adds nothing. Terminates
     * because each pass only ever adds to the set, never removes from it, and there are finitely many methods in one
     * file - a call cycle between two of them simply stops contributing new names once both are already in the set,
     * rather than looping.
     *
     * <p>
     * {@code excluded} is the one place the two uses are NOT symmetrical, and deliberately so. Because
     * {@link #methodBodies} merges every declaration of a name into one body, an ambiguous name carries the union of
     * its declarations' calls - which over-flags for the indictment (harmless: at worst a redundant annotation) and
     * <em>under</em>-flags for the exemption (an excuse a method has not earned). So the {@code runAs} caller passes
     * {@link #namesDeclaredMoreThanOnce} here and the repository caller passes an empty set: a name declared twice can
     * never confer the exemption, though it is still indicted on the merged body like any other. Filtered inside the
     * fixed point rather than subtracted after it, because a name removed afterwards would already have pulled its
     * callers in.
     */
    private static Set<String> methodsReaching(Map<String, String> bodies, Pattern marker, Set<String> excluded) {

        var reachable = new HashSet<String>();
        for (var entry : bodies.entrySet()) {
            if (!excluded.contains(entry.getKey()) && marker.matcher(entry.getValue()).find()) {
                reachable.add(entry.getKey());
            }
        }

        var changed = true;
        while (changed) {
            changed = false;
            for (var entry : bodies.entrySet()) {
                if (!excluded.contains(entry.getKey()) && !reachable.contains(entry.getKey())
                        && callsAnyOf(entry.getValue(), reachable)) {
                    reachable.add(entry.getKey());
                    changed = true;
                }
            }
        }
        return reachable;
    }

    /**
     * Whether {@code body} calls or references, by name, any method of the same file listed in {@code names}, whatever
     * that method's visibility. The match must be unqualified or written on {@code this} ({@code name(},
     * {@code this.name(}, {@code this::name}); a call on any other receiver is not a call to this file's own method and
     * does not count. Without that check the matcher is a bare token search, and any future method in a file with an
     * exempt private {@code end} whose body happened to contain {@code range.end()} would inherit that exemption and be
     * excused however many repository calls it made.
     */
    private static boolean callsAnyOf(String body, Set<String> names) {

        if (names.isEmpty()) {
            return false;
        }
        var matcher = CALL_OR_REFERENCE.matcher(body);
        while (matcher.find()) {
            var group = matcher.group(1) != null ? 1 : 2;
            if (names.contains(matcher.group(group)) && qualifierIsThisOrAbsent(body, matcher.start(group))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the name starting at {@code nameStart} is called on nothing at all or on {@code this} - the two shapes
     * that can reach a method of the same class, and the two that are therefore self-invocations - rather than on some
     * other receiver.
     */
    private static boolean qualifierIsThisOrAbsent(String body, int nameStart) {

        var i = lastNonWhitespaceBefore(body, nameStart);
        if (i < 0) {
            return true;
        }
        if (body.charAt(i) == ':' && i > 0 && body.charAt(i - 1) == ':') {
            i = lastNonWhitespaceBefore(body, i - 1);
        } else if (body.charAt(i) == '.') {
            i = lastNonWhitespaceBefore(body, i);
        } else {
            return true;
        }
        return i >= 3 && body.regionMatches(i - 3, "this", 0, 4)
                && (i == 3 || !Character.isJavaIdentifierPart(body.charAt(i - 4)));
    }

    private static int lastNonWhitespaceBefore(String body, int index) {

        var i = index - 1;
        while (i >= 0 && Character.isWhitespace(body.charAt(i))) {
            i--;
        }
        return i;
    }

    /**
     * A method's body: everything after its opening brace, up to and including its matching close. Deliberately not
     * including the signature line, so a method whose file also holds a private overload of the same name cannot match
     * its own declaration and excuse itself as its own callee. Empty for a declaration with no body at all - an
     * abstract or interface method - detected by its {@code ;} arriving before any {@code {}. Without that check the
     * brace balancing would run on past the bodiless declaration and hand it the NEXT member's body.
     */
    private static String bodyOf(String[] lines, int signatureIndex) {

        var extent = String.join("\n", List.of(lines).subList(signatureIndex, methodExtentEnd(lines, signatureIndex)));
        var open = extent.indexOf('{');
        var semicolon = extent.indexOf(';');
        if (open < 0 || (semicolon >= 0 && semicolon < open)) {
            return "";
        }
        return extent.substring(open + 1);
    }

    /**
     * The line index just past this method's closing brace, found by counting braces from the signature line onward -
     * the method's real extent, never a flat lookahead that can run past it into whatever member comes next. Safe over
     * masked text: {@link SourceMasking#mask} blanks every brace that ever appeared inside a comment or a string,
     * character or text-block literal, so every brace counted here is real Java structure. Falls back to end-of-file if
     * the braces never balance, which does not happen for source that compiles.
     */
    private static int methodExtentEnd(String[] lines, int signatureIndex) {

        var depth = 0;
        var sawOpenBrace = false;
        for (var i = signatureIndex; i < lines.length; i++) {
            for (var j = 0; j < lines[i].length(); j++) {
                var c = lines[i].charAt(j);
                if (c == '{') {
                    depth++;
                    sawOpenBrace = true;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (sawOpenBrace && depth <= 0) {
                return i + 1;
            }
        }
        return lines.length;
    }

    /**
     * True when a {@code class} declaration in this file carries {@code @Transactional} in the decoration block
     * directly above it - the same {@link #annotationBlockAbove} a method signature uses, applied to the type instead.
     * Defensive only: no {@code @Service} or {@code @Component} file in {@code src/main} declares it on the type today,
     * so this never fires. See the class javadoc's <em>Class-level {@code @Transactional}</em> paragraph, and that
     * paragraph for why the flat six-line window this replaced was a latent whole-file hole rather than a one-method
     * one.
     */
    private static boolean hasClassLevelTransactional(String[] lines) {

        for (var i = 0; i < lines.length; i++) {
            if (!CLASS_DECLARATION.matcher(lines[i]).find()) {
                continue;
            }
            if (annotationBlockAbove(lines, i).contains("@Transactional")) {
                return true;
            }
        }
        return false;
    }

    static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The scan itself, on synthetic sources, so the shapes that have actually regressed here are held by assertions
     * rather than by whatever {@code src/main} happens to contain on the day. The previous rounds' narrowings took this
     * guard from 39 methods flagged to 0, and later replaced a real helper's body with an empty one, both while every
     * gate stayed green. Every test below is one of those mistakes, or one made while fixing them.
     */
    @Nested
    class Mechanics {

        private static final String FIELD = "\n    private final ThingRepository repository;\n";

        @Test
        void flagsAReadReachingARepositoryThroughASameFilePrivateHelper() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        public String read(String id) {

                            return helper(id);
                        }

                        private String helper(String id) {

                            return repository.findById(id);
                        }
                    }
                    """)).singleElement().asString().contains("read");
        }

        @Test
        void doesNotFlagThatSameReadOnceItIsAnnotated() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        @Transactional(readOnly = true)
                        public String read(String id) {

                            return helper(id);
                        }

                        private String helper(String id) {

                            return repository.findById(id);
                        }
                    }
                    """)).isEmpty();
        }

        /** RULING R9: a body that calls {@code runAs} must never be annotated, so the guard must not ask it to be. */
        @Test
        void exemptsAReadWhoseOwnBodyCallsRunAs() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        public String read(String id) {

                            return OrganizationScope.runAs(id, () -> repository.findById(id));
                        }
                    }
                    """)).isEmpty();
        }

        /**
         * The receiver check. {@code window.end()} names some other object's method, not this file's private
         * {@code end}, so it must not inherit that method's {@code runAs} exemption. Before the check existed, any
         * future method in such a file whose body happened to contain the token {@code end(} was excused however many
         * repository calls it made.
         */
        @Test
        void doesNotLetAQualifiedCallOnAnotherReceiverTriggerTheRunAsExemption() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        private void end(long id) {

                            OrganizationScope.runAs("o", () -> repository.deleteById(id));
                        }

                        public String read(Window window) {

                            return repository.findById(window.end());
                        }
                    }
                    """)).singleElement().asString().contains("read");
        }

        /**
         * A same-file <em>public</em> helper called on {@code this}. Spring's proxy cannot intercept a self-invocation,
         * so {@code loaded}'s own annotation does not fire on this path and {@code read} is the only place one can.
         * This is {@code DefaultAuthorizationGate.hasPermission} in miniature, and it also pins the {@link #ANY_METHOD}
         * bug that once hid a live instance of it: {@code return
         * loaded(id)} must register as a call, never as a declaration overwriting {@code loaded}'s real body. Note that
         * the annotation sits five lines above {@code read}'s signature, close enough that the flat six-line lookback
         * {@link #annotationBlockAbove} replaced would have called this method covered.
         */
        @Test
        void flagsAReadThatSelfInvokesAnAnnotatedPublicHelper() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        @Transactional(readOnly = true)
                        public String loaded(String id) {

                            return repository.findById(id);
                        }

                        public String read(String id) {

                            return loaded(id);
                        }
                    }
                    """)).singleElement().asString().contains("read");
        }

        /**
         * The member boundary itself, with nothing at all between the two methods but their braces: one annotated read,
         * one unannotated read directly below it, both touching the repository themselves. Under the flat six-line
         * lookback the second was silently covered by the first's annotation, which the mesh recorded as a harmless
         * over-flagging gap.
         */
        @Test
        void doesNotLetOneMethodsAnnotationCoverTheNextMethodBelowIt() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        @Transactional(readOnly = true)
                        public String one(String id) {

                            return repository.findById(id);
                        }

                        public String two(String id) {

                            return repository.findById(id);
                        }
                    }
                    """)).singleElement().asString().contains("two");
        }

        /** A call through another bean's receiver is not this file's method, so it must not carry the reach either. */
        @Test
        void doesNotAttributeAnotherBeansMethodToThisFilesHelperOfTheSameName() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        private String loaded(String id) {

                            return repository.findById(id);
                        }

                        public String read(String id) {

                            return other.loaded(id);
                        }
                    }
                    """)).isEmpty();
        }

        /**
         * A ternary continuation line. {@code ? load(id)} parses as "type {@code ?}, name {@code load}", so it
         * registers as a second declaration of {@code load} with an empty body; under {@code put} that empty body
         * replaced the real helper's and {@code read} stopped being flagged. Six live files write this shape, and none
         * hides an offender today only because in each the helper happens to be declared below its caller and so wins
         * the race. Merging removes the race.
         */
        @Test
        void isNotFooledByATernaryContinuationThatLooksLikeADeclaration() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        private String load(String id) {

                            return repository.findById(id);
                        }

                        public String read(String id, boolean flag) {

                            return flag
                                    ? load(id)
                                    : "";
                        }
                    }
                    """)).singleElement().asString().contains("read");
        }

        /**
         * Overloads. Two declarations of one name are one key to this scan, so the body that reads the repository must
         * not be lost to the one that does not. Eight live files carry same-named declarations, among them
         * {@code MembershipService.isMember} and {@code SessionMapper.toSession}.
         */
        @Test
        void doesNotLoseAnOverloadsRepositoryCallToItsSiblingsEmptierBody() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        private String load(String id) {

                            return repository.findById(id);
                        }

                        private String load(String id, int limit) {

                            return id;
                        }

                        public String read(String id) {

                            return load(id);
                        }
                    }
                    """)).singleElement().asString().contains("read");
        }

        /**
         * A bare statement call must not register as a declaration. It is harmless when its extent holds no braces,
         * because the body comes back empty and merging ignores it - but a statement whose call takes a block, here a
         * lambda, hands that block's contents to the name as if it were a method body, and every caller of the real
         * method inherits a repository reach it does not have. That is the over-flagging direction rather than the
         * dangerous one, which is why {@link #ANY_METHOD} requiring its type to start with a non-space is defence in
         * depth rather than the load-bearing fix it was first described as.
         */
        @Test
        void doesNotTreatABareStatementCallAsADeclaration() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        @Transactional(readOnly = true)
                        public String seedFromAStatement(String id) {

                            register(() -> {
                                repository.deleteById(id);
                            });
                            return id;
                        }

                        @Transactional(readOnly = true)
                        public String seedFromAReturn(String id) {

                            return register(() -> {
                                repository.deleteById(id);
                            });
                        }

                        public String read(String id) {

                            register(null);
                            return id;
                        }

                        private String register(Runnable task) {

                            return "";
                        }
                    }
                    """)).isEmpty();
        }

        /**
         * A call wrapped onto a continuation line after a comma is not a declaration. Were it one, {@code account}
         * would be declared twice, a name declared twice never confers the {@code runAs} exemption, and {@code export}
         * would be indicted for the repository read {@code links} makes. The nested type arguments on {@code account}
         * are the other half: the real declaration must still parse, commas and all.
         */
        @Test
        void doesNotTreatAWrappedArgumentAsADeclaration() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        public Map<String, Object> export(String id) {

                            return Map.of(Sample.LINKS, links(id),
                                    Sample.ACCOUNT, account(id));
                        }

                        private List<String> links(String id) {

                            return repository.findAll(id);
                        }

                        private Map<String, List<Map<String, Object>>> account(String id) {

                            return SubjectScope.runAs(id, () -> repository.findById(id));
                        }
                    }
                    """)).isEmpty();
        }

        /**
         * Two hops, the shape a one-hop cap would miss: the fixed point has to run to convergence, not stop at the
         * first frame.
         */
        @Test
        void followsTheReachTwoHopsDeepInBothDirections() {

            var reaching = """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        public String read(String id) {

                            return first(id);
                        }

                        private String first(String id) {

                            return second(id);
                        }

                        private String second(String id) {

                            return %s;
                        }
                    }
                    """;
            assertThat(offendersIn("Sample.java", reaching.formatted("repository.findById(id)"))).singleElement()
                    .asString().contains("read");
            assertThat(offendersIn("Sample.java",
                    reaching.formatted("OrganizationScope.runAs(id, () -> repository.findById(id))"))).isEmpty();
        }

        /**
         * The unspanned-pool carve-out, the second largest behavioural exclusion in this guard. It is keyed to the
         * receiver a file declares as the pool's client, never to the file, so a class holding both that client and a
         * primary-pool repository is still indicted for the repository.
         */
        @Test
        void excludesAnUnspannedPoolClientsCallsButNothingElseInTheSameFile() {

            var declaration = """
                    @Component
                    class Sample {

                        private final JdbcClient jdbc;
                        private final ThingRepository repository;

                        Sample(@Qualifier("tenantDiscoveryJdbcClient") JdbcClient jdbc) {
                            this.jdbc = jdbc;
                        }
                    """;
            assertThat(offendersIn("Sample.java", declaration + """
                        public String read() {

                            return jdbc.sql("SELECT 1").query(String.class).single();
                        }
                    }
                    """)).isEmpty();

            assertThat(offendersIn("Sample.java", declaration + """
                        public String alsoReadsThePrimaryPool(String id) {

                            return repository.findById(id);
                        }
                    }
                    """)).singleElement().asString().contains("alsoReadsThePrimaryPool");
        }

        /**
         * A nested type must not turn a neighbouring METHOD's annotation into a whole-file exemption.
         * {@link #CLASS_DECLARATION} matches a nested {@code final class} as readily as the top-level one, so under the
         * flat six-line window {@link #hasClassLevelTransactional} used, {@code one}'s annotation sitting six lines
         * above {@code Holder} returned true and covered every method in the file, {@code two} included. No class-level
         * annotation was needed. The tree is only free of it because no {@code @Service} or {@code @Component} file
         * currently nests a second class, and {@code ValidMeetingUrl} and {@code ValidTimeZone} already write the shape
         * a nested validator away.
         */
        @Test
        void doesNotLetANestedClassTurnAMethodAnnotationIntoAWholeFileExemption() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        @Transactional(readOnly = true)
                        public String one(String id) {

                            return repository.findById(id);
                        }

                        final class Holder {
                        }

                        public String two(String id) {

                            return repository.findById(id);
                        }
                    }
                    """)).singleElement().asString().contains("two");
        }

        /**
         * A repository held in a field named for the rows it holds. {@link #REPOSITORY_CALL} matches a receiver by
         * name, so {@code rates.findBySubject(...)} was invisible to it however plainly the field's TYPE said
         * repository - thirty-one fields across twenty-two files, seven of them on live read paths, among them
         * {@code ReactionService.tally}. The second assertion is why {@link #receiverDeclaration} insists on
         * {@code ; , ) =} after the name: a method <em>returning</em> a repository must not register the method's own
         * name as a receiver, or every file with an accessor like this would indict every method that calls it.
         */
        @Test
        void resolvesAReceiverByItsDeclaredTypeNotOnlyByItsName() {

            assertThat(offendersIn("Sample.java", """
                    @Component
                    class Sample {

                        private final QuoteRepository rates;

                        public long amountFor(String subject) {

                            return rates.findBySubject(subject).orElse(0L);
                        }
                    }
                    """)).singleElement().asString().contains("amountFor");

            assertThat(offendersIn("Sample.java", """
                    @Component
                    class Sample {

                        public QuoteRepository rates() {

                            return null;
                        }

                        public long amountFor(String subject) {

                            return rates().hashCode();
                        }
                    }
                    """)).isEmpty();
        }

        /**
         * The one direction the merge in {@link #methodBodies} really can get wrong, and which this file's javadoc used
         * to call impossible ("it can ask for an annotation a method does not need, and cannot excuse one that does").
         * {@link #methodsReaching} is used twice, so attributing a name's calls too widely helps the indictment and
         * HURTS the exemption: here only the void {@code load} calls {@code runAs}, but the merged body puts the name
         * in the {@code runAs} reach, and {@code read} - which reads the repository in its own body - was excused by
         * calling the other overload. {@link #namesDeclaredMoreThanOnce} is what stops it.
         */
        @Test
        void doesNotLetAnOverloadedNameCarryTheRunAsExemptionToACallerThatReadsForItself() {

            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {
                    """ + FIELD + """
                        private void load(String id) {

                            OrganizationScope.runAs(id, () -> repository.deleteById(id));
                        }

                        private String load(String id, int limit) {

                            return id;
                        }

                        public String read(String id) {

                            repository.findById(id);
                            return load(id, 5);
                        }
                    }
                    """)).singleElement().asString().contains("read");
        }

        @Test
        void flagsAReadThroughAPersistencePortHeldUnderAnyName() {

            var port = knownPersistencePorts().iterator().next();
            assertThat(offendersIn("Sample.java", """
                    @Service
                    class Sample {

                        private final %s saves;

                        public Object read(String id) {

                            return saves.find(id);
                        }
                    }
                    """.formatted(port))).singleElement().asString().contains("read");
        }

        @Test
        void readsEveryImplementedTypeWhateverItsTypeArguments() {

            assertThat(implementedTypes("""
                    final class Sample implements dev.axonia.x.Store<? extends Map<String, List<Long[]>>>, Other[],
                            Third<Map<String, Map<String, ?>>> {
                    }
                    record Row(long id) implements Readable {
                    }
                    """)).containsExactlyInAnyOrder("Store", "Other", "Third", "Readable");
        }

        /** The file filter: a {@code @Component} is scanned exactly as a {@code @Service} is, a plain class is not. */
        @Test
        void scansComponentsAsWellAsServicesAndSkipsAnUnannotatedClass() {

            var body = """
                    class Sample {
                    """ + FIELD + """
                        public String read(String id) {

                            return repository.findById(id);
                        }
                    }
                    """;
            assertThat(offendersIn("Sample.java", "@Component\n" + body)).singleElement().asString().contains("read");
            assertThat(offendersIn("Sample.java", body)).isEmpty();
        }
    }
}
