package fr.sharkboyph0realc.cache.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

/**
 * Tout sort en Component Adventure (API native de Paper).
 * On ne renvoie plus jamais de String coloree : c'est ce qui cassait les couleurs.
 */
public final class MessageManager {

    private static final String FILE_NAME = "messages.yml";

    /** Lit les codes '&' du fichier (et les codes hex &#rrggbb). */
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
        .character('&')
        .hexCharacter('#')
        .hexColors()
        .build();

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        messages = YamlConfiguration.loadConfiguration(file);
        applyJarDefaults();
    }

    public void reload() {
        load();
    }

    /** Le messages.yml du jar sert de valeurs par defaut : aucune cle ne peut manquer. */
    private void applyJarDefaults() {
        try (InputStream in = plugin.getResource(FILE_NAME)) {
            if (in == null) {
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                messages.setDefaults(YamlConfiguration.loadConfiguration(reader));
                messages.options().copyDefaults(true);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Impossible de lire les messages par defaut du jar", ex);
        }
    }

    // ------------------------------------------------------------ lecture

    /** Composant sans prefixe (utile pour les titres). */
    public Component component(String path) {
        return colorize(messages.getString(path, path));
    }

    public Component component(String path, Map<String, String> placeholders) {
        return colorize(replace(messages.getString(path, path), placeholders));
    }

    /** Composant prefixe, pour le chat. */
    public Component prefixed(String path) {
        return prefix().append(component(path));
    }

    public Component prefixed(String path, Map<String, String> placeholders) {
        return prefix().append(component(path, placeholders));
    }

    public Component prefix() {
        return colorize(messages.getString("prefix", ""));
    }

    /** Colore un texte libre ecrit en '&' et le prefixe. */
    public Component rawPrefixed(String legacyText) {
        return prefix().append(colorize(legacyText));
    }

    // ------------------------------------------------------------- envoi

    public void send(CommandSender sender, String path) {
        sender.sendMessage(prefixed(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(prefixed(path, placeholders));
    }

    /** Envoie un texte libre en '&' avec le prefixe. */
    public void sendRaw(CommandSender sender, String legacyText) {
        sender.sendMessage(rawPrefixed(legacyText));
    }

    /** Envoie un texte libre en '&' sans prefixe (lignes d'aide). */
    public void sendRawNoPrefix(CommandSender sender, String legacyText) {
        sender.sendMessage(colorize(legacyText));
    }

    // ------------------------------------------------------------ interne

    private String replace(String message, Map<String, String> placeholders) {
        if (message == null) {
            return "";
        }
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return AMPERSAND.deserialize(text);
    }
}
