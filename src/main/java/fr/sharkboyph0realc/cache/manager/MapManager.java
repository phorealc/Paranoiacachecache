package fr.sharkboyph0realc.cache.manager;

import fr.sharkboyph0realc.cache.model.MapData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class MapManager {

    private final JavaPlugin plugin;
    private final Map<String, MapData> maps = new HashMap<>();
    private File file;
    private FileConfiguration data;
    private String selectedMap;

    public MapManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Impossible de creer le dossier data du plugin.");
        }

        file = new File(plugin.getDataFolder(), "maps.yml");
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("maps.yml n'a pas pu etre cree.");
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de creer maps.yml", ex);
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
        maps.clear();

        selectedMap = data.getString("selected-map");

        ConfigurationSection mapsSection = data.getConfigurationSection("maps");
        if (mapsSection == null) {
            return;
        }

        for (String key : mapsSection.getKeys(false)) {
            ConfigurationSection section = mapsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            MapData mapData = new MapData(key);
            mapData.setCenter(readLocation(section, "center"));
            mapData.setHunterSpawn(readLocation(section, "hunter-spawn"));
            mapData.setWaitingRoom(readLocation(section, "waiting-room"));
            mapData.setBorderRadius(section.getInt("border-radius", 150));
            maps.put(key.toLowerCase(), mapData);
        }
    }

    public void save() {
        data.set("selected-map", selectedMap);
        data.set("maps", null);

        for (MapData map : maps.values()) {
            String base = "maps." + map.getName();
            data.set(base + ".border-radius", map.getBorderRadius());
            writeLocation(base + ".center", map.getCenter());
            writeLocation(base + ".hunter-spawn", map.getHunterSpawn());
            writeLocation(base + ".waiting-room", map.getWaitingRoom());
        }

        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder maps.yml", ex);
        }
    }

    public boolean createMap(String name) {
        String key = name.toLowerCase();
        if (maps.containsKey(key)) {
            return false;
        }
        maps.put(key, new MapData(name));
        save();
        return true;
    }

    public boolean deleteMap(String name) {
        String key = name.toLowerCase();
        MapData removed = maps.remove(key);
        if (removed == null) {
            return false;
        }

        if (selectedMap != null && selectedMap.equalsIgnoreCase(name)) {
            selectedMap = null;
        }

        save();
        return true;
    }

    public MapData getMap(String name) {
        if (name == null) {
            return null;
        }
        return maps.get(name.toLowerCase());
    }

    public Collection<MapData> getMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public void setSelectedMap(String name) {
        selectedMap = name;
        save();
    }

    public String getSelectedMapName() {
        return selectedMap;
    }

    public MapData getSelectedMap() {
        if (selectedMap == null) {
            return null;
        }
        return getMap(selectedMap);
    }

    private void writeLocation(String path, Location location) {
        if (location == null || location.getWorld() == null) {
            data.set(path, null);
            return;
        }

        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
    }

    private Location readLocation(ConfigurationSection section, String path) {
        ConfigurationSection locSection = section.getConfigurationSection(path);
        if (locSection == null) {
            return null;
        }

        String worldName = locSection.getString("world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = locSection.getDouble("x");
        double y = locSection.getDouble("y");
        double z = locSection.getDouble("z");
        float yaw = (float) locSection.getDouble("yaw");
        float pitch = (float) locSection.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}
