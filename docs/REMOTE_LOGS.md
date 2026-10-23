# Service logs

Open the selected service dashboard and choose **Service logs**. Local services retain the
existing managed-file viewer. For a remote service the button becomes available after its
capabilities advertise service administration permission. The server independently enforces
authentication and administration on every request; the UI does not grant access.

Remote viewing uses the selected service client's existing authenticated transport. The
capability probe and page requests run off the JavaFX thread. Only the supported active
`service` stream with `vetted-diagnostics-v1` is displayed. Older services, missing capability
and denied access show a safe retry message. The same pane is used in the service detail
**Logs** tab; this is separate from the published-project application runner.

Remote logs contain vetted diagnostic families, not every raw log message. The viewer retains
up to 5,000 rows and updates unfinished exception events without duplication. HTTP failures
show a safe numeric status without provider messages or credentials. A 410 means the cursor
expired or history changed (for example restart, rotation or truncation). Polling stops after
errors; **Reload logs / reset gap** explicitly starts a fresh active-file query. It cannot
recover rotated-away history and never silently crosses a known gap.

Polling stops when the pane detaches, its tab is deselected, an ancestor becomes invisible,
or its window hides. Reopening the view starts a new capability check and active-file query.
Late replies cannot populate a closed or replaced view. Local file paths and behavior are
preserved. No credentials or remote payloads are written to a new store.

The service HTTP endpoint must already be running. Container/startup failures before HTTP,
unknown diagnostic families and expired archives still require infrastructure logs.

## Verification scope

The production dashboard button and shared detail-pane factory are tested with a controlled
service client, including administration denial, unavailable/unsupported capability, gaps,
late replies, visibility and window/tab lifecycle, and local files. An isolated synthetic
window rendering is captured as test evidence. This does not establish deployed endpoint,
real Hub JWT, installed-distribution or live remote-service acceptance. Existing documented
repository baseline test failures remain separate from these tests.


## Local update behavior

Local reading runs off the FX thread; displayed entry/property updates run on FX. Complete
appended lines and UTF-8 fragments are reconciled without duplicate rows; an unfinished line
is retained until its newline arrives. Stack continuations update the preceding entry.
The existing PID-bearing format, the current shared service file format and its console
fallback are recognized. The initial watch is registered before reading, with periodic retry
for missed notifications and files or directories created later.

File identity, retained boundary samples and size/time checks detect ordinary rotation,
replacement and truncation/regrowth. A new file generation replaces the displayed old rows.
This is an operational viewer, not a tamper-proof journal; it does not compare every historical
byte on each poll. Existing local history retention is unchanged. Column selection and the
current auto-scroll preference survive updates. No text/level row-filter feature is added.
Switching files/sources and shutting down invalidate already queued updates from the old source.
