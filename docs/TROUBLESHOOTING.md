# Troubleshooting

[Documentation home](../README.md) · [Services](SERVICES.md)

Start with the selected identity, service, and focal digital twin. Many unavailable
actions reflect missing permission or a service capability rather than a client error.

| Symptom | What to check or do |
| --- | --- |
| First launch shows an anonymous or incorrect identity | Register/sign in at the Hub, download the intended account's certificate, and follow [certificate setup](GETTING_STARTED.md#set-up-your-identity-with-a-user-certificate) |
| The certificate drop is rejected | Drop exactly one readable file whose name starts with `klab.cert`; folders and multiple files are not accepted |
| Certificate replacement asks you to stop services | Stop all local services and auxiliary processes and wait for start/stop operations to finish before dropping again |
| Certificate authentication is already in progress | Wait for the current installation/authentication attempt to finish before retrying |
| Certificate installed, but authentication did not succeed | Check the Hub connection and certificate, then retry; confirm the resulting username before starting services |
| Certificate installation failed | Read the drop area's error, check that the file is a valid user certificate downloaded from the Hub, and retry after resolving the reported problem |
| No workspaces or resources appear | Check your profile and Resources service, then access to the workspace/project/resource |
| Local services do not start | Check distribution management and the power state; failures before HTTP need distribution/infrastructure logs |
| Worldview stays empty | Connect a Reasoner with your certificate's worldview loaded and refresh Worldview |
| A source editor has no language assistance | Check its language-support status; source remains editable while support loads or is unavailable |
| Project settings are read-only | Check write access and lock ownership; only administrators can change a worldview declaration |
| Workspace settings are read-only | Workspace ownership or service administration is required; older ownerless records need an administrator |
| Semantic validation is pending/unavailable | Check the selected Reasoner and source/import synchronization; inspect status and document markers, then use Validate now if needed |
| An observation request lacks geometry | Check context for a quality, or the selected observer's perceived extent for a substantial/collective |
| A request goes to the wrong context | Check the focal-twin selector, current context observation, and observer before resubmitting |
| A map or point value is unavailable | Inspect observation geometry/type and runtime export support; not every observation has mapped content |
| Quality export produced no file | The visible Export to filesystem command is not implemented; do not assume a backup exists |
| Run or Debug is disabled | Save valid source, review compilation errors, and connect a local Runtime |
| An application runs old source | Synchronize a changed managed mirror to its project, then Stop and Run saved application |
| An application loses its browser view | Keep the IDE running and use Reconnect; this retains the current instance |
| A managed copy differs from the project | Reopen it and review Use project source / Keep local copy; using project source keeps a .bak of local edits |
| No Start workflow action appears | Check applicable definitions, permissions, and configured workflow support; existing flows may still be browsable |
| A workflow operation reports a conflict | Retain the draft, refresh saved state, and reconcile before retrying |
| Workflow creation/deletion has an uncertain result | Check the saved flow/list before another request; repeated actions are blocked until reconciled |
| Review acceptance is disabled | Inspect all required server checks, unresolved semantics, and human rationale; blocked checks need service-side resolution |
| Remote logs stop updating | Check the numeric status; Reload logs / reset gap starts a fresh active-file query after a gap |

## Gather useful evidence

Record the IDE build, operating system, relevant service identities and versions,
the action you took, expected result, actual result, and approximate time. Include
editor diagnostics, activity outcome, or a small relevant log excerpt. A screenshot
should show the selected service/twin and status when they matter. Remove credentials
and sensitive data before sharing.

[Report an IDE issue](https://github.com/integratedmodelling/klab-ide/issues) for
client failures. If the evidence points to a service, involve its administrator or
[report a service issue](https://github.com/integratedmodelling/klab-services/issues).

The IDE remains under active development. Keep important source and project material
in version control, save drafts before closing editors, and inspect destructive
operation confirmations carefully.
