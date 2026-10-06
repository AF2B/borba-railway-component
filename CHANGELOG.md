# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `failure`, which builds a failure from an `:error` code and its context, `lift`, `either?`, `fmap-left` and `unwrap-or`.
- `default-error-statuses`, and `:error->status` accepts a map that is merged over it, besides a function.
- A test suite with 100% coverage, including the monad and functor laws checked on generated values.

### Changed

- **Breaking:** `bind`, and so `>>=` and `|>`, throws when a step returns something that is neither a `Right` nor a `Left`.
- **Breaking:** `try-right` no longer returns the message of an exception, which can describe internals. A failure from an
  exception is `{:error :exception :exception-class "…"}`; an `ex-info` whose data has an `:error` is returned as it is.
- **Breaking:** the HTTP status of the `:exception` error is 500.
- The published library is named `io.github.af2b/borba-railway-component`.

### Fixed

- `|>` evaluated its initial expression twice.

## [1.0.0] - 2026-03-30

First release: the `Right` and `Left` records, `bind`, `fmap`, `>>=`, `|>`, `unwrap`, `either`, `try-right`, `validate` and
`borba.railway.http/railway->response`.

[Unreleased]: https://github.com/AF2B/borba-railway-component/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/AF2B/borba-railway-component/releases/tag/v1.0.0
