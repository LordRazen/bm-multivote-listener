# Code-Review MultiVoteListener

Stand: 2026-10-24. Geprüft: alle Quelldateien, `pom.xml`, `plugin.yml`, `config.yml` und das gebaute Jar.
Es wurde nichts ausgeführt. Das Plugin hat keine Tests. Mit „vermutlich“ markierte Punkte sind aus dem Code abgeleitet und nicht live bestätigt.

## Übersicht

| # | Schwere | Befund | Datei |
|---|---------|--------|-------|
| 1 | Kritisch | Vault-API und PlayerPoints ins Jar geshadet | `pom.xml:101-110` |
| 2 | Kritisch | Essentials-Zugriff bricht Votes vor dem Speichern ab | `VoteEventListener.java:141-143` |
| 3 | Hoch | Command-Injection über Spielername/Service | `VoteEventListener.java:324-337` |
| 4 | Hoch | Blockierende JDBC-Aufrufe im Main-Thread | `VoteEventListener`, `MultiVoteListener` |
| 5 | Hoch | `voteCheck` läuft bei jedem Start am 1. des Monats | `MultiVoteListener.java:138-141` |
| 6 | Hoch | Race beim Abholen der Trophäen (Doppelauszahlung) | `MultiVoteListener.java:284-358` |
| 7 | Hoch | `enabled` wird für normale Services nie geprüft | `VoteEventListener.java:91-114` |
| 8 | Mittel | Color-Code-Regex ersetzt jedes `&` | `Tools.java:23` |
| 9 | Mittel | `Long.parseLong(timestamp)` ohne Absicherung | `VoteEventListener.java:162` |
| 10 | Mittel | Dedup ohne Unique-Key, Migration evtl. ungültig | `saveVoteIfNotExists`, `onEnable` |
| 11 | Mittel | Economy wird nur beim Start aufgelöst | `MultiVoteListener.java:92-101` |
| 12 | Mittel | Config-Fehler werden nie erkannt | `loadConfig`, `CommandHandler` |
| 13 | Mittel | `assert` als Null-Schutz | `giveTrophy` |
| 14 | Mittel | Fragiles Parsen der Trophäen-Permission | `MultiVoteListener.java:312-314` |
| 15 | Mittel | Falsche Config-Doku (`allow_fake_names`, `%amount%`) | `config.yml`, `VoteEventListener` |
| 16 | Niedrig | Aufräumen (siehe unten) | diverse |

## Befunde im Detail

### 1. Vault-API und PlayerPoints ins Jar geshadet (Kritisch)
Beide Abhängigkeiten haben den Default-Scope `compile`. Das Jar ist 24 MB groß und enthält `net/milkbowl/vault` (11 Klassen) und `org/black_ixx/playerpoints` (224 Klassen).
- Der Plugin-Classloader findet die eigene Kopie vermutlich vor der des Servers. Dann scheitert `(PlayerPoints) getPlugin("PlayerPoints")` (`MultiVoteListener.java:110`) mit `ClassCastException`, und `getRegistration(Economy.class)` findet keinen Provider.
- HikariCP und mysql-connector sind unrelokiert und können mit anderen Plugins kollidieren.

### 2. Essentials-Zugriff bricht Votes ab (Kritisch)
`VoteEventListener.java:141-143` castet `getPlugin("Essentials")` und ruft `getUser(username)` auf, bevor der Vote in die DB geschrieben wird.
- Ohne EssentialsX ist `ess` null, und der Vote geht verloren.
- Auch mit EssentialsX kann `getUser` für unbekannte Namen null liefern (relevant bei `allow_fake_names`).
- `essUser` wird nur für `essUser.getUUID()` bei PlayerPoints gebraucht (Z. 291). `user.getUniqueId()` reicht.
- Essentials fehlt in `softdepend`.

