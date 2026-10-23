# Local project applications

With a local runtime connected, use **Edit and run application locally** on a project
application in the workspace tree. This opens the usual managed source mirror. For an
application declaring platform `WEB` or `ANY`, its Run button opens an **Application** tab
containing a JavaFX WebView and Run, Stop, and Reconnect controls.

The **Open in** choice selects **Internal WebView**, the default browser, or a detected
installed browser. **Choose browser…** lets you locate another browser executable or a
nonstandard installation. The choice locks for the duration of a run; Stop enables it
again so the next run can target a different browser.

External browsers use the same portable renderer and runtime instance through a temporary
loopback connection hosted by the IDE. Service credentials stay in Java. Reconnect can
reopen the chosen browser, and browser reload resumes the current view. Closing a browser
window leaves the run active until Stop or the application/editor tab closes. Stopping
also closes its loopback connection. This is useful for comparing browser engines; the
renderer limitations below also apply to external destinations.

The runtime's UI instance API launches **published project applications** by URN. Publish
source changes from the behavior editor before running. Opening or reconnecting the view
does not publish source. Desktop/mobile applications keep their source editor but cannot
use this web runner. For a standalone file, publish it to a project and open the published
application from the workspace tree first.

The IDE launches one instance per application tab through `POST /api/v1/ui/instances`.
It uses a single-use ticket and the runtime's `/ui-bus` WebSocket to attach to that
instance. Reconnect replaces the attachment; it does not create another application.
Stop uses the instance DELETE endpoint. Closing the application tab or behavior editor
also requests termination, including when a launch is still in flight. Launch retries
retain their idempotency key until the instance is stopped. Credentials remain in Java;
the WebView receives snapshots and can send widget input only.

The embedded renderer currently supports the portable core controls (text, buttons,
checkboxes, inputs, numbers, selects and progress), container surfaces, layout properties,
and project stylesheets. It reports unsupported extended widgets and custom toolkits
explicitly. It does not embed the full Quasar dashboard, support file transfer, or attach
an agent debugger. The connection console records launch and transport status; the UI
instance response currently exposes no agent handle for the ordinary agent console.

Validation uses `ApplicationUiSessionTest` for launch/close races, idempotent retries,
failed-stop retries, and runtime context paths. A live authenticated runtime is required
for end-to-end application launch testing.
`ApplicationBrowserBridgeTest` checks transport authorization, snapshot replay after
reload, acknowledged input sequences, and forwarding widget input to the existing session.
