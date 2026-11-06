package adminqol;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.Packets;

public class Commands{
    private final TeamAssignments assignments;
    private final TeamMenus teamMenus;
    private final AdminMenus adminMenus;
    private final Mutes mutes;
    private final MapLoop mapLoop;

    public Commands(TeamAssignments assignments, TeamMenus teamMenus, AdminMenus adminMenus, Mutes mutes, MapLoop mapLoop){
        this.assignments = assignments;
        this.teamMenus = teamMenus;
        this.adminMenus = adminMenus;
        this.mutes = mutes;
        this.mapLoop = mapLoop;
    }

    public void registerClient(CommandHandler handler){
        handler.<Player>register("admin", "Open the admin menu.", (args, player) -> {
            if(!requireAdmin(player)) return;
            adminMenus.openRoot(player);
        });

        handler.<Player>register("pause", "Pause or unpause the game.", (args, player) -> {
            if(!requireAdmin(player)) return;
            if(Vars.state.isMenu()){
                player.sendMessage("[scarlet]No game is running.");
                return;
            }

            boolean pause = !Vars.state.isPaused();
            Vars.state.set(pause ? GameState.State.paused : GameState.State.playing);
            Call.sendMessage("[accent]Game " + (pause ? "paused" : "unpaused") + " by " + player.coloredName() + "[].");
        });

        handler.<Player>register("teams", "[clear]", "Open team menu, or clear assignments.", (args, player) -> {
            if(!requireAdmin(player)) return;
            if(args.length > 0 && args[0].equalsIgnoreCase("clear")){
                assignments.clear();
                player.sendMessage("[accent]Team assignments cleared.");
                return;
            }
            teamMenus.openPlayerList(player);
        });

        handler.<Player>register("team", "<team> [player...]", "Set a player's team (or your own).", (args, player) -> {
            if(!requireAdmin(player)) return;

            Team team = resolveTeam(args[0]);
            if(team == null){
                player.sendMessage("[scarlet]Unknown team: []" + args[0]);
                return;
            }

            Player target = player;
            if(args.length > 1){
                target = PlayerLookup.find(args[1]);
                if(target == null){
                    player.sendMessage("[scarlet]Player not found.");
                    return;
                }
            }

            assignments.assign(target, team);
            String note = TeamAssignments.shouldPersist() ? "" : " [lightgray](not saved: not PvP)[]";
            player.sendMessage("[accent]Set []" + target.coloredName() + "[accent] to [#" + team.color + "]" + team.name + "[]." + note);
            if(target != player){
                target.sendMessage("[accent]Your team was set to [#" + team.color + "]" + team.name + "[].");
            }
        });

        handler.<Player>register("kick", "<player...>", "Kick a player.", (args, player) -> {
            if(!requireAdmin(player)) return;

            Player target = PlayerLookup.find(args[0]);
            if(target == null){
                player.sendMessage("[scarlet]Player not found.");
                return;
            }
            if(target.admin && target != player){
                player.sendMessage("[scarlet]Cannot kick another admin.");
                return;
            }

            Call.sendMessage("[scarlet]" + target.coloredName() + "[scarlet] was kicked by " + player.coloredName() + "[].");
            target.kick(Packets.KickReason.kick);
        });

        handler.<Player>register("mute", "<player...>", "Mute or unmute a player.", (args, player) -> {
            if(!requireAdmin(player)) return;

            Player target = PlayerLookup.find(args[0]);
            if(target == null){
                player.sendMessage("[scarlet]Player not found.");
                return;
            }
            if(target.admin && target != player){
                player.sendMessage("[scarlet]Cannot mute another admin.");
                return;
            }

            boolean nowMuted = mutes.toggle(target);
            player.sendMessage("[accent]" + target.coloredName() + (nowMuted ? "[accent] muted." : "[accent] unmuted."));
            target.sendMessage(nowMuted ? "[scarlet]You have been muted." : "[accent]You have been unmuted.");
        });

        handler.<Player>register("players", "List online players.", (args, player) -> {
            if(!requireAdmin(player)) return;

            if(Groups.player.size() == 0){
                player.sendMessage("[scarlet]No players online.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[accent]Players (").append(Groups.player.size()).append("):[]\n");
            for(Player p : Groups.player){
                Team t = p.team();
                String uuid = p.uuid();
                String shortId = uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
                sb.append("[#").append(t.color).append(']').append(t.name).append("[] ")
                    .append(p.coloredName());
                if(mutes.isMuted(p)) sb.append(" [scarlet]muted[]");
                sb.append(" [lightgray]").append(shortId).append("[]\n");
            }
            player.sendMessage(sb.toString());
        });

        handler.<Player>register("gameover", "[winner]", "Force a game over.", (args, player) -> {
            if(!requireAdmin(player)) return;
            if(Vars.state.isMenu()){
                player.sendMessage("[scarlet]No game is running.");
                return;
            }

            Team winner = Vars.state.rules.waveTeam;
            if(args.length > 0){
                Team resolved = resolveTeam(args[0]);
                if(resolved == null){
                    player.sendMessage("[scarlet]Unknown team: []" + args[0]);
                    return;
                }
                winner = resolved;
            }

            Events.fire(new EventType.GameOverEvent(winner));
            player.sendMessage("[accent]Game over triggered.");
        });

        handler.<Player>register("maps", "[all/custom/default]", "List available maps.", (args, player) -> {
            if(!requireAdmin(player)) return;

            Seq<Map> all = Vars.maps.all().copy();
            all.sort((a, b) -> a.plainName().compareToIgnoreCase(b.plainName()));

            String filter = args.length == 0 ? "custom" : args[0].toLowerCase();
            Seq<Map> maps = new Seq<>();
            switch(filter){
                case "all" -> maps.addAll(all);
                case "default" -> {
                    for(Map map : all){
                        if(Vars.maps.defaultMaps().contains(map)) maps.add(map);
                    }
                }
                default -> {
                    for(Map map : all){
                        if(Vars.maps.customMaps().contains(map)) maps.add(map);
                    }
                }
            }

            if(maps.isEmpty()){
                player.sendMessage("[scarlet]No maps found.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[accent]Maps (").append(maps.size).append("):[]\n");
            for(Map map : maps){
                sb.append("[lightgray]").append(all.indexOf(map)).append("[] ").append(map.name()).append('\n');
            }
            player.sendMessage(sb.toString());
        });

        handler.<Player>register("nextmap", "<name/index/clear...>", "Set the next map. Add 'loop' to repeat it after each game.", (args, player) -> {
            if(!requireAdmin(player)) return;

            String raw = args[0].trim();
            boolean loop = false;
            String[] parts = raw.split("\\s+");
            if(parts.length >= 2 && parts[parts.length - 1].equalsIgnoreCase("loop")){
                loop = true;
                raw = raw.substring(0, raw.length() - parts[parts.length - 1].length()).trim();
            }

            if(raw.equalsIgnoreCase("clear") || raw.equalsIgnoreCase("off")){
                mapLoop.clear();
                player.sendMessage("[accent]Map loop disabled.");
                return;
            }

            if(raw.equalsIgnoreCase("loop")){
                if(Vars.state.map == null || Vars.state.isMenu()){
                    player.sendMessage("[scarlet]No map is currently running.");
                    return;
                }
                mapLoop.set(Vars.state.map, true);
                player.sendMessage("[accent]Looping current map: []" + Vars.state.map.name() + "[].");
                return;
            }

            Map map = resolveMap(raw);
            if(map == null){
                player.sendMessage("[scarlet]Map not found.");
                return;
            }

            mapLoop.set(map, loop);
            if(loop){
                player.sendMessage("[accent]Next map set to []" + map.name() + "[accent] (looping).");
            }else{
                player.sendMessage("[accent]Next map set to []" + map.name() + "[].");
            }
        });
    }

    public void registerServer(CommandHandler handler){
        handler.register("aq-teams-clear", "Clear persisted team assignments.", args -> {
            assignments.clear();
            Log.info("Admin QOL: team assignments cleared.");
        });
    }

    private static boolean requireAdmin(Player player){
        if(player.admin) return true;
        player.sendMessage("[scarlet]Admin only.");
        return false;
    }

    private static Team resolveTeam(String raw){
        String name = Strings.stripColors(raw).trim();
        if(name.isEmpty()) return null;

        if(Strings.canParseInt(name)){
            int id = Strings.parseInt(name);
            if(id >= 0 && id < Team.all.length) return Team.get(id);
        }

        for(Team team : Team.all){
            if(team == null) continue;
            if(team.name.equalsIgnoreCase(name)) return team;
        }
        return null;
    }

    private static Map resolveMap(String raw){
        String query = Strings.stripColors(raw).trim().replace('_', ' ');
        if(query.isEmpty()) return null;

        Seq<Map> sorted = Vars.maps.all().copy();
        sorted.sort((a, b) -> a.plainName().compareToIgnoreCase(b.plainName()));

        if(Strings.canParsePositiveInt(query)){
            int index = Strings.parseInt(query);
            if(index >= 0 && index < sorted.size) return sorted.get(index);
            return null;
        }

        Map exact = sorted.find(map -> map.plainName().replace('_', ' ').equalsIgnoreCase(query));
        if(exact != null) return exact;

        return sorted.find(map -> map.plainName().replace('_', ' ').toLowerCase().contains(query.toLowerCase()));
    }
}
