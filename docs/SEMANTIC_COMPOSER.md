# Semantic composer interaction and implementation stages

## Implemented interaction

The compact composer uses a 12px base font and a source chooser in place of its title.
The concepts/operators table appears above a single compound input box. The box contains
the server-confirmed, semantically colored expression followed immediately by the text
field for the next token. Both share one background and border. Long expressions scroll
horizontally to keep the input reachable. The confirmed prefix is changed through semantic
operations, rather than ordinary character editing.
Both entry points use a borderless modal overlay covering the owner's scene, with a
translucent backdrop and the composer centered. The panel grows to fit the observable card;
on smaller windows its contents scroll rather than clipping. It follows owner moves/resizes.

| Action | Result |
| --- | --- |
| Type in the input | Search after 350 ms without another keystroke. Concept searches require at least two characters; single-character symbol operators and requested literal values remain usable. |
| Up or Down in the input | Move the highlighted result up/down and scroll it into view, keeping input focus and the cursor position. Selection stops at the first/last result. |
| Return on a table row, or double-click | Add that concept/operator; clear the pending query and return focus to the input at position zero. |
| Return in the input | Accept a requested literal value, or add the highlighted match when the cursor is at the end of a nonblank search with no selected text. An empty query or a cursor inside the search does not add a component. |
| Backspace at the input boundary | Undo one server-confirmed composition step. Ordinary Backspace elsewhere edits the pending query. Holding the key does not repeat semantic undo. |
| `(` or `)` | In concept mode, open/close a scope when the Reasoner permits it; preserve characters as text when entering literal values or searching authorities. Holding the key does not repeat scope edits. |
| Ctrl+Enter in the input | Submit the confirmed expression to the host, including identities and other predicates. Disabled while any nonblank query text remains. Ctrl+Enter on the table is ignored. |
| Arrow icon at the right of the input | Same action as Ctrl+Enter. Requires nonempty confirmed content, no pending query, and an acknowledged session with no request running; an OBSERVABLE concept is not required. |
| Copy icon beside the arrow | Copy the exact confirmed declaration as plain text to the system clipboard, under the same availability condition as submission. |
| Escape / dialog window close | Dismiss without accepting. |

The old Undo, parentheses, Add selected, Add value, Start over, Cancel and textual Continue
buttons are removed. Routine instructional/validation-success messages are removed;
errors and request progress remain visible. The observable documentation card is retained.
The Authorities entry exposes the searchable worldview-local binding IDs beside the source chooser.
Holding Enter across selection and focus changes never repeats selection or submits.
Editor callbacks retain the Observable return type for compatibility: a confirmed predicate or
other declaration without a completed observable is returned with its exact declaration text
and the current confirmed concept (or an unresolved semantic marker when no concept exists).
Runtime hosts perform their own validation before executing an observation. Suggestion warnings
do not block export of a confirmed expression; uncertain edits still require recovery.

## Waiting, errors, and recovery

The input's Continue icon is replaced by a spinner while a request runs. A short status
identifies the operation (`Adding …`, `Undoing…`, `Searching…`, or `Recovering expression…`).
Confirmed tokens remain visible until the Reasoner acknowledges the change. Typing the
next query remains possible during an insertion; its reply must still update the expression,
then only the newest query is searched. Edits within a session remain serialized, and stale
query proposals are never selectable. A new keystroke abandons an obsolete TOKEN worker
immediately and debounces the newest text on a fresh worker, retaining the confirmed
expression and known session. An initialization whose session ID is still unknown may
start a fresh session; any late returned unused session is cancelled.

Backspace at the input boundary replaces a pending TOKEN search with undo immediately.
It queues undo while an edit is running, rather than interrupting an uncertain edit.

Transport failures preserve the last confirmed expression, card, and session ID. The input
icon becomes Retry search and obtains the server's actual state without resending the edit.
Backspace can also undo a possibly committed edit, including a first edit for which no
response arrived. Continue and Ctrl+Enter cannot submit uncertain state. Rejected literals
keep their diagnostic and pending text so the user can correct them.

Semantic requests have a 30-second client deadline (the underlying HTTP client's default
is 300 seconds). A timeout interrupts and retires that worker; later work uses a fresh one.
An existing session is retained after a TOKEN timeout because searching does not edit the
expression; the input icon retries in the same context, and Backspace can undo. A timeout
during an edit or initialization abandons the session because an outstanding edit may
still finish server-side. Those timeouts and expired sessions retain the displayed snapshot,
but require an explicit restart with the input icon or Backspace at the input boundary.
Restart starts an empty expression and preserves pending text. Late replies cannot replace
either a newer query or a restarted session.
There is no automatic edit retry or speculative insertion, which could duplicate an edit.

