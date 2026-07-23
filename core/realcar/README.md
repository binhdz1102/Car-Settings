# core:realcar

`core:realcar` is a coroutine-first, type-safe wrapper around Android Automotive
`CarPropertyManager`.

`core:realcar` là lớp bọc type-safe, ưu tiên coroutine cho
`CarPropertyManager` của Android Automotive.

Documentation:

- [Hướng dẫn tiếng Việt](docs/USAGE_VI.md)
- [English guide](docs/USAGE_EN.md)

Main capabilities:

- type-safe keys for every VHAL value shape;
- throwing and non-throwing single read/write APIs;
- VHAL-confirmed asynchronous writes;
- `Flow` and callback subscriptions with shared platform subscriptions;
- immutable last-known-value cache;
- normalized property metadata;
- high-throughput, ordered, chunked batch read/write with partial results;
- normalized permission, type, area, availability, timeout, service, and async errors.

The library does not merge vehicle permissions into the host app. Each product must request
only the permissions required by the properties it uses.
