package fr.sharkboyph0realc.cache.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.logging.Level;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class MessageManager {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Impossible de creer le dossier data du plugin.");
        }

        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            messages = new YamlConfiguration();
            setDefaults(messages);
            try {
                messages.save(file);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder messages.yml", ex);
            }
        }

        messages = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        if (file == null) {
            load();
            return;
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    private void setDefaults(FileConfiguration cfg) {
        cfg.set("prefix", "&8[&6Cache&8] &r");
        cfg.set("no-permission", "&cTu n'as pas la permission.");
        cfg.set("player-only", "&cCette commande est reservee aux joueurs.");
        cfg.set("map-created", "&aMap %map% creee.");
        cfg.set("map-deleted", "&eMap %map% supprimee.");
        cfg.set("map-selected", "&aMap %map% selectionnee.");
        cfg.set("map-updated", "&aMap %map% mise a jour.");
        cfg.set("hunter-added", "&a%player% est maintenant chasseur.");
        cfg.set("hunters-cleared", "&eListe des chasseurs videe.");
        cfg.set("game-started", "&aLa partie commence.");
        cfg.set("game-stopped", "&cLa partie a ete arretee.");
        cfg.set("winner-hunters", "&6Les chasseurs ont gagne.");
        cfg.set("winner-hiders", "&aLes joueurs caches ont gagne.");
        cfg.set("winner-draw", "&eFin de partie.");
        cfg.set("hider-found", "&c%player% a ete trouve.");
        cfg.set("time-up-glow", "&eTemps ecoule sans joueur trouve: tout le monde brille pendant 30 secondes.");
        cfg.set("reloaded", "&aConfiguration rechargee.");
        cfg.set("invalid-map", "&cMap introuvable.");
        cfg.set("invalid-player", "&cJoueur introuvable.");
        cfg.set("invalid-number", "&cValeur numerique invalide.");
        cfg.set("no-selected-map", "&cAucune map selectionnee.");
        cfg.set("map-not-ready", "&cLa map selectionnee n'est pas prete (centre/spawn/waiting/border).");
        cfg.set("state-running", "&cUne partie est deja en cours.");
        cfg.set("state-idle", "&cAucune partie en cours.");
        cfg.set("hunter-none", "&cAucun chasseur selectionne.");
        cfg.set("hider-pearl-used", "&cTa perle de fuite est deja utilisee.");
        cfg.set("hunter-pearl-cooldown", "&cPerle en recharge: %seconds%s");
        cfg.set("player-disconnected-found", "&e%player% s'est deconnecte et est compte comme trouve.");
    }

    public String get(String path) {
        String prefix = colorize(messages.getString("prefix", ""));
        String raw = messages.getString(path, path);
        return prefix + colorize(raw);
    }

    public String getPlain(String path) {
        return colorize(messages.getString(path, path));
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        sender.sendMessage(message);
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }

    private String colorize(String text) {
        if (text == null) {
            return "";
        }
        return LegacyComponentSerializer.legacyAmpersand().serialize(
            LegacyComponentSerializer.legacyAmpersand().deserialize(text)
        );
    }
}
