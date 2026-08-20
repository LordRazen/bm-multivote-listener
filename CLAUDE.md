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
Soft depends: **PlayerPoints** (points rewards), **EssentialsX** (used directly in `VoteEventListener` to resolve users — currently not soft-guarded, see below), **LuckPerms** (trophy permissions, invoked via console commands, not the Java API).

## Architecture

- `MultiVoteListener` (main class, `onEnable`/`onDisable`) — loads `config.yml`, starts the `DatabaseManager`, runs the `CREATE TABLE IF NOT EXISTS votes` migration, detects Vault/PlayerPoints/Spigot-vs-CraftBukkit, registers `VoteEventListener` and the `/mvote` `CommandHandler`. Also owns the monthly trophy logic (`voteCheck`, `giveTrophy`, `receiveTrophies`, `setPermissionViaConsole`/`removePermissionViaConsole`) — auto-runs `voteCheck` on the 1st of each month via a date check in `onEnable` (not a scheduled task, so it only fires if the server restarts on the 1st).
- `listeners/VoteEventListener` — the core vote-handling flow, all in one `onVote(VotifierEvent)` handler:
  1. Resolve which configured `services.*` entry matches the incoming vote (falls back to `services.default` if enabled, else drops unknown votes).
  2. Normalize the vote timestamp — see the timezone hack below.
  3. Deduplicate + persist the vote to the `votes` table via `saveVoteIfNotExists` (dedup key: uuid + votesite + date); if already voted today for that service, the whole reward flow is skipped.
  4. Broadcast a vote message (with clickable URL on Spigot via `UrlBroadcast`), then apply rewards: Vault money, PlayerPoints, heal/feed, online commands, offline commands.
- `database/DatabaseManager` — generic HikariCP wrapper reading `database.*` / `database.hikari.*` from `config.yml`. `executeQuery`/`executeUpdate` take lambda-based `StatementPreparer`/`ResultSetExtractor` for parameterized SQL; `runMigration` runs raw DDL. Plugin disables itself if pool init or the initial migration fails.
- `commands/CommandHandler` — implements `/mvote` (`reload`, `status`, `services`, `give-trophies`, `receive-trophies <player>`, default → usage). Permission check requires OP or `mvote.admin`.
- `Tools` — static helpers: Minecraft `&`→`§` color code translation/stripping, and building the text responses for `/mvote status` / `/mvote services` / usage.
- `UrlBroadcast` — Adventure `Component`/`ClickEvent` based broadcast with an open-URL click event; only used when `plugin.isSpigot()` is true, with a CraftBukkit-plain-broadcast fallback on `NullPointerException`.
- `utils/Logger` — trivial wrapper around `Bukkit.getLogger().info`.

## `config.yml` conventions

- `services.*` entries each define `name` (matched against the incoming Votifier service name), `url`, `enabled`, `money`, `points`, `heal`, `online_commands`, `offline_commands`, optional `usermessage` (suppresses the standard reward messages when set). `services.default` is the fallback when no `name` matches and `enabled: true`.
- Message strings under `messages.*` support placeholders (`%name%`, `%service%`, `%amount%`, `%player_name%`, `%month%`, `%year%`) substituted via `String.replaceAll`, and `&`-style color codes reformatted by `Tools.reformatColorCodes`.
- `database.*` configures the HikariCP pool (see README for the full key list); `/mvote reload` reloads `config.yml` but does **not** restart the `DatabaseManager` pool.
- Editing `src/main/resources/config.yml` is the template shipped in the jar; it's Maven-filtered (`filtering: true`), so `${...}` placeholders (e.g. `${project.version}` in `plugin.yml`) get substituted at build time.

## Known sharp edges (be careful, don't "fix" silently)

- `VoteEventListener.onVote` unconditionally casts `Bukkit.getServer().getPluginManager().getPlugin("Essentials")` to `Essentials` and calls `.getUser(username)` — this will NPE if EssentialsX isn't installed, even though it's only a softdepend in `plugin.yml`.
- The vote timestamp timezone handling is service-name-keyed by observation, not by a documented API contract (see the German comment in `VoteEventListener` around line 155): `minecraft-server.eu` timestamps are treated as GMT, everything else as `Europe/Berlin`. If you add new vote services with different timestamp behavior, this switch needs a new case.
- Vote dedup is per calendar date (`date` column), not per timestamp — a service that sends multiple votes per day for the same player will only reward the first.
