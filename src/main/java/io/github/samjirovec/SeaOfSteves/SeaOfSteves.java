package io.github.samjirovec.SeaOfSteves;

import org.bukkit.plugin.java.JavaPlugin;

import static io.github.samjirovec.SeaOfSteves.Utils.Logging.logPluginLoaded;

public class SeaOfSteves extends JavaPlugin {
    @Override
    public void onEnable() {
        // Begin Registering Event Listeners
        getLogger().info("Registering Event Listeners");

        // Begin Registering Commands
        getLogger().info("Registering Commands");;

        //Loaded Successfully
        logPluginLoaded();
    }
    @Override
    public void onDisable() {
        getLogger().info("onDisable is called!");
    }

}
