package adminqol;

import arc.struct.ObjectSet;
import mindustry.Vars;
import mindustry.gen.Player;

public class Mutes{
    private final ObjectSet<String> muted = new ObjectSet<>();

    public void install(){
        Vars.netServer.admins.addChatFilter((player, text) -> {
            if(player != null && muted.contains(player.uuid())){
                player.sendMessage("[scarlet]You are muted.");
                return null;
            }
            return text;
        });
    }

    public boolean toggle(Player target){
        if(muted.contains(target.uuid())){
            muted.remove(target.uuid());
            return false;
        }
        muted.add(target.uuid());
        return true;
    }

    public boolean isMuted(Player target){
        return muted.contains(target.uuid());
    }

    public void unmute(Player target){
        muted.remove(target.uuid());
    }
}
