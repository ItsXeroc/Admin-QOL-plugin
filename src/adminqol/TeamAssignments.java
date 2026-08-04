package adminqol;

import arc.Core;
import arc.files.Fi;
import arc.struct.ObjectIntMap;
import arc.util.Log;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.game.Team;
import mindustry.gen.Player;

public class TeamAssignments{
    private final ObjectIntMap<String> byUuid = new ObjectIntMap<>();
    private NetServer.TeamAssigner previous;

    public void installAssigner(){
        previous = Vars.netServer.assigner;
        Vars.netServer.assigner = (player, players) -> {
            if(shouldPersist()){
                int id = byUuid.get(player.uuid(), Integer.MIN_VALUE);
                if(id != Integer.MIN_VALUE){
                    return Team.get(id);
                }
            }
            return previous.assign(player, players);
        };
    }

    public void assign(Player player, Team team){
        player.team(team);
        if(shouldPersist()){
            byUuid.put(player.uuid(), team.id);
            save();
        }
    }

    public Team get(String uuid){
        int id = byUuid.get(uuid, Integer.MIN_VALUE);
        return id == Integer.MIN_VALUE ? null : Team.get(id);
    }

    public boolean has(String uuid){
        return byUuid.containsKey(uuid);
    }

    public int size(){
        return byUuid.size;
    }

    public void clear(){
        if(byUuid.size == 0) return;
        byUuid.clear();
        save();
    }

    public void onWorldLoad(String previousMapName, String mapName){
        if(!shouldPersist()){
            clear();
            return;
        }
        if(previousMapName != null && !previousMapName.equals(mapName)){
            clear();
        }
    }

    public static boolean shouldPersist(){
        return Vars.state != null && Vars.state.rules != null && Vars.state.rules.pvp;
    }

    public void load(){
        Fi file = file();
        if(!file.exists()) return;

        try{
            JsonValue root = new JsonReader().parse(file);
            byUuid.clear();
            for(JsonValue child = root.child; child != null; child = child.next){
                byUuid.put(child.name, child.asInt());
            }
        }catch(Throwable t){
            Log.err("Admin QOL: failed to load team assignments", t);
        }
    }

    public void save(){
        Fi file = file();
        file.parent().mkdirs();

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for(ObjectIntMap.Entry<String> e : byUuid.entries()){
            if(!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.key)).append("\":").append(e.value);
        }
        sb.append('}');
        file.writeString(sb.toString(), false);
    }

    private Fi file(){
        return Core.settings.getDataDirectory().child("mods").child("admin-qol").child("assignments.json");
    }

    private static String escape(String s){
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