Key releases are tracked at scene level and held-key guards reset when the window loses
focus, so a release outside the composer cannot permanently suppress subsequent actions.

### Reasoner review and fixes

`klab-services/klab.core.services/.../indexing/SemanticSearchSession.java` previously committed
an edit before generating proposals, but exceptions from the index, individual concept
lookups, or clause documentation could escape without returning the committed snapshot.
The corrected session returns the expression and undo state with explicit diagnostics,
isolates failing candidates, and retains other usable suggestions. Out-of-order responses
also tolerate documentation failures.

TOKEN searches no longer replay the unchanged accepted expression. The `each` candidate
is tested only at legal positions, avoiding needless replay after a completed operand.
All candidate compatibility checks remain in place. A proposal now retains its validated
token and resulting state; SELECT reuses that state only after checking the proposal
response ID. Undo and other edits still replay their resulting expression.

The indexer previously ignored its requested candidate limit and searched only the first
1,000 hits before semantic filtering. It now pages through hits until the requested number
of eligible candidates is found or the index is exhausted. The session supplies an
expression-validation filter that runs before the candidate limit, so incompatible heads
after a predicate cannot crowd out later eligible qualities. Those validated states are
reused in the proposal response, avoiding a second validation pass.

Live thread snapshots were idle when inspected; they did not capture a blocked request.
The reported improvement after repeated attempts is consistent with declaration-cache
warming, but does not establish the cause of every intermittent timeout. Client FINE logs
record request/response IDs, mode, selection, query length, client/server elapsed time,
match counts and error counts. Failures and timeouts log at WARNING, including whether
the worker had already finished. Query text is not logged.

The session registers incoming request IDs before taking its state lock. A newer request
cooperatively cancels an obsolete TOKEN scan between index hits and candidate validations;
superseded queries cannot publish proposals. SELECT, UNDO and other edits remain atomic.
An individual Reasoner/Resources call already underway must finish before the next
cancellation checkpoint; native ontology calls are not forcibly interrupted.
The HTTP POST helper preserves deliberate interruption without reporting a service error
to the user scope. Other transport failures retain their existing reporting behavior.

An isolated live replay found Normalized in 175 ms, selected it in 504 ms, and searched `s`
in 1,120 ms with Slope among the results. These were warm-cache observations after the
reported stall. The original blocked request was not captured, so they do not establish
its precise cause. A one-character query examines many concepts, regardless of whether
the intended Elevation and Slope concepts are siblings.

A client cache could display provisional results keyed by worldview revision, confirmed
expression and query. Cached rows must be refreshed before selection because proposal
IDs and sessions belong to the server; cached results cannot safely authorize an edit.

Four added server regressions fail against the original session and pass after the fix.
The focused suites pass 30 composer tests, 32 Reasoner session tests, five actual-Lucene
indexer tests and four HTTP tests. They cover minimum length, rapid typing, replacement of
a hung query, Backspace recovery, preservation of edits during cancellation, Normalized
followed by Elevation beyond invalid head candidates, and quiet transport interruption.
The new scan-cancellation and transport-interruption regressions fail against the prior
implementation and pass with these changes.
The service source
changes are in the neighboring `klab-services` repository; its running Reasoner must be
rebuilt/restarted to use them. Authority discovery/search is described in the stages below.

## Stage 1: synchronized worldview authority bindings

Implementation is in the sibling `klab-services` repository. `Worldview.getAuthorityBindings()`
now carries the local ID, identity root, declaration ontology/offset/length and source hash,
selected authority descriptor, exact containing component/version, and selected Reasoner URL.
`NavigableWorldview` delegates this metadata. Resources builds a fresh binding snapshot on
worldview retrieval; client collection and updates replace it, including removed bindings.

Resources traverses nested declarations, validates the configuration envelope and identity
root, and diagnoses missing/ambiguous descriptors and duplicate local IDs. An explicit
`provider@version` selects that exact descriptor version; otherwise exactly one matching
descriptor is required. Validation errors invalidate the worldview. Component visibility
uses the requesting scope's existing usage rights. Reasoner lookup is pinned to the selected
component and descriptor, including after incremental ontology updates.

