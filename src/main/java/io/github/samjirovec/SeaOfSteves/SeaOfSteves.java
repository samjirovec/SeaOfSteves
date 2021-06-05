package io.github.samjirovec.SeaOfSteves;

import io.github.samjirovec.SeaOfSteves.Listeners.PlayerListener;
import io.github.samjirovec.SeaOfSteves.Utils.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import static io.github.samjirovec.SeaOfSteves.Utils.Logging.logPluginLoaded;

public class SeaOfSteves extends JavaPlugin {

    private final PlayerManager playerManager;

    public SeaOfSteves() {
        this.playerManager = new PlayerManager();
    }

    @Override
    public void onEnable() {
        // Begin Registering Event Listeners
        getLogger().info("Registering Event Listeners");
        getServer().getPluginManager().registerEvents(new PlayerListener(this.playerManager), this);

        // Begin Registering Commands
        getLogger().info("Registering Commands");

        // Loading in players
        playerManager.loadInitialPlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerManager.addPlayer(player);
        }
        playerManager.forceSave();

        //Loaded Successfully
        logPluginLoaded();
    }
    @Override
    public void onDisable() {
        playerManager.forceSave();
    }

}
