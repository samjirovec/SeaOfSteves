package io.github.samjirovec.SeaOfSteves.Model;

import com.google.gson.annotations.Expose;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.entity.Player;

@Getter
@Setter
@Builder
public class SeaOfStevesPlayer {

    private transient Player player;

    @Expose private PlayerMeta playerMeta;

}
