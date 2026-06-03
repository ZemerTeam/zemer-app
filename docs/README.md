# Zemer repository documentation

This documentation is code-derived. It records facts visible in tracked repository files and avoids product claims that are not backed by source files, Gradle configuration, Android resources, generated Room schemas, or Kotlin declarations. Runtime data from YouTube, Firebase, Android services, or user preferences is documented only as code paths and stored fields.

## Documentation set

| Document | Scope |
| --- | --- |
| [`repository-map.md`](repository-map.md) | Top-level project structure, Gradle modules, Android manifest facts, database schema, source/resource counts, and generated inventory pointers. |
| [`app/README.md`](app/README.md) | `:app` module map: packages, Android components, DI, auth, sync, playback, lyrics, database, viewmodels, resources, assets, and native code. |
| [`app/database.md`](app/database.md) | Room database facts: schema 32 entities, fields, primary keys, indices, migrations, DAO annotation counts, and DAO method inventory. |
| [`app/playback.md`](app/playback.md) | Playback, queues, download services, media session constants, player UI, and playback consumers. |
| [`app/preferences-sync-auth.md`](app/preferences-sync-auth.md) | DataStore preference keys, content-filter state, Firebase auth managers, cloud preference sync models, and sync utility state. |
| [`app/viewmodels.md`](app/viewmodels.md) | ViewModel inventory with declarations and referenced app/InnerTube dependencies. |
| [`build-release.md`](build-release.md) | Root Gradle/settings facts, GitHub Actions release workflow, native/submodule facts, and auxiliary JVM module facts. |
| [`whitelist/README.md`](whitelist/README.md) | Artist whitelist storage, Firebase fetch path, filtering rules, sync integration points, UI entry points, and database queries. |
| [`innertube/README.md`](innertube/README.md) | `:innertube` module architecture, request wrapper APIs, parser pages, models, dependencies, and consumers in the app module. |
| [`ui/README.md`](ui/README.md) | Compose UI structure, navigation routes, screen files, reusable components, player UI, settings UI, and theme utilities. |
| [`reference/kotlin-files.md`](reference/kotlin-files.md) | Every tracked Kotlin file with line count, package, composable flag, declaration list, import count, and top external import roots. |
| [`reference/non-kotlin-files.md`](reference/non-kotlin-files.md) | Every tracked non-Kotlin file with line/byte count and file-type-specific metadata for XML, JSON, Gradle, scripts, assets, resources, and gitlinks. |
| [`reference/resource-index.md`](reference/resource-index.md) | Android resource directory/file inventory with XML roots and resource names where parseable. |

## Ground rules used while documenting

- Facts are derived from tracked files and local source inspection only.
- Generated build directories and Gradle caches are not treated as product source.
- Inventories use scripts over tracked files; they intentionally report declarations and metadata instead of inventing behavior.
- If a tracked path is a gitlink or cannot be text-decoded, the docs record that fact rather than content.
