# Build, run, and package the IDE

[Developer documentation](README.md)

## Prerequisites

Use JDK 21 and Maven, or the included Maven wrapper. The POM targets Java 21 and
JavaFX 21.0.6. The IDE depends on matching k.LAB `1.0.0-SNAPSHOT` artifacts and
companion libraries including the modeler, editor, map, and graph libraries.
Resolve them from the configured repositories or install compatible builds in
your selected local Maven repository. A client build does not provision services
or authenticate a user.

The runtime dependency `klab.core.services` with classifier `application-ui` contains
only the shared browser renderer assets. If that artifact is unavailable or stale,
build it in the companion `klab-services` checkout:

```powershell
mvn -pl klab.core.services -am -Pwebui "-DskipTests" install
```

Keep API, common/client, modeler, and service artifacts aligned. In particular,
new API interfaces paired with older clients can inherit unsupported default
methods without issuing HTTP requests. The workflow compatibility test checks
the dependency implementations used by the IDE.

## Test and launch

From the IDE repository root on Windows:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd javafx:run@run
```

On Linux/macOS:

```sh
./mvnw clean test
./mvnw javafx:run@run
```

The `run` execution is configured in the root [pom.xml](../../pom.xml).
Service-backed workflows need compatible running services. Use component fixtures
only as synthetic QA; they do not establish authenticated live-service behavior.

## Package

```powershell
.\mvnw.cmd clean package
```

The shaded input is `target/klab-ide-all-1.0.0-SNAPSHOT.jar`. Every package verifies
the presence of `application-ui.html`, `META-INF/klab/ui-embedded/renderer.js`, and
`META-INF/klab/ui-embedded/renderer.css`, even with tests skipped. Missing assets
produce the explicit `Missing packaged Quasar renderer` diagnostic. The packaged
IDE does not require an external Node.js installation for this renderer.

Conveyor packaging additionally requires Conveyor and the signing/license environment
expected by [conveyor.conf](../../conveyor.conf) and [Jenkinsfile](../../Jenkinsfile):

```powershell
.\mvnw.cmd clean package -Pconveyor
```

The profile invokes `conveyor --passphrase=env:SIGNING_PASSPHRASE make` with
`${conveyor.target}` (default `app`). Signing keys, target selection, distribution
URLs, and release publishing are environment/CI concerns; use current configuration
rather than copying the historical build examples. The configuration uses version
`1.0.0` plus a Git-derived revision and allows `CONVEYOR_BASE_URL` to override the
site URL. It does not use the older `PROJECT_VERSION` recipe.

See [Application integration](APPLICATIONS.md) for renderer packaging and
[Workflow integration](WORKFLOWS.md) / [Proposal-review integration](PROPOSAL_REVIEW.md)
for focused validation procedures and recorded baseline failures.
