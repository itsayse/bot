# NightTreeFeller

A Paper/Spigot plugin (1.20.x) with two features:

1. **Sleep to skip night, smoothly** — as soon as one player (configurable) sleeps,
   the night fades out over a few seconds instead of vanilla's instant jump. Since
   Minecraft renders the sun/moon purely from the world time value, the plugin just
   advances time gradually each tick, so the moon sets and the sun rises like a
   real time-lapse animation.
2. **One-chop tree felling** — break the *bottom* log of a tree and the whole
   connected trunk (and only that tree) drops at once. Chopping a log in the
   middle of a trunk still just breaks that single block, like vanilla. Optionally,
   the connected logs can break in a staggered, top-down sequence instead of all at
   once (see `tree-feller.fell-delay-ticks` in `config.yml`).

## Requirements

- Java 17+
- Maven
- A Paper (or Spigot) server, 1.20.x

## Building

> **Note:** this zip does not include `pom.xml` — carry over your existing one
> (this bundle doesn't change any Maven dependencies or plugin coordinates, so
> your original `pom.xml` still applies as-is).

```bash
cd nighttreefeller
mvn clean package
```

The compiled plugin will be at `target/nighttreefeller.jar`. Drop that into your
server's `plugins/` folder and restart (or `/reload`, though a restart is safer).

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
  max-logs: 200               # safety cap so log-built structures can't be nuked
  drop-items: true
  play-sound: true
  fell-delay-ticks: 2         # 0 = instant, 2-4 = quick collapse, 5-10 = slower/dramatic
```

## How the tree felling works

When a log block breaks, the plugin checks whether the block *below* it is the
same log type. If it isn't (meaning you chopped the base of the trunk), it does a
flood-fill search that only ever travels from log-block to log-block of the same
wood type (never through leaves or other blocks). Every connected log found is
broken and dropped along with the one you chopped. Because the search can't cross
through leaves, a second tree standing right next to the one you're chopping is
never touched — only the tree whose trunk you cut falls.

If `fell-delay-ticks` is greater than 0, the connected logs are sorted top-down
and broken one at a time with that many ticks between each, so the tree visibly
collapses instead of vanishing all at once. Setting it to `0` restores the
original instant behavior.

## Files

```
nighttreefeller/
├── README.md
└── src/main/
    ├── java/com/example/nighttreefeller/
    │   ├── NightTreeFellerPlugin.java   (main plugin class)
    │   ├── SleepListener.java           (smooth sleep-skip)
    │   └── TreeFellerListener.java      (tree felling, now with optional delay)
    └── resources/
        ├── plugin.yml
        └── config.yml
```

(`pom.xml` not included in this zip — see the Building note above.)
