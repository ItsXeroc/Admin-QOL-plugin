package adminqol;

import arc.struct.Seq;
import arc.util.Strings;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public final class PlayerLookup{
    private PlayerLookup(){}

    public static Player find(String raw){
        if(raw == null || raw.isEmpty()) return null;

        String query = Strings.stripColors(raw).trim();
        if(query.isEmpty()) return null;

        Player exact = Groups.player.find(p -> p.plainName().equalsIgnoreCase(query));
        if(exact != null) return exact;

        String lower = query.toLowerCase();
        Seq<Player> matches = new Seq<>();
        for(Player p : Groups.player){
            if(p.plainName().toLowerCase().contains(lower)){
                matches.add(p);
            }
        }
        return matches.size == 1 ? matches.first() : null;
    }
}
