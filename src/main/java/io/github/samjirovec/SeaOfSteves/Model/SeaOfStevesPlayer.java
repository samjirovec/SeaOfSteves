package io.github.samjirovec.SeaOfSteves.Model;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class SeaOfStevesPlayer {

    private transient Player player;

    @Expose private PlayerMeta playerMeta;

    public SeaOfStevesPlayer(Player player) {
        this.player = player;
        this.playerMeta = new PlayerMeta(Emissary.NONE, 0);
    }

    public SeaOfStevesPlayer(Player player, Emissary emissary) {
        this.player = player;
        this.playerMeta = new PlayerMeta(emissary, 0);
    }

}
