# Admin QOL

Mindustry server plugin with admin quality-of-life commands and menus.

Requires Mindustry **146+**. Place `admin-qol.jar` in the server `config/mods` folder and restart.

## Build

```bash
./gradlew jar
```

Output: `build/libs/admin-qol.jar`

## Commands

| Command | Description |
|---------|-------------|
| `/admin` | Open the admin menu |
| `/pause` | Pause or unpause the game |
| `/teams` | Open the team assignment menu |
| `/teams clear` | Clear saved team assignments |
| `/team <team> [player]` | Set a player's team (defaults to yourself) |
| `/kick <player>` | Kick a player |
| `/mute <player>` | Mute or unmute a player |
| `/players` | List online players |
| `/gameover [winner]` | Force a game over |
| `/maps [all/custom/default]` | List maps |
| `/nextmap <name/index> [loop]` | Set the next map; `loop` repeats it after each game |
| `/nextmap loop` | Loop the currently running map |
| `/nextmap clear` | Disable map loop |

All client commands require official admin status.

## Team persistence

Assignments are saved and reapplied on reconnect **only while the map is PvP**.

- Survival / attack / sandbox (co-op): team changes apply immediately, but are not saved across leave/rejoin. Loading a co-op map clears any saved assignments.
- PvP: assignments stick for the current map until game over, map change, or `/teams clear`.

## Server

| Command | Description |
|---------|-------------|
| `aq-teams-clear` | Clear persisted team assignments |
