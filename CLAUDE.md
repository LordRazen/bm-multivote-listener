# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

MultiVoteListener is a Paper/Spigot Minecraft plugin that listens for Votifier vote events and rewards players (money via Vault, points via PlayerPoints, healing, custom commands). It also tracks votes in a MySQL database and runs a monthly "vote trophy" reward cycle via LuckPerms permissions.

## Build & package

```bash
mvn clean package
```

- Compiled/shaded jar goes to `target/multivotelistener-1.0.jar`.
- A `maven-antrun-plugin` execution also copies the built jar to `latest/MultiVoteListener-latest.jar` on every `package`.
- `target/` is currently committed to the repo (despite being in `.gitignore`) — if you rebuild, check whether the tracked copies under `target/` need to be re-added/committed alongside source changes.
- There are no automated tests in this project.

Requires Java 21 (`maven.compiler.source/target` in `<properties>` say 17, but the `maven-compiler-plugin` config overrides to 21 — the plugin config wins).

## Runtime dependencies

Hard depends (plugin won't enable without them): **Votifier**, **Vault**.
Soft depends: **PlayerPoints** (points rewards), **LuckPerms** (trophy permissions via the LuckPerms Java API; the streak bonus checks the player's direct parent groups via `MultiVoteListener#getGroups`, which blocks and must only be called off the main thread). Vault, PlayerPoints, LuckPerms API are `provided`; HikariCP and the MySQL driver are loaded by Paper via `libraries:` in `plugin.yml` (nothing is shaded).

## Architecture

- `MultiVoteListener` (main class, `onEnable`/`onDisable`) — loads `config.yml`, starts the `DatabaseManager`, runs the `CREATE TABLE IF NOT EXISTS votes` migration, validates the config (`validateConfig`; votes are ignored while it is invalid), resolves PlayerPoints and the Vault economy (the economy is resolved lazily because providers may register later), registers `VoteEventListener` and the `/mvote` `CommandHandler`. Also owns the monthly trophy logic: `voteCheck` (async DB query, grants `blockminers.votepokal.M_YYYY` via the LuckPerms API) runs automatically 30s after startup on the 1st of a month, once per month (last run is stored in `state.yml`); `receiveTrophies` removes+saves the permissions via the LuckPerms API *before* paying out and guards against concurrent claims per player.
- `listeners/VoteEventListener` — the core vote-handling flow, all in one `onVote(VotifierEvent)` handler:
  1. Resolve which configured `services.*` entry matches the incoming vote (falls back to `services.default` if enabled, else drops unknown votes).
  2. Normalize the vote timestamp — see the timezone hack below.
  3. Deduplicate + persist the vote to the `votes` table via `saveVoteIfNotExists` (dedup key: uuid + votesite + date); if already voted today for that service, the whole reward flow is skipped.
  3b. `calculateStreakDays` computes the consecutive-day streak (current month, `vote_streak.services` only) for players who are a direct parent member of a group in `vote_streak.groups`; bonus `(days-1) * bonus_per_day` is added to the Vault deposit, once per day, only for votes dated today (Berlin).
  4. Broadcast a vote message (with clickable URL via `UrlBroadcast`), then apply rewards: Vault money, PlayerPoints, heal/feed, online commands, offline commands.
- `database/DatabaseManager` — generic HikariCP wrapper reading `database.*` / `database.hikari.*` from `config.yml`. `executeQuery`/`executeUpdate` take lambda-based `StatementPreparer`/`ResultSetExtractor` for parameterized SQL; `runMigration` runs raw DDL. Plugin disables itself if pool init or the initial migration fails.
- `commands/CommandHandler` — implements `/mvote` (`reload`, `status`, `services`, `give-trophies`, `receive-trophies <player>`, `testvote <player> [service]` (fires a synthetic `VotifierEvent` through the normal flow, no dedup bypass), default → usage). Permission check requires OP or `mvote.admin`.
- `Tools` — static helpers: Minecraft `&`→`§` color code translation/stripping, and building the text responses for `/mvote status` / `/mvote services` / usage.
- `UrlBroadcast` — Adventure `Component`/`ClickEvent` based broadcast with an open-URL click event.

## `config.yml` conventions

- `services.*` entries each define `name` (matched against the incoming Votifier service name), `url`, `enabled`, `money`, `points`, `heal`, `online_commands`, `offline_commands`, optional `usermessage` (suppresses the standard reward messages when set, except the streak bonus message). `services.default` is the fallback when no `name` matches and `enabled: true`.
- Message strings under `messages.*` support placeholders (`%name%`, `%service%`, `%amount%`, `%player_name%`, `%month%`, `%year%`) substituted via `String.replaceAll`, and `&`-style color codes reformatted by `Tools.reformatColorCodes`.
- `database.*` configures the HikariCP pool (see README for the full key list); `/mvote reload` reloads `config.yml` but does **not** restart the `DatabaseManager` pool.
- Editing `src/main/resources/config.yml` is the template shipped in the jar; it's Maven-filtered (`filtering: true`), so `${...}` placeholders (e.g. `${project.version}` in `plugin.yml`) get substituted at build time.

## Known sharp edges (be careful, don't "fix" silently)

- `VoteEventListener.onVote` only resolves the service and normalizes the timestamp on the main thread; dedup/insert/streak run async and `giveRewards` runs back on the main thread. Usernames are validated against `^[A-Za-z0-9_]{1,16}$` because they are inserted into console commands.
- The vote timestamp timezone handling is service-name-keyed by observation, not by a documented API contract (see the German comment in `VoteEventListener` around line 155): `minecraft-server.eu` timestamps are treated as GMT, everything else as `Europe/Berlin`. If you add new vote services with different timestamp behavior, this switch needs a new case.
- Vote dedup is per calendar date (`date` column), not per timestamp — a service that sends multiple votes per day for the same player will only reward the first.
