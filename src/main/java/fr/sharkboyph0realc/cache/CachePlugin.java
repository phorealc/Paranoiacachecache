package fr.sharkboyph0realc.cache;

import fr.sharkboyph0realc.cache.command.CacheCommand;
import fr.sharkboyph0realc.cache.listener.GameListener;
import fr.sharkboyph0realc.cache.manager.GameManager;
import fr.sharkboyph0realc.cache.manager.MapManager;
import fr.sharkboyph0realc.cache.manager.MessageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CachePlugin extends JavaPlugin {

    private MessageManager messageManager;
    private MapManager mapManager;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messageManager = new MessageManager(this);
        messageManager.load();

        mapManager = new MapManager(this);
        mapManager.load();

        gameManager = new GameManager(this, messageManager, mapManager);

        CacheCommand cacheCommand = new CacheCommand(this, messageManager, mapManager, gameManager);
        PluginCommand command = getCommand("cache");
        if (command != null) {
            command.setExecutor(cacheCommand);
            command.setTabCompleter(cacheCommand);
        } else {
            getLogger().severe("Commande /cache introuvable dans plugin.yml");
        }

        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);
        getLogger().info("Le plugin cache est active.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            // Nettoie la partie et restaure la worldborder, sans teleporter
            // les joueurs (le serveur est en train de s'arreter).
            gameManager.shutdown();
        }
        if (mapManager != null) {
            mapManager.save();
        }
        getLogger().info("Le plugin cache est desactive.");
    }
}
