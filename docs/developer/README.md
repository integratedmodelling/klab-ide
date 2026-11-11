# Developer documentation

This directory contains build instructions, implementation contracts, verification
notes, and unfinished work for maintainers of the JavaFX desktop client. The
[main README](../../README.md) is the user documentation entry point.

## Current entry points

| Document | Purpose |
| --- | --- |
| [Build and packaging](BUILD.md) | Java/Maven prerequisites, shared renderer assets, local launch, packaging, and CI |
| [Architecture](ARCHITECTURE.md) | Source map, workbench lifecycle, and service integration |
| [Implementation gaps](GAPS.md) | Source-confirmed gaps and follow-up work, separate from user procedures |
| [Documentation maintenance](DOCUMENTATION.md) | Audience boundary, coverage evidence, and screenshot requirements |
| [Application integration](APPLICATIONS.md) | Runtime UI instances, browser bridge, asset packaging, and test scope |
| [Workflow integration](WORKFLOWS.md) | Provider/stage contracts, revision handling, and historical verification |
| [Proposal-review integration](PROPOSAL_REVIEW.md) | Candidate binding, lexical markers, checks, ownership, and verification |
| [Semantic composer integration](SEMANTIC_COMPOSER.md) | Semantic session recovery, authority contracts, timings, and staged implementation notes |
| [Service-log integration](REMOTE_LOGS.md) | Transport, polling lifecycle, local-file handling, and verification boundaries |
| [Historical documents](history/README.md) | Superseded source inventories, architecture audit, build notes, and the previous mixed-audience guide |

The integration documents retain dated implementation and test evidence. Their
recorded test counts describe those runs, not a new verification of this checkout.
Consult current source and [GAPS.md](GAPS.md) before treating a past limitation as
still open. Repository product/design guidance remains in [PRODUCT.md](../../PRODUCT.md)
and [DESIGN.md](../../DESIGN.md).
