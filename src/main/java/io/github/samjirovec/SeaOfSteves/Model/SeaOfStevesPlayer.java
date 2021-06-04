package io.github.samjirovec.SeaOfSteves.Model;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.entity.Player;

@Getter
@Setter
@NoArgsConstructor
public class SeaOfStevesPlayer {

    private transient Player player;

    @Expose private PlayerMeta playerMeta;

}
