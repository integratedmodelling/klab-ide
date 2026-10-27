# Local project applications

With a local runtime connected, use **Edit and run application locally** on a project
application in the workspace tree. This opens the usual managed source mirror. For an
application declaring platform `WEB` or `ANY`, its Run button opens an **Application** tab
containing a JavaFX WebView and Run, Stop, and Reconnect controls.

Use the **browser icon menu beside Debug/Run in the source editor** to choose **Internal
WebView**, the default browser, or a detected installed browser before running.
**Choose browser…** lets you locate another browser executable or a nonstandard
installation. The running application's **Open in** choice shares this setting. Changing
the destination during a run reconnects the new renderer to the same application instance
and retained state; it does not launch another application.

External browsers use the same portable renderer and runtime instance through a temporary
loopback connection hosted by the IDE. Service credentials stay in Java. Reconnect can
reopen the chosen browser, and browser reload resumes the current view. Closing a browser
window leaves the run active until Stop or the application/editor tab closes. Stopping
also closes its loopback connection. This is useful for comparing browser engines; the
same Quasar widget catalog is used in both destinations. The IDE accepts only a
local runtime with a local URL; it cannot launch applications on remote services.

Local UI attachment tickets retain the original HTTP request's verified local authentication
context only on the runtime server. This requires a loopback request authenticated as local.
Attachment and heartbeat checks revalidate that context, expiry, identity and project access.
Server credentials are never returned to either renderer. Remote browser sessions retain
their existing credential-based authentication path.

The runtime's UI instance API launches **saved project applications** by URN. Saves in the
workspace editor update that project source directly. Changes made in a separate local
behavior mirror still need its Publish action. Reopening a managed copy reconciles newer
project source unless it would overwrite local changes; project change notifications also
refresh clean open copies. Existing runtime instances retain the source they started with:
use **Stop**, then **Run saved application** after a save. **Reconnect** keeps the same instance.
Opening or reconnecting the view does not publish source. Desktop/mobile applications keep their source editor but cannot
use this web runner. For a standalone file, publish it to a project and open the published
application from the workspace tree first.

Local editing resolves the selected behavior type from its project catalog. An application
and an older behavior can declare the same URN; they must not share a global lookup or a
managed mirror. Mirrors retain the behavior type, and clean older mirrors are migrated
when reopened. The runtime launcher also resolves the project's application when a
same-URN behavior shadows it in the global index.

The same synchronization applies to test cases, scripts, and other behavior types.
Opening a managed copy from recents checks its owning project's catalog again. Mirrors
persist the service's last-update timestamp so delayed older revisions cannot replace
newer source; equal timestamps still compare source contents. Local edits remain protected.
When automatic compilation is enabled, refreshed source is compiled again so Run and
Debug use the updated behavior.
If both copies differ from the last synchronized source, reopening offers **Use project
source** or **Keep local copy**. Using project source preserves the local file as a `.bak`
beside the mirror before loading the Resources version. Removing a recent-file card only
removes it from the list; it does not delete or reset the managed mirror.

The IDE launches one instance per application tab through `POST /api/v1/ui/instances`.
It uses a single-use ticket and the runtime's `/ui-bus` WebSocket to attach to that
instance. Reconnect replaces the attachment; it does not create another application.
Stop uses the instance DELETE endpoint. Closing the application tab or behavior editor
also requests termination, including when a launch is still in flight. Launch retries
retain their idempotency key until the instance is stopped. Credentials remain in Java;
the WebView receives snapshots and can send widget input only.

The embedded renderer now uses the same Quasar scaffold and extended widgets as the runtime
browser interface. It includes forms, choices, sliders/ranges, dates/times/colors, collections,
navigation, Markdown/images/icons, media, and upload/download. The previous message
`Widget "..." requires the runtime web interface` came from a separate primitive-only HTML
renderer and is no longer the fallback for these widgets.

Build the shared browser assets in `klab-services`, then rebuild the IDE:

```powershell
# From the klab-services checkout:
mvn -pl klab.core.services -am -Pwebui "-DskipTests" install
mvn -f ../klab-ide/pom.xml "-DskipTests" package
```

Restart the IDE and Stop/Run the application after rebuilding. `-Pwebui` creates the
`klab.core.services:application-ui` asset-only classifier used by the IDE at runtime; it
imports no service implementation dependencies. There are no per-widget feature flags.
The page loads an offline, precompiled script and CSS rather than a CDN or ES modules.
Missing assets produce an explicit `Missing packaged Quasar renderer` build diagnostic.
For modular IDE launches, assets may come from the asset-only JAR on the runtime classpath;
they need not remain unpacked in `target/classes`. The loader checks both the IDE module
and its classloader, so an IDE-only rebuild does not hide an already-installed renderer.

Conveyor distributes the renderer inside `target/klab-ide-all-1.0.0-SNAPSHOT.jar`, its
existing application input; no external Node.js installation or asset directory is needed.
Every Maven `package` checks that this shaded JAR contains the HTML, renderer JavaScript
and CSS, including builds with `-DskipTests`. The check runs before Conveyor is invoked
by `-Pconveyor`, so Jenkins' existing `clean package` pipeline also enforces it. The services
Jenkins pipeline already uses `-Pwebui` for install/deploy, publishing the
`klab.core.services:application-ui` classifier alongside the matching service artifacts
so clean CI agents can resolve the IDE's runtime dependency.

File operations use the Java-owned session to reach the same bounded and authorized runtime
file endpoints; service credentials stay in Java. The internal view saves through a native
Save dialog, while external browsers use normal downloads. Custom installed toolkits still
require their runtime web renderer, and media codecs depend on the browser engine.
The runner does not attach an agent debugger. Its connection console records launch and
transport status; the instance response exposes no agent handle for the ordinary agent console.

`ApplicationRendererFxTest` exercises the packaged Quasar bundle in an actual JavaFX WebView.
Validation also uses `ApplicationUiSessionTest` for launch/close races, idempotent retries,
failed-stop retries, and runtime context paths. A live authenticated runtime is required
for end-to-end application launch testing.
`ApplicationBrowserBridgeTest` checks transport authorization, snapshot replay after
reload, acknowledged input sequences, and forwarding widget input to the existing session.
