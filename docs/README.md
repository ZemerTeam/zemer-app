# Zemer repository documentation

This documentation is code-derived. It records facts visible in the repository at the time of writing and intentionally avoids product claims that are not backed by source files, Gradle configuration, Android resources, generated Room schemas, or Kotlin declarations.

## Documentation set

| Document | Scope |
| --- | --- |
| [`repository-map.md`](repository-map.md) | Top-level project structure, Gradle modules, Android manifest facts, database schema, resource groups, and generated code inventory. |
| [`whitelist/README.md`](whitelist/README.md) | Artist whitelist storage, Firebase fetch path, filtering rules, sync integration points, UI entry points, and database queries. |
| [`innertube/README.md`](innertube/README.md) | `:innertube` module architecture, request wrapper APIs, parser pages, models, dependencies, and consumers in the app module. |
| [`ui/README.md`](ui/README.md) | Compose UI structure, navigation routes, screen files, reusable components, player UI, settings UI, and theme utilities. |

## Ground rules used while documenting

- Facts are derived from tracked files and local source inspection only.
- Generated build directories and Gradle caches are not treated as product source.
- Where behavior depends on runtime data from YouTube, Firebase, Android services, or user preferences, the docs describe the code paths and stored fields rather than asserting external data values.
