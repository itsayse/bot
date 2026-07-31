# KeyAuth (Paper plugin)

Gates new players behind a Discord-bot-issued access key, then lets them set
a password for future logins. Periodically re-checks with the bot in case a
key gets revoked or expires mid-session.

## Flow

1. **New player joins** → prompted for `/key <code> <discordUserId>`.
2. Plugin calls the bot's key API to redeem the key **and** link the given
   Discord user id to it. On success, the key is permanently bound to that
   player's UUID (one key = one player), and the bot immediately grants that
   Discord account the configured "key role" (set from `/panel` → Server
   Keys, or `/key` commands, on the Discord side).
3. Player sets a password: `/register <password> <confirm>`.
4. **Returning player joins** → prompted for `/login <password>`.
5. On successful login (and every `key-check-interval-minutes`, default 5),
   the plugin re-checks the bound key with the bot. If it's expired or been
   revoked, the player is frozen in place — can't move, break/place blocks,
   interact, take/deal damage, chat, or run most commands — until they fix it
   with `/renewkey <newCode>`. If they never fix it within
   `locked-timeout-minutes` (default 10), they're kicked as a last resort.
   The bot strips the Discord role from their linked account around the same
   time (either immediately, on that check, or via the bot's periodic sweep).
6. **Changed Discord accounts?** An authenticated player can run
   `/changeuser <newDiscordUserId>` at any time. The bot removes the role
   from the old linked account and grants it to the new one (if the key is
   still valid).

While unauthenticated, players can't move, break/place blocks, chat, take
damage, or run any command besides `/key`, `/register`, `/login`.
`/changeuser` only works once logged in.

## Build

Requires Maven and **Java 25** (Paper 26.x requires JDK 25, both to build against and to run) plus internet access to `repo.papermc.io`.

```
mvn clean package
```

Output: `target/keyauth.jar` → drop into your server's `plugins/` folder.

**Note:** The `pom.xml` is pinned to Paper `26.1.2` (`[26.1.2.build,)`). If you
run a different Paper version, update that version string to match — check
https://papermc.io/downloads/paper for what you're running.

## Configure

Edit `plugins/KeyAuth/config.yml` after first run:

```yaml
api:
  base-url: "http://127.0.0.1:8787"   # where the Discord bot's key API is listening
  secret: "..."                        # must exactly match MC_API_SECRET in the bot's .env
```

If the Minecraft server and Discord bot run on different machines, replace
`127.0.0.1` with the bot's real address, and make sure the bot's
`MC_API_PORT` (default 8787) is reachable from the Minecraft server —
firewall it to only allow that one connection, since it grants server
access.

## Generating keys

On the Discord side (all admin-only):
- `/key generate [days] [send_to]` — makes a key; optionally DMs it straight
  to a member with instructions.
- `/giftkey <user> [days]` — makes a key and delivers it as a themed "gift"
  DM (spoiler-tagged), plus a public announcement that doesn't reveal it.
- `/key extend <key> [days]`, `/key shorten <key> [days]`, `/key remove <key>`
  (revokes + strips the role immediately), `/key delete <key>` (permanently
  deletes the record), `/key info <key>`, `/key list`.
- Or do all of the above from `/panel` → **🔑 Server Keys**, which also lets
  you set the Discord role granted while a key is valid.

Keys are 32 characters and expire 30 days after creation by default
(configurable per-key at generation time, and extendable/shortenable later).
Once expired or removed, the linked Discord role is stripped automatically
(both on the player's next in-game check, and via a background sweep every
few minutes on the bot side).