Reasoner capabilities advertise only successfully configured bindings for the loaded source
revision. Client synchronization chooses among matching hosts, preferring a local Reasoner
over a remote one and storing its URL in `reasonerUrl`. Host matching includes the declaration
ontology's source hash so an older configuration cannot win. Resources leaves this field null:
locality is relative to the client. A null URL also means no matching addressable host has been
discovered; host discovery can be refreshed with `WorldviewImpl.refreshAuthorityHosts`.

Discovery records contain neither parameter values nor provider-held configuration IDs.
The Reasoner still receives configuration parameters through the existing authorized ontology
transport. Provider-specific parameter validation remains in `Authority.configure`, since
the descriptor has no parameter schema. No new credential transport is introduced.
Advertised sub-authorities remain descriptor metadata, not additional bindings or inferred
filter aliases. The IDE chooser filters bindings by `provider.searchable()` and uses the
selected Reasoner URL; the search and semantic insertion contracts are described in Stage 2.

Verification: 21 focused authority tests pass across the common, core-services, Reasoner,
and Resources modules. The affected reactor, including Modeler, builds successfully in an
isolated source snapshot. Coverage includes transport round trips and legacy payloads,
nested declarations, invalid/ambiguous providers, duplicate names, removals and failed-fetch
recovery, exact component pinning, local-host preference, remote fallback, and stale revisions.

### Original requirements

Discovery belongs to the `Worldview` synchronized from Resources. During validation,
Resources must collect `requires authority` declarations, including nested concept
statements, and match each declaration's provider URN to its `Extensions.AuthorityDescriptor`.

Proposed binding data (field names to finalize in the API repository):

- Worldview-local authority ID used in expressions, such as `TAXA`.
- Root identity concept URN and source declaration location.
- Provider URN, selected descriptor/version, and containing component identity.
- Validated configuration parameters needed by the Reasoner to establish the bridge.
- Searchability from the matched descriptor, and advertised sub-authorities.

The bindings should be serialized by `WorldviewImpl`, included in Resources worldview
responses, and replaced/reconciled on worldview updates. They describe configured
worldview bindings, rather than every installed provider. Provider-held configuration IDs
remain internal to the Reasoner. The UI lists the binding's local ID, never its provider URN.
Parameter transport must follow existing authorization rules, with credential values kept
out of client-facing discovery metadata.

Validation must diagnose missing/ambiguous provider descriptors, invalid root identities,
and conflicting local names. The chooser includes only validated searchable bindings.
Reasoner bridge initialization must use the same selected provider/version and binding.
Runtime search failures are reported separately from validation or an empty result set.

The implementation rejects multiple matching descriptors and invalidates the worldview
when a required provider descriptor is unavailable. Sub-authority filter alias representation
remains a Stage 2 decision. An advertised sub-authority must
not be inferred to be an independent binding: only providers declaring search-filter
semantics can share their parent's codes/configuration.

## Stage 2: Reasoner search and insertion contract

Implemented in `klab-services`. `Reasoner.searchAuthority(AuthoritySearchRequest, Scope)`
and its authenticated HTTP client/controller accept the local binding ID, query, optional
advertised filter, offset and limit. Query length is 1–256; offset is 0–10,000 and limit
1–100. `AuthoritySearchResponse` distinguishes OK (including an empty list), UNSUPPORTED,
UNAVAILABLE and FAILED, and carries canonical `AuthorityIdentity` candidates, notifications,
total and nextOffset. Pagination describes the provider-returned list, not its entire catalog.
Provider configuration IDs remain server-side.

`SemanticSearchRequest.Mode.IDENTITY` carries authority, identityCode and the current semantic
response revision in matchesRequestId. The scoped semantic session resolves the exact canonical
identity, checks consistency and compatibility, then inserts one undoable step. Stale revisions,
failed resolution, incompatible identities and error notifications preserve the expression.
`AuthorityIdentitySyntax` serializes the `AUTHORITY:code` token, including necessary escaping.
Ordinary SELECT remains restricted to semantic proposal IDs.

`getAuthorityDocumentation` supplies media-type URL metadata. The HTTP controller publishes
provider-local files through an authenticated content endpoint; upstream web URLs remain intact.
Search and scoped semantic insertion are available through ReasonerClient. Existing backend tests
include identity insertion/undo through HTTP, stale revisions, failures and documentation transport.

## Stage 3: enable authority mode in the IDE

The initial implementation enables the source chooser's Authorities entry. It reveals
a combo box immediately to its right containing the synchronized searchable binding IDs.
Keep the compound input and confirmed expression unchanged while switching modes.
Reuse the input's pending text as the selected authority's search query.

