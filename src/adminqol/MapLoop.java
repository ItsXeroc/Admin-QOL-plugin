package adminqol;

import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.maps.Map;

public class MapLoop{
    private Map map;

    public void install(){
        Events.on(EventType.GameOverEvent.class, e -> {
            if(map == null) return;
            Map looping = map;
            Core.app.post(() -> {
                if(map == looping){
                    Vars.maps.setNextMapOverride(looping);
                }
            });
        });
    }

    public void set(Map map, boolean loop){
        Vars.maps.setNextMapOverride(map);
        this.map = loop ? map : null;
    }

    public void clear(){
        map = null;
    }

    public Map get(){
        return map;
    }

    public boolean active(){
        return map != null;
    }
}
