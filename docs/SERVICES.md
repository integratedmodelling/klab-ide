# Services and local distribution

[Documentation home](../README.md) · [Service logs](REMOTE_LOGS.md)

The IDE uses connected services for knowledge, data, reasoning, and execution.
The services available to you depend on your identity and permissions. Local
distribution controls manage services on your computer; remote services have
their own availability and access rules.

## Check the services you need

| Service | What it provides in the IDE |
| --- | --- |
| Reasoner | Shared worldview, semantic search/composition, and semantic validation |
| Resources | Workspaces, projects, resources, workflows, and saved source |
| Resolver | Resolution of knowledge and resources for observation requests |
| Runtime | Digital twins, observations, and execution |

Use the lower navigation rail's service buttons to open dashboards. When more than
one instance is available, check which service is selected before acting. Browsing
a workspace requires Resources; observing normally needs the cooperating stack.
Local behavior execution and the project web runner require a local Runtime.

## Install, update, and start local services

Use the download button to open distribution management. Inspect available and
installed distributions. Each card identifies its version/build and whether it is
installed, current, or available from the network.

1. Use **Synchronize** to install a binary distribution or obtain its current files.
2. Use **Verify integrity** on an installed distribution to check its local files.
3. Stop local services before selecting another installed build with **Make current**.
   A pending switch shows **Switching...**; wait for **Current** before continuing.
4. Start services with the power control and wait for startup to finish.

**Delete from disk** removes a non-current installed distribution after confirmation.
A source-checkout/HEAD card is managed from its source tree and does not offer binary
distribution actions. Stop local services before synchronizing the current distribution.

Stopping local services interrupts work that depends on them. Check active runs
first. Access to a remote service can remain available independently.

> **Screenshot placeholder — Distribution management:** show installed/available
> distribution information, an offered update action, and the power state after startup.

## Inspect a dashboard

A selected service's dashboard shows operational status, health/load, memory, and
advertised components. Service links open its web dashboard, public capabilities,
and status page. A browser page may need a separate sign-in.

Component entries identify version, origin, and update state. Use history to inspect
recorded component events. Where you have administrator rights and the service
supports it, you can check/install an update or remove a component. Built-in
components cannot be removed. Read the named component and version in the removal
confirmation; other work may depend on it.

**Import components** offers the service's import schemas. Choose a schema and
supply its required file, URL, or parameters. Available schemas vary by service.
Service settings and email settings appear when supported. Administrative credential
controls require service administrator rights; these are separate from your user profile.

### Administer credential sets

Open **Credentials** on an authorized service dashboard. Use **Add credential set**,
enter a name and endpoint URL, select an authentication schema, and complete its
required fields. Set allowed/excluded users and groups, or allow all authenticated
users where appropriate, then select **Save credential set**.

Consumers refer to the set by its ID, as in a local project's Git credential choice.
Secret values are stored on the service and are not returned to the form. When
editing, leave a secret blank to retain it or use its explicit clear control to
remove it. Deleting a set breaks references to its ID; check those uses before
confirming **Delete credential set**.

> **Screenshot placeholder — Service dashboard:** show selected service identity,
> load/memory charts, component version/update/history controls, and administration actions.

## Diagnose failures

Open **Service logs** on the selected dashboard or **Logs** in its service details.
See [Reading service logs](REMOTE_LOGS.md) for local/remote behavior and reset rules.
Failures before the service's HTTP endpoint starts require local distribution or
infrastructure logs. [Troubleshooting](TROUBLESHOOTING.md) explains what to collect.
