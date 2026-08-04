package adminqol;

import arc.Events;
import arc.struct.IntIntMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import mindustry.ui.Menus;

public class AdminMenus{
    private static final int PAGE_SIZE = 6;

    private final TeamAssignments assignments;
    private final TeamMenus teamMenus;
    private final Mutes mutes;
    private final MapLoop mapLoop;

    private final IntIntMap pageByAdmin = new IntIntMap();
    private final IntIntMap actionByAdmin = new IntIntMap();

    private static final int ACTION_KICK = 1;
    private static final int ACTION_MUTE = 2;

    private int rootMenu = -1;
    private int playerActionMenu = -1;

    public AdminMenus(TeamAssignments assignments, TeamMenus teamMenus, Mutes mutes, MapLoop mapLoop){
        this.assignments = assignments;
        this.teamMenus = teamMenus;
        this.mutes = mutes;
        this.mapLoop = mapLoop;
    }

    public void register(){
        rootMenu = Menus.registerMenu((player, option) -> {
            if(!player.admin || option < 0) return;

            switch(option){
                case 0 -> togglePause(player);
                case 1 -> teamMenus.openPlayerList(player, true);
                case 2 -> {
                    pageByAdmin.put(player.id, 0);
                    openPlayerAction(player, ACTION_KICK);
                }
                case 3 -> {
                    pageByAdmin.put(player.id, 0);
                    openPlayerAction(player, ACTION_MUTE);
                }
                case 4 -> {
                    assignments.clear();
                    player.sendMessage("[accent]Team assignments cleared.");
                    openRoot(player);
                }
                case 5 -> {
                    if(mapLoop.active()){
                        mapLoop.clear();
                        player.sendMessage("[accent]Map loop disabled.");
                    }else{
                        player.sendMessage("[scarlet]No map loop is active. Use /nextmap <map> loop");
                    }
                    openRoot(player);
                }
                case 6 -> {
                    if(Vars.state.isMenu()){
                        player.sendMessage("[scarlet]No game is running.");
                        return;
                    }
                    Events.fire(new EventType.GameOverEvent(Vars.state.rules.waveTeam));
                    player.sendMessage("[accent]Game over triggered.");
                }
            }
        });

        playerActionMenu = Menus.registerMenu((player, option) -> {
            if(!player.admin || option < 0) return;

            int action = actionByAdmin.get(player.id, 0);
            int page = pageByAdmin.get(player.id, 0);
            Seq<Player> online = onlineOthers(player);
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, online.size);
            int count = end - start;
            boolean hasPrev = page > 0;
            boolean hasNext = end < online.size;

            if(option < count){
                Player target = online.get(start + option);
                if(action == ACTION_KICK){
                    kick(player, target);
                    openPlayerAction(player, action);
                }else if(action == ACTION_MUTE){
                    mute(player, target);
                    openPlayerAction(player, action);
                }
                return;
            }

            int cursor = count;
            if(hasPrev){
                if(option == cursor){
                    pageByAdmin.put(player.id, page - 1);
                    openPlayerAction(player, action);
                    return;
                }
                cursor++;
            }
            if(hasNext){
                if(option == cursor){
                    pageByAdmin.put(player.id, page + 1);
                    openPlayerAction(player, action);
                    return;
                }
                cursor++;
            }
            if(option == cursor){
                openRoot(player);
            }
        });
    }

    public void openRoot(Player admin){
        if(!admin.admin) return;

        String pauseLabel = Vars.state.isPaused() ? "Unpause" : "Pause";
        String persist = TeamAssignments.shouldPersist()
            ? "[accent]PvP persist: on[] (" + assignments.size() + ")"
            : "[lightgray]PvP persist: off (co-op mode)[]";
        String loop = mapLoop.active()
            ? "[accent]Loop: []" + mapLoop.get().name()
            : "[lightgray]Loop: off[]";

        String[][] options = {
            {pauseLabel},
            {"Assign Teams"},
            {"Kick Player"},
            {"Mute / Unmute"},
            {"Clear Team Assignments"},
            {mapLoop.active() ? "Disable Map Loop" : "Map Loop (use /nextmap)"},
            {"Game Over"}
        };

        Call.menu(admin.con, rootMenu, "Admin QOL", persist + "\n" + loop + "\nChoose an action.", options);
    }

    private void openPlayerAction(Player admin, int action){
        actionByAdmin.put(admin.id, action);
        Seq<Player> online = onlineOthers(admin);
        if(online.isEmpty()){
            admin.sendMessage("[scarlet]No other players online.");
            openRoot(admin);
            return;
        }

        int page = pageByAdmin.get(admin.id, 0);
        int maxPage = Math.max(0, (online.size - 1) / PAGE_SIZE);
        if(page > maxPage){
            page = maxPage;
            pageByAdmin.put(admin.id, page);
        }

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, online.size);

        Seq<String[]> rows = new Seq<>();
        for(int i = start; i < end; i++){
            Player p = online.get(i);
            String label = p.coloredName();
            if(action == ACTION_MUTE && mutes.isMuted(p)){
                label = "[scarlet](muted) []" + label;
            }
            rows.add(new String[]{label});
        }

        boolean hasPrev = page > 0;
        boolean hasNext = end < online.size;
        if(hasPrev && hasNext){
            rows.add(new String[]{"< Prev", "Next >"});
        }else if(hasPrev){
            rows.add(new String[]{"< Prev"});
        }else if(hasNext){
            rows.add(new String[]{"Next >"});
        }

        rows.add(new String[]{"< Back"});

        String title = action == ACTION_KICK ? "Kick Player" : "Mute / Unmute";
        Call.menu(admin.con, playerActionMenu, title, "Select a player.\nPage " + (page + 1) + "/" + (maxPage + 1), rows.toArray(String[].class));
    }

    private void togglePause(Player admin){
        if(Vars.state.isMenu()){
            admin.sendMessage("[scarlet]No game is running.");
            openRoot(admin);
            return;
        }
        boolean pause = !Vars.state.isPaused();
        Vars.state.set(pause ? GameState.State.paused : GameState.State.playing);
        Call.sendMessage("[accent]Game " + (pause ? "paused" : "unpaused") + " by " + admin.coloredName() + "[].");
        openRoot(admin);
    }

    private void kick(Player admin, Player target){
        if(target.admin && target != admin){
            admin.sendMessage("[scarlet]Cannot kick another admin.");
            return;
        }
        Call.sendMessage("[scarlet]" + target.coloredName() + "[scarlet] was kicked by " + admin.coloredName() + "[].");
        target.kick(Packets.KickReason.kick);
    }

    private void mute(Player admin, Player target){
        if(target.admin && target != admin){
            admin.sendMessage("[scarlet]Cannot mute another admin.");
            return;
        }
        boolean nowMuted = mutes.toggle(target);
        admin.sendMessage("[accent]" + target.coloredName() + (nowMuted ? "[accent] muted." : "[accent] unmuted."));
        target.sendMessage(nowMuted ? "[scarlet]You have been muted." : "[accent]You have been unmuted.");
    }

    private static Seq<Player> onlineOthers(Player self){
        Seq<Player> list = new Seq<>();
        for(Player p : Groups.player){
            if(p != self) list.add(p);
        }
        list.sort((a, b) -> a.plainName().compareToIgnoreCase(b.plainName()));
        return list;
    }
}
