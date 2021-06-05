package io.github.samjirovec.SeaOfSteves.Utils;

import io.github.samjirovec.SeaOfSteves.Model.SeaOfStevesPlayer;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Log
public class PlayerManager {

    private final Map<String, SeaOfStevesPlayer> playerMap;
    private final Dao dao;

    public PlayerManager() {
        this.dao = new Dao();
        playerMap = new HashMap<>();
    }

    public void loadInitialPlayers() {
        playerMap.putAll(dao.retrieveAllPlayers());
        printMap();
    }

    public void addPlayer(SeaOfStevesPlayer player) {
        playerMap.putIfAbsent(player.getPlayer().getName(), player);
        log.info("Saving info, %s");
        dao.saveMap(playerMap);
    }

    public void updatePlayer(SeaOfStevesPlayer player) {
        if (playerMap.containsKey(player.getPlayer().getName())) {
            playerMap.put(player.getPlayer().getName(), player);
        } else {
            addPlayer(player);
        }
    }

    public void addPlayer(Player player) {
        try {
            SeaOfStevesPlayer savePlayer = new SeaOfStevesPlayer(player);
            playerMap.putIfAbsent(player.getName(), savePlayer);
            log.info("Saving info");
            dao.saveMap(playerMap);
        } catch (NullPointerException e) {
            log.info(String.format("Player %s does not exist...?", player));
        }
    }

    public Optional<SeaOfStevesPlayer> getPlayer(String playerName) {
        return Optional.ofNullable(playerMap.get(playerName));
    }

    public void forceSave() {
        log.info("Saving info");
        dao.saveMap(playerMap);
        printMap();
    }

    public void printMap() {
        for (SeaOfStevesPlayer player : playerMap.values()) {
            log.info(String.format("Meta: %s", player.getPlayerMeta().toString()));
        }
    }
}
