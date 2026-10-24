# MultiVoteListener

*MultiVoteListener* is a lightweight Votifier listener for Paper/Spigot servers, inspired by Simple Vote Listener. It adds a couple of features required on a server I am working on as an Admin: multiple independent vote services, vote tracking in MySQL, a vote streak bonus and a monthly "vote trophy" reward cycle.

## Features

- Handle multiple vote services independently, each with its own rewards
- Vault (money) and PlayerPoints (points) rewards, heal/feed
- Run console commands upon vote, either only if the player is online or always
- Customizable messages & broadcasts with color codes (`&`) and placeholders
- Clickable broadcast (opens the vote URL) - Spigot/Paper only
- Enable / disable selected services, default service for votes from unknown services
- Votes are stored in a MySQL database (HikariCP) and deduplicated per player, service and day
- Vote streak: bonus money for consecutive daily votes (LuckPerms groups)
- Monthly vote trophy: players who voted often enough receive a reward (via LuckPerms permissions)
- Admin command to simulate a vote for testing

## Requirements

| Type | Plugins |
| --- | --- |
| Required | Votifier, Vault |
| Optional | PlayerPoints (points rewards) |
| Optional | LuckPerms (vote trophies and streak groups, both via its API) |

- Java 21
- A MySQL/MariaDB database. The plugin creates the `votes` table on startup and disables itself if the connection pool or the migration fails.

## Build & Install

```bash
mvn clean package
```

The shaded jar is created in `target/multivotelistener-1.0.jar` and copied to `latest/MultiVoteListener-latest.jar`. It contains HikariCP and the MySQL driver. Put it into the `plugins` folder and start the server once to generate `config.yml`.

## Commands

All commands require OP or the permission `mvote.admin`.

| Command | Description |
| --- | --- |
| `/mvote` | Show usage |
| `/mvote reload` | Reload `config.yml` (does not restart the database pool) |
| `/mvote status` | Show active plugin hooks (Vault, PlayerPoints) |
| `/mvote services` | List configured vote services |
| `/mvote testvote <player> [service]` | Simulate a vote (see below) |
| `/mvote give-trophies` | Manually run the monthly trophy check |
| `/mvote receive-trophies <player>` | Hand out the player's monthly trophy rewards if they have the permission |

### Simulating a vote

`/mvote testvote <player> [service]` fires a synthetic Votifier event with the current time. It goes through the normal vote flow (database entry, streak, broadcast, rewards).
If `service` is omitted, the first configured service (except `default`) is used; otherwise pass the service `name` from the config, e.g. `minecraft-server.eu`.

Deduplication applies like for real votes: a second test vote for the same player and service on the same day is skipped. Delete the row from the `votes` table to repeat it.

## Configuration

The reference is the commented `config.yml` generated on first start. Main sections:

### Services

Each entry below `services:` defines one vote service:

```yml
services:
  default:                    # fallback if no service matches; set enabled: false to ignore unknown votes
    name: 'multivote_default'
    url: 'http://example.com' # valid URL makes the broadcast clickable
    enabled: true
    money: 150                # requires Vault
    points: 1                 # requires PlayerPoints
    heal: true
    online_commands: []       # run only if the player is online
    offline_commands: []      # run in any case
  service0:
    name: 'minecraft-server.eu'   # must match the service name sent by Votifier
    ...
```

Optional `usermessage` replaces the standard player reward messages (the streak bonus message is still sent). Messages and commands support the placeholders `%name%`, `%service%`, `%amount%`, `%player_name%`, `%month%`, `%year%` (and `%days%` for the streak message).

### Database

The plugin uses a HikariCP connection pool:

```yml
database:
  jdbcUrl: "jdbc:mysql://127.0.0.1:3306/yourdb?useSSL=false&serverTimezone=UTC&tcpKeepAlive=true&connectTimeout=5000&socketTimeout=10000"
  username: "root"
  password: "change-me"
  hikari:
    poolName: "MultiVoteListenerPool"
    maximumPoolSize: 10
    minimumIdle: 2
    connectionTimeout: 5000
    idleTimeout: 600000
    maxLifetime: 1800000
    keepaliveTime: 300000
    validationTimeout: 3000
    initializationFailTimeout: 1
    leakDetectionThreshold: 0
    autoCommit: true
    connectionTestQuery: "SELECT 1"
```

### Vote streak

Players who are a direct member (parent) of one of the configured LuckPerms groups receive a bonus for voting on consecutive days within a month: day 1 gets the normal reward, day N gets the normal reward plus `(N-1) * bonus_per_day`. A gap or a new month resets the streak. The bonus is paid once per day.

```yml
vote_streak:
  enabled: true
  bonus_per_day: 50
  max_streak_days: 30                # safety cap, 0 = no limit
  groups: ['neuling', 'entdecker']   # LuckPerms groups (parents) that receive the bonus
  services: ['minecraft-server.eu']  # vote services (services.*.name) that count for the streak
```

### Vote trophy

`monthly_vote_reward_money`, `trophy_head_command` and the `vote_trophy_*` / `given_trophies` messages control the monthly trophy. The check runs automatically on the 1st of a month when the server starts (it is not a scheduled task), or manually via `/mvote give-trophies`. Players then collect their reward with `/mvote receive-trophies <player>`.

## Known limitations

- Votes are deduplicated per calendar day and service; additional votes the same day are not rewarded.
- Vote timestamps from `minecraft-server.eu` are interpreted as GMT, all others as `Europe/Berlin`. New services with different behavior need a new case in `VoteEventListener`.
- Votes of disabled services are ignored; invalid usernames are dropped.

## Support & Troubleshooting

Need help? Have a feature request? Feel free to contact me via email, PN, forum, etc. Since the plugin is running on my server as well I am very motivated to get errors fixed immediately once they come to my attention.
