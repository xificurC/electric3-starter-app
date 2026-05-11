# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A starter template for [Electric v3](https://electric.hyperfiddle.net/) — Hyperfiddle's reactive full-stack Clojure/ClojureScript framework. The app boots a server-side and client-side Electric process that stay synchronized over a single WebSocket.

## Commands

```bash
# Dev (Jetty 10+, the default)
clj -A:dev -X dev/-main           # shell entry; prints login URL on first run
# REPL alternative: start REPL with :dev alias, then (dev/-main)
# Server: http://localhost:8080

# Dev (Jetty 9 — alternative WS adapter)
clj -A:dev:jetty9 -X dev-jetty9/-main
# Also edit src-dev/user.clj to require 'dev-jetty9 :as dev (see comment line)

# Prod
clj -X:build:prod build-client        # compile cljs (use :build:prod, NOT clj -T)
clj -M:prod -m prod                   # run server

# Uberjar
clj -X:build:prod uberjar :build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# Local Electric source (when hacking on Electric itself)
# Add :local alias — overrides com.hyperfiddle/{electric,hyperfiddle-contrib,electric-secret,hyperfiddle-agent} to local checkouts at ../

# Jetty smoke tests (manual; opens browser)
./test-jetty-setups.sh
./test-jetty-setups.sh --no-browser           # CI-friendly
./test-reject-stale-client.sh
```

There is no test suite — the `test-*.sh` scripts are integration smoke tests against a running server.

## Architecture

**Single-JVM requirement.** Electric's hot reload requires the Clojure REPL and the shadow-cljs ClojureScript compiler to share one JVM so client and server compilation stay in lockstep. `src-dev/dev.cljc` boots shadow-cljs in-process. If your editor spawns shadow in a separate process, hot reload will misbehave. `src-dev/user.clj` is loaded automatically under `:dev` and `(require 'dev)`s the chosen entrypoint so the REPL is immediately ready.

**Three deps profiles, two Jetty stacks.**

- `:dev` adds `src-dev` + shadow-cljs.
- `:prod` adds `src-prod`.
- `:build` adds `src-build` + `tools.build` for client compilation and uberjar packaging.
- `:jetty9` is a stack override: downgrades `ring/ring` to 1.9.6 and pins Jetty 9 WebSocket deps. The hyperfiddle-agent supports both Jetty 9 and 12 via runtime classpath detection.

The dev and prod entrypoints come in two parallel variants (`dev` / `dev-jetty9`, `prod` / `prod-jetty9`) that differ only in how the WebSocket adapter is wired (`hyperfiddle.electric-ring-adapter3` vs `hyperfiddle.electric-jetty9-ring-adapter3`). The application code under `src/` is the same.

**Boot symmetry.** `electric_starter_app.main/electric-boot` is one function with reader conditionals: on JVM it calls `e/boot-server` and consumes the ring-request; in the browser it calls `e/boot-client` and passes an `(e/amb)` no-value hole in the same arity slot. Server boot is invoked from the ring middleware (`electric-ring/wrap-electric-websocket`); client boot is invoked from the `^:export -main` in the dev/prod entrypoint.

**Hot reload.** Client entrypoints declare `^:dev/before-load` (tears down the running browser Electric process) and `^:dev/after-load` (re-boots), keyed on a `browser-process` var. shadow-cljs' `:build-hooks [(hyperfiddle.electric.shadow-cljs.hooks3/reload-clj)]` triggers Clojure-side reload on save.

**Version-coupled client/server.** In prod, client and server must run the exact same Electric build. `src-build/build.clj` writes `resources/electric-manifest.edn` with `:hyperfiddle/electric-user-version` (from `git describe`), and bakes the same version into the compiled JS via `:closure-defines`. At runtime `prod.cljc` reads the manifest via `comptime-resource` (a `clojure.java.io/resource` slurp at macro-expansion time). `electric-ring/wrap-reject-stale-client` rejects WS connections from a client whose version doesn't match; the client uses `electric-client/reload-when-stale` to hard-reload when the server version changes.

**Index page templating.** `prod.cljc/wrap-prod-index-page` serves `resources/public/electric_starter_app/index.prod.html`, substituting `$:key$` placeholders with values from the merged config + the shadow-cljs `manifest.edn` (which provides fingerprinted JS module paths for cache busting). Dev serves `index.dev.html` directly with no substitution.

## License

Electric v3 is free for non-commercial use; commercial use requires a license. Dev builds perform a compile-time login (prints a URL on first boot). Prod builds contain no license code on the classpath.