Replace the concepts table with a split pane: matching identities in a table on the left,
documentation on the right. Single-click/keyboard row selection loads documentation without
inserting. Return/double-click inserts the selected identity through the semantic session,
clears the query, and returns the cursor to the input boundary. Continue and Ctrl+Enter
always accept only the confirmed expression with no pending query, regardless of search mode; Ctrl+Enter
is restricted to the input.

Use `getAuthorityDocumentation` for selected-identity documentation, rendering Markdown when
available and falling back to the candidate's description. Define how other advertised
media (HTML, images, PDF) should be presented. Load documentation asynchronously with
independent selection revisions; stale results must not overwrite a newer row or authority.
Debounce searches, reject stale results, and retain the same semantic session while changing
providers. Empty authority lists and search failures must have explicit empty/error states.

### Initial implementation and remaining decisions

Both composer entry points discover synchronized worldview bindings asynchronously. Searchable
local IDs populate the adjacent chooser; advertised sub-authorities are not inferred to be bindings.
Discovery refreshes host metadata when the scope supplies a WorldviewImpl. Searches use the
binding's selected Reasoner URL and existing scoped service client, with a two-character minimum,
350 ms debounce and 30-second deadline. New queries and provider/mode changes interrupt obsolete
workers and reject late replies. The expression and pending text survive mode changes.

The split pane shows identity labels/canonical codes and documentation. Arrow keys from the input
scan rows; row selection loads documentation without inserting. Enter and primary double-click send
IDENTITY on the existing semantic Reasoner/session with its latest response revision. Successful
insertion clears the pending query and restores focus; failed insertion preserves it. Undo uses the
same semantic operation as concept selection. Searches do not change the semantic response revision.
An authority hosted elsewhere may be browsed, but the session's Reasoner must also have that binding
configured to insert it; the IDE does not transfer or replay sessions between hosts.

Documentation has independent selection revisions and a 30-second deadline. Candidate descriptions
render immediately as Markdown. Advertised text/markdown is fetched asynchronously and rendered with
the existing Flexmark/BBCode renderer, bounded to 1 MiB. Credentials are sent only to the selected
Reasoner's exact authenticated documentation content endpoint and never forwarded on redirects.
Other HTTP(S) media are shown as links. Empty lists, unavailable hosts, failed searches and failed
documentation are explicit; documentation failures do not block insertion.

Further decisions:

- The first page contains at most 100 results and reports truncation. Add paging or infinite scrolling
  if needed, accounting for provider ordering changing between searches.
- Add explicit advertised search-filter selection when providers require it. The initial chooser
  searches the whole binding with no inferred filter aliases.
- Choose embedded viewers for HTML, images and PDFs. Links to authenticated content currently require
  browser authentication; a future authenticated media reader should share the service client's
  transport rather than expose credentials in URLs. Redirected Markdown falls back to the description.
- Discovery is a snapshot for each opened composer. Live worldview/provider changes need refresh
  behavior that preserves an active semantic session and diagnoses binding revision changes.

Identity replies must contain the unchanged confirmed prefix plus the exact canonically encoded
authority token before the pending query is cleared. An unchanged success-looking reply or an
unexpectedly truncated snapshot preserves both query and prefix and requires session recovery;
it logs token counts without exposing the code. Normal rejection diagnostics remain visible.
This also preserves a committed identity returned with suggestion/documentation warnings.

A live isolated replay of the reported Fagus selection found `Fagus sylvatica L.` as
`TAXA:[3DSK5]` and returned that styled token without errors. The sanitized real response is now
a client rendering regression fixture. The original disappearance was not reproduced live;
the missing-token and truncated-response clearing cases are reproduced by focused tests.

Verification: all 40 focused composer tests pass, and `mvn -o -DskipTests compile` succeeds on the
production module path. Focused IDE regressions cover canonical identity insertion in the existing session, undo, input arrows
and Enter, obsolete search replies, stale documentation, searchable-ID filtering, provider failures
and rejected insertion, plus bounded Markdown transport without credentials on public URLs.
Backend HTTP insertion/undo coverage remains in the Stage 2 suites; it was reviewed but not rerun
as part of the IDE implementation.

## Verification

Exercise compound token coloring and spacing, repeated Backspace, ordinary text editing,
scope and literal handling, focus restoration after Return, Ctrl+Enter from the input,
and ignored Ctrl+Enter on the table (including disabled Continue and unconfirmed query text). Authority stages
also require worldview serialization/update tests, declaration-to-descriptor validation,
provider search failures, stale search/documentation responses, incompatible identity
insertion, and an identity insertion/undo round trip through the Reasoner HTTP client.
