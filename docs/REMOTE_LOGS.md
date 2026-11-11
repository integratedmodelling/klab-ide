# Reading service logs

[Documentation home](../README.md) · [Services](SERVICES.md)

Open a selected service dashboard and choose **Service logs**, or open the service
details' **Logs** tab. Check the service identity so you read the right instance.

## Local services

The local viewer follows the managed service log file. Complete appended lines
appear automatically; stack traces stay attached to their log entry. An unfinished
line appears after its newline arrives. Rotation, replacement, or truncation starts
a new displayed file generation.

Column choices and your auto-scroll preference survive updates. The viewer is
for operational diagnosis; keep a separate copy when you need durable evidence.

## Remote services

Remote viewing becomes available when the service advertises administration access
and a compatible diagnostic-log capability. The service enforces authorization
on every request. If the capability is missing, unsupported, or access is denied,
the pane explains the problem and offers retry.

Remote logs show vetted diagnostic events rather than every raw server message.
The viewer retains up to 5,000 rows. It reads the active service stream, so it
cannot recover archived or rotated-away history.

> **Screenshot placeholder — Service logs:** show the selected remote service,
> diagnostic rows with a stack trace, auto-scroll, and Reload logs / reset gap.

## Handle gaps and errors

A numeric HTTP error identifies a failed request. Status **410** means the cursor
expired or history changed, for example after restart, rotation, or truncation.
Polling stops on errors. Use **Reload logs / reset gap** to start a fresh query of
the active file after reviewing the warning. This explicitly crosses the gap;
it does not recover missing history.

Polling pauses when the view is hidden, its tab is deselected, or its window is
closed. Reopening remote logs checks access again and starts a fresh active-file
query. Startup failures before HTTP, unavailable diagnostic families, and expired
archives need infrastructure logs from the service administrator.

Before sharing logs in an issue, remove credentials, private paths, and sensitive
project or data content. Include the service identity/version and approximate time
of the failed operation.
