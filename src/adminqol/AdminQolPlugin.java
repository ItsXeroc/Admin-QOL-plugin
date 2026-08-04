package adminqol;

import arc.Events;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Plugin;

public class AdminQolPlugin extends Plugin{
    private final TeamAssignments assignments = new TeamAssignments();
    private final Mutes mutes = new Mutes();
    private final MapLoop mapLoop = new MapLoop();
    private final TeamMenus teamMenus = new TeamMenus(assignments);
    private final AdminMenus adminMenus = new AdminMenus(assignments, teamMenus, mutes, mapLoop);
    private final Commands commands = new Commands(assignments, teamMenus, adminMenus, mutes, mapLoop);
    private String lastMapName;

    @Override
    public void init(){
        teamMenus.setAdminMenus(adminMenus);
        assignments.load();
        assignments.installAssigner();
        mutes.install();
        mapLoop.install();
        teamMenus.register();
        adminMenus.register();

        Events.on(EventType.GameOverEvent.class, e -> assignments.clear());
        Events.on(EventType.WorldLoadEvent.class, e -> {
            if(!Vars.net.server()) return;
            String mapName = Vars.state.map == null ? "" : Vars.state.map.name();
            assignments.onWorldLoad(lastMapName, mapName);
            lastMapName = mapName;
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        commands.registerClient(handler);
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        commands.registerServer(handler);
    }
}
