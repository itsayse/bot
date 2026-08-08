# NightTreeFeller

A Paper/Spigot plugin (1.20.x) with three features:

1. **Sleep to skip night, smoothly** — as soon as one player (configurable) sleeps,
   the night fades out over a few seconds instead of vanilla's instant jump. Since
   Minecraft renders the sun/moon purely from the world time value, the plugin just
   advances time gradually each tick, so the moon sets and the sun rises like a
   real time-lapse animation.
2. **One-chop tree felling** — sneak (shift) + break the *bottom* log of a tree
   and the whole connected trunk (and only that tree) drops at once. Chopping a
   log in the middle of a trunk still just breaks that single block, like
   vanilla. The leaf canopy fast-decays once it loses all log support, no
   matter what order you chop the trunk in.
   **Sneaking is required by default** — this is the main safeguard against
   accidentally felling/decaying something that's part of a build rather than
   a wild tree. Normal breaking (not sneaking) never triggers any of this.
3. **Hourly tellraw announcement** — runs a configurable `/tellraw @a ...`
   message on a repeating timer (default: every 60 minutes).

## Requirements

- Java 17+
- Maven
- A Paper (or Spigot) server, 1.20.x

## Building

```bash
cd nighttreefeller
mvn clean package
```

The compiled plugin will be at `target/nighttreefeller.jar`. Drop that into your
server's `plugins/` folder and restart (or `/reload`, though a restart is safer).

> Network note: this was written and packaged without an internet connection able
> to reach `repo.papermc.io`, so it has **not** been compiled/verified in this
> environment. It's straightforward, standard Bukkit-API code — building it with
> Maven on a machine with internet access should work directly. If you hit a
> compile error, the most likely cause is an API version mismatch; try bumping
> the `paper-api` version in `pom.xml` to match your server version.

## Configuration (`config.yml`, generated on first run)

```yaml
sleep:
  players-required: 1        # how many players must sleep to trigger it
  use-percentage: false      # treat players-required as a % instead of a headcount
  transition-seconds: 6      # how long the smooth sunrise animation takes
  clear-storm: true          # also clear thunderstorms
  start-message: "&e☀ &7The night fades away..."

tree-feller:
  enabled: true
  require-axe: true          # must be holding an axe to fell a tree
  max-logs: 200              # safety cap so log-built structures can't be nuked
  drop-items: true
  play-sound: true
```

## Announcements

```yaml
announcements:
  tellraw:
    enabled: true
    interval-minutes: 60        # every hour
    initial-delay-minutes: 60   # wait 1 hour after startup before the first one
    message: '{"text":"...json..."}'
```

`message` is run verbatim as `tellraw @a <message>` from console every
`interval-minutes`. To change the wording, edit the JSON directly in
`config.yml` — it has to stay valid JSON (test it at minecraft's own
in-game `/tellraw` command, or a JSON validator, if you're not sure).
Set `enabled: false` to turn it off entirely.

## How the tree felling works

When a log block breaks, the plugin checks whether the block *below* it is the
same log type. If it isn't (meaning you chopped the base of the trunk), it does a
flood-fill search that only ever travels from log-block to log-block of the same
wood type (never through leaves or other blocks). Every connected log found is
broken and dropped along with the one you chopped. Because the search can't cross
through leaves, a second tree standing right next to the one you're chopping is
never touched — only the tree whose trunk you cut falls.

## Files

```
nighttreefeller/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/example/nighttreefeller/
    │   ├── NightTreeFellerPlugin.java   (main plugin class)
    │   ├── SleepListener.java           (smooth sleep-skip)
    │   ├── TreeFellerListener.java      (tree felling + leaf decay)
    │   └── AnnouncementScheduler.java   (hourly tellraw announcement)
    └── resources/
        ├── plugin.yml
        └── config.yml
```
