package adminqol;

import arc.struct.IntIntMap;
import arc.struct.IntMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.Menus;

public class TeamMenus{
    private static final int PAGE_SIZE = 6;

    private final TeamAssignments assignments;
    private final IntIntMap pageByAdmin = new IntIntMap();
    private final IntIntMap targetByAdmin = new IntIntMap();
    private final IntMap<Seq<Team>> teamsByAdmin = new IntMap<>();
    private final IntMap<Boolean> fromAdminByPlayer = new IntMap<>();

    private AdminMenus adminMenus;
    private int playerMenu = -1;
    private int teamMenu = -1;

    public TeamMenus(TeamAssignments assignments){
        this.assignments = assignments;
    }

    public void setAdminMenus(AdminMenus adminMenus){
        this.adminMenus = adminMenus;
    }

    public void register(){
        playerMenu = Menus.registerMenu((player, option) -> {
            if(!player.admin || option < 0) return;

            int page = pageByAdmin.get(player.id, 0);
            Seq<Player> online = onlinePlayers();
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, online.size);
            int count = end - start;
            boolean hasPrev = page > 0;
            boolean hasNext = end < online.size;
            boolean fromAdmin = fromAdminByPlayer.get(player.id, false);

            if(option < count){
                Player target = online.get(start + option);
                targetByAdmin.put(player.id, target.id);
                openTeamPicker(player, target);
                return;
            }

            int cursor = count;
            if(hasPrev){
                if(option == cursor){
                    pageByAdmin.put(player.id, page - 1);
                    openPlayerList(player, fromAdmin);
                    return;
                }
                cursor++;
            }
            if(hasNext){
                if(option == cursor){
                    pageByAdmin.put(player.id, page + 1);
                    openPlayerList(player, fromAdmin);
                    return;
                }
                cursor++;
            }
            if(fromAdmin && option == cursor && adminMenus != null){
                adminMenus.openRoot(player);
            }
        });

        teamMenu = Menus.registerMenu((player, option) -> {
            if(!player.admin || option < 0) return;

            Seq<Team> teams = teamsByAdmin.get(player.id);
            if(teams == null) return;

            if(option == teams.size){
                openPlayerList(player, fromAdminByPlayer.get(player.id, false));
                return;
            }

            if(option > teams.size) return;

            int targetId = targetByAdmin.get(player.id, Integer.MIN_VALUE);
            if(targetId == Integer.MIN_VALUE) return;

            Player target = Groups.player.getByID(targetId);
            if(target == null){
                player.sendMessage("[scarlet]Player is no longer online.");
                return;
            }

            Team team = teams.get(option);
            assignments.assign(target, team);
            player.sendMessage("[accent]Set []" + target.coloredName() + "[accent] to [#" + team.color + "]" + team.name + "[].");
            if(target != player){
                target.sendMessage("[accent]Your team was set to [#" + team.color + "]" + team.name + "[].");
            }
            openPlayerList(player, fromAdminByPlayer.get(player.id, false));
        });
    }

    public void openPlayerList(Player admin){
        openPlayerList(admin, false);
    }

    public void openPlayerList(Player admin, boolean fromAdmin){
        if(!admin.admin) return;
        fromAdminByPlayer.put(admin.id, fromAdmin);

        Seq<Player> online = onlinePlayers();
        if(online.isEmpty()){
            admin.sendMessage("[scarlet]No players online.");
            if(fromAdmin && adminMenus != null) adminMenus.openRoot(admin);
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
            Team t = p.team();
            rows.add(new String[]{"[#" + t.color + "]" + p.coloredName() + "[]"});
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

        if(fromAdmin){
            rows.add(new String[]{"< Back"});
        }

        Call.menu(admin.con, playerMenu, "Assign Team", "Select a player.\nPage " + (page + 1) + "/" + (maxPage + 1), rows.toArray(String[].class));
    }

    private void openTeamPicker(Player admin, Player target){
        Seq<Team> teams = buildSelectable();
        teamsByAdmin.put(admin.id, teams);

        String[][] options = new String[teams.size + 1][1];
        for(int i = 0; i < teams.size; i++){
            Team team = teams.get(i);
            options[i][0] = "[#" + team.color + "]" + team.name + "[]";
        }
        options[teams.size][0] = "< Back";
        Call.menu(admin.con, teamMenu, "Team for " + target.plainName(), "Choose a team.", options);
    }

    private static Seq<Team> buildSelectable(){
        ObjectSet<Team> set = new ObjectSet<>();
        for(Team team : Team.baseTeams){
            if(team != null) set.add(team);
        }

        if(Vars.state != null && Vars.state.teams != null){
            for(Teams.TeamData data : Vars.state.teams.getActive()){
                if(data != null && data.team != null) set.add(data.team);
            }
        }

        for(Player p : Groups.player){
            set.add(p.team());
        }

        Seq<Team> list = set.toSeq();
        list.sort((a, b) -> Integer.compare(a.id, b.id));
        return list;
    }

    private static Seq<Player> onlinePlayers(){
        Seq<Player> list = new Seq<>();
        Groups.player.copy(list);
        list.sort((a, b) -> a.plainName().compareToIgnoreCase(b.plainName()));
        return list;
    }
}
