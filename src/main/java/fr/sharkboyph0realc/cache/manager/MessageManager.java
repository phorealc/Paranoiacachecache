package fr.sharkboyph0realc.cache.manager;

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

public final class MessageManager {

    private static final String FILE_NAME = "messages.yml";

    /** Format utilise dans messages.yml (&c, &a, ...). */
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    /** Format reellement interprete par le client Minecraft. */
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            // Copie le messages.yml du jar (cree aussi le dossier data au besoin).
            plugin.saveResource(FILE_NAME, false);
        }

        messages = YamlConfiguration.loadConfiguration(file);
        applyJarDefaults();
    }

    public void reload() {
        load();
    }

    /**
     * Le fichier du jar sert de valeurs par defaut : une cle ajoutee dans une
     * future version reste lisible meme si le messages.yml du serveur est ancien.
     */
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
        sender.sendMessage(format(path, placeholders));
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }

    /**
     * Convertit les codes '&' du fichier en codes section.
     * L'ancienne version re-serialisait en '&' : les joueurs voyaient "&cTexte".
     */
    private String colorize(String text) {
        if (text == null) {
            return "";
        }
        return SECTION.serialize(AMPERSAND.deserialize(text));
    }
}