### 3. Command-Injection (Hoch)
`%name%` und `%service%` stammen aus dem Vote und werden ungeprüft per `replaceAll` in Konsolenbefehle eingesetzt (Z. 324-337). Votifier begrenzt nur die Länge, nicht die Zeichen. Ein Name wie `x permission set * true` könnte Argumente einschleusen.
`replaceAll` mit `$` oder `\` im Wert wirft zusätzlich `IllegalArgumentException`.

### 4. Blockierende JDBC-Aufrufe im Main-Thread (Hoch)
NuVotifier feuert das Event meines Wissens synchron. Jeder Vote macht bis zu 5 Queries im Server-Tick. Mit `connectTimeout=5000` und `socketTimeout=10000` (Default-URL) bedeutet ein DB-Problem bis zu 10 s Serverfreeze. Gleiches gilt für `voteCheck` und `receiveTrophies`.

### 5. `voteCheck` bei jedem Start am 1. (Hoch)
Wer seinen Pokal schon abgeholt hat (Permission entfernt), bekommt die Permission beim nächsten Neustart am selben Tag erneut. `lp` ist nicht in `softdepend`, der Befehl kann beim Start ins Leere laufen. `getOfflinePlayer(uuid).getName()` kann null sein (`lp user null …`).

### 6. Race bei der Trophäen-Abholung (Hoch)
Die Permissions werden direkt aus der LuckPerms-Tabelle gelesen, das `unset` läuft über die Konsole und wird von LuckPerms asynchron geschrieben. Ein Doppelklick im BossShop kann Kopf und Geld doppelt auszahlen. Das Entfernen wird nicht auf Erfolg geprüft, bevor belohnt wird.

### 7. `enabled` wird nicht geprüft (Hoch)
Die Service-Schleife matcht alle `services.*`, auch deaktivierte. Nur `default` wird geprüft. Passt der Name zu `multivote_default`, gilt `knownService = true`, auch wenn `default.enabled: false` ist.

### 8. Color-Code-Regex (Mittel)
`&(?!0-9a-fA-Fk-oK-OrR)` ist ein negativer Lookahead auf den wörtlichen Text, keine Zeichenklasse. Er trifft bei jedem `&` zu. Richtig: `&(?=[0-9a-fA-Fk-oK-OrR])`.

### 9. Timestamp-Parsing (Mittel)
Ein leerer oder nicht numerischer Timestamp wirft `NumberFormatException`, der Vote geht verloren.

### 10. Dedup und Migration (Mittel)
Check-then-Insert ohne Unique-Index erlaubt bei gleichzeitigen Votes Doppelbelohnung. Die Migration nutzt `date DATE … DEFAULT CURRENT_TIMESTAMP`, auf MySQL für DATE-Spalten vermutlich ungültig. `INT(11)` ist veraltet, und `votesite TEXT` verhindert einen normalen Unique-Index.

### 11. Economy nur beim Start aufgelöst (Mittel)
Registriert sich der Economy-Provider erst nach diesem Plugin, bleiben Geldbelohnungen dauerhaft aus.

### 12. Config-Fehler (Mittel)
`reloadConfig()` und `getConfig()` werfen bei kaputtem YAML nicht. Die Fehlermeldungen in `/mvote reload` und `loadConfig()` sind toter Code. Eine leere Config führt zu NPEs (`getConfigurationSection("services")`, `getString("message_prefix")`).

### 13. `assert` als Null-Schutz (Mittel)
In `giveTrophy` (Z. 337, 349) sind Asserts auf dem Server aus. Ist der Spieler zwischenzeitlich offline, gibt es eine NPE, nachdem der Kopf-Befehl schon lief.

### 14. Parsen der Trophäen-Permission (Mittel)
Eine Permission wie `blockminers.votepokal.*` löst `NumberFormatException` oder `ArrayIndexOutOfBounds` aus und bricht die Abholung ab. Negierte (`value = 0`) und abgelaufene Einträge werden nicht gefiltert. Das SQL nutzt doppelte Anführungszeichen, das scheitert unter `ANSI_QUOTES`.

### 15. Falsche Config-Doku (Mittel)
`allow_fake_names` ist im Code auskommentiert und wirkungslos. `%amount%` wird in Commands durch `""` ersetzt.

### 16. Aufräumen (Niedrig)
- Toter Code: `enableVaultPerm` (nie gesetzt; der Zweig in `CommandHandler.java:50` würde mit NPE scheitern), Feld `config`, Feld `newVote`, Parameter `plugin` in `receiveTrophies`, Kommentar-Gerümpel in `onVote`.
- `UrlBroadcast`: Javadoc nennt Bungee, der Code nutzt Adventure. Die Spigot/CraftBukkit-Weiche ist wegen `paper-api` immer „Spigot“. `Component.text` mit `§` besser über `LegacyComponentSerializer.legacySection()`.
- Logging uneinheitlich (`System.out.println`, `printStackTrace`, `utils.Logger`, `getLogger()`).
- `getMaxHealth()` deprecated, `Player.class.cast(user)` durch `user.getPlayer()` ersetzen.
- `LocalDate.now()` (Systemzone) im Monatslauf vs. Berlin in der Streak-Logik.
- `mvote.admin` nicht in `plugin.yml` deklariert; ohne Rechte gibt es keine Rückmeldung.
- Default-DB-Zugang `root`/`test`; `useSSL=false` in Connector/J 9 veraltet (`sslMode`).
- `ready`/`dataSource` im `DatabaseManager` nicht `volatile` (relevant, sobald async).
- `/mvote reload` meldet Erfolg in Rot (`&4`).

---

## Fix-Plan

Reihenfolge nach Risiko und Aufwand. Jede Phase ist einzeln baubar und committbar. Nach jeder Phase `mvn clean package` und ein Test auf einem Testserver (`/mvote testvote <name> [service]`).

### Phase 1: Build und Abhängigkeiten (Befund 1, 2)
1. `pom.xml`: `VaultAPI` und `playerpoints` auf `<scope>provided</scope>`.
2. HikariCP und mysql-connector entweder per `libraries:` in `plugin.yml` laden (Jar wird klein) oder im Shade-Plugin relocaten.
3. `plugin.yml`: `softdepend` um `Essentials`, `LuckPerms` und das Economy-Plugin ergänzen.
4. `VoteEventListener`: Essentials-Import und `essUser` entfernen, PlayerPoints mit `user.getUniqueId()` füllen.
5. Prüfen: Jar-Größe, `unzip -l` enthält kein `net/milkbowl` oder `org/black_ixx`, Plugin startet ohne EssentialsX.

### Phase 2: Quick-Fixes im Vote-Flow (Befund 3, 7, 8, 9, 15)
1. Username gegen `^[A-Za-z0-9_]{1,16}$` validieren, sonst Vote verwerfen und loggen.
2. `replaceAll` für Platzhalter durch `replace` ersetzen.
3. Service-Schleife: bei jedem Eintrag `enabled` prüfen. `default` nur verwenden, wenn `enabled: true`, und nicht über den Namen matchen.
4. `Tools.reformatColorCodes`: Regex auf `&(?=[0-9a-fA-Fk-oK-OrR])` korrigieren.
5. Timestamp: `NumberFormatException` abfangen und auf `System.currentTimeMillis()` zurückfallen (mit Log-Eintrag).
6. `allow_fake_names` entweder implementieren oder aus Config und Doku entfernen. `%amount%`-Doku in der Config angleichen.

### Phase 3: Trophäen-System (Befund 5, 6, 13, 14)
1. Monatslauf idempotent machen: letzten ausgeführten Monat persistieren (eigene Tabelle oder Config) und nur einmal pro Monat ausführen.
2. Den Lauf per verzögertem Task nach Serverstart ausführen (LuckPerms geladen).
3. Permissions per UUID setzen (`lp user <uuid> …`) statt per Namen.
4. `receiveTrophies`: pro Spieler sperren (z. B. `Set<UUID>` für laufende Abholungen). Permission über die LuckPerms-API prüfen und entfernen, erst bei Erfolg belohnen.
5. Permission-Parsing absichern (try/catch, ungültige Einträge überspringen), nur `value = 1` und nicht abgelaufene Einträge lesen, Anführungszeichen im SQL auf einfache ändern.
6. `assert` durch echte Null-Checks mit Fehlermeldung ersetzen.

### Phase 4: Datenbank und Threading (Befund 4, 10)
1. Migration korrigieren (`votesite VARCHAR(64)`, `date` ohne ungültigen Default, `INT` statt `INT(11)`). Für bestehende Tabellen ein `ALTER TABLE` mit Unique-Index `(uuid, votesite, date)`; vorher Duplikate bereinigen.
2. `saveVoteIfNotExists` auf `INSERT` mit Duplicate-Handling umstellen und `insert`-Ergebnis als Dedup-Signal nutzen.
3. DB-Teil von `onVote`, `voteCheck` und `receiveTrophies` async ausführen (`runTaskAsynchronously`). Belohnungen (Heal, Commands, Vault, Nachrichten) wieder synchron per `runTask`.
4. `DatabaseManager`: Felder `volatile`, JDBC-URL-Timeouts in der Default-Config überdenken.
5. Prüfen: parallele Votes (zwei `testvote` direkt hintereinander), DB gestoppt (Server bleibt flüssig).

### Phase 5: Robustheit der Konfiguration (Befund 11, 12)
1. Economy lazy auflösen (beim ersten Gebrauch erneut versuchen).
2. Config-Validierung beim Start und bei `/mvote reload`: Pflichtschlüssel prüfen (`message_prefix`, `services`, Messages), bei Fehlern klare Meldung und ggf. Votes ablehnen.
3. Reload-Meldung: Erfolg in Grün, Fehler in Rot.

### Phase 6: Aufräumen (Befund 16)
Toter Code entfernen, Logging vereinheitlichen, `UrlBroadcast` auf `LegacyComponentSerializer` umstellen und Javadoc korrigieren, `mvote.admin` in `plugin.yml` deklarieren und Rückmeldung bei fehlenden Rechten, Zeitzonen vereinheitlichen, `CLAUDE.md` an den neuen Stand anpassen (Abschnitt „Known sharp edges“).

### Verifikation je Phase
- `mvn clean package` ohne Fehler, Jar in `target/` und `latest/` aktualisiert (bei committetem `target/` mit hinzufügen).
- Manuelle Tests auf dem Testserver: Vote bekannter Service, unbekannter Service mit Default, deaktivierter Service, Fake-Name, ungültiger Name, doppelter Vote am selben Tag, Streak-Bonus, `give-trophies` und `receive-trophies` (inkl. Doppelaufruf).
- Es gibt keine automatisierten Tests. Für Phase 3 und 4 lohnt sich ein kleines Test-Setup (z. B. MockBukkit oder eine Testdatenbank), falls das Plugin weiter wachsen soll.
