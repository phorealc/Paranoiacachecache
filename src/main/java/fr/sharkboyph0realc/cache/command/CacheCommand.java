package fr.sharkboyph0realc.cache.command;

import fr.sharkboyph0realc.cache.manager.GameManager;
import fr.sharkboyph0realc.cache.manager.MapManager;
import fr.sharkboyph0realc.cache.manager.MessageManager;
import fr.sharkboyph0realc.cache.model.MapData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CacheCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "cache.admin";

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final MapManager mapManager;
    private final GameManager gameManager;

    public CacheCommand(JavaPlugin plugin, MessageManager messages, MapManager mapManager, GameManager gameManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.mapManager = mapManager;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "start" -> {
                gameManager.startGame(sender);
                return true;
            }
            case "stop" -> {
                gameManager.stopGame(sender);
                return true;
            }
            case "reload" -> {
                plugin.reloadConfig();
                messages.reload();
                mapManager.load();
                gameManager.reloadRuntimeData();
                messages.send(sender, "reloaded");
                return true;
            }
            case "select" -> {
                if (args.length < 2) {
                    messages.sendRawNoPrefix(sender, "&7/" + label + " select <map>");
                    return true;
                }
                return handleSelect(sender, args[1]);
            }
            case "chasseur", "hunter" -> {
                return handleHunter(sender, label, args);
            }
            case "time" -> {
                return handleTime(sender, label, args);
            }
            case "map" -> {
                return handleMap(sender, label, args);
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private boolean handleSelect(CommandSender sender, String mapName) {
        MapData map = mapManager.getMap(mapName);
        if (map == null) {
            messages.send(sender, "invalid-map");
            return true;
        }

        mapManager.setSelectedMap(map.getName());
        messages.send(sender, "map-selected", Map.of("map", map.getName()));
        return true;
    }

    private boolean handleHunter(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            messages.sendRawNoPrefix(sender, "&7/" + label + " chasseur set <joueur>");
            messages.sendRawNoPrefix(sender, "&7/" + label + " chasseur clear");
            messages.sendRawNoPrefix(sender, "&7/" + label + " chasseur list");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set", "add" -> {
                if (args.length < 3) {
                    messages.sendRawNoPrefix(sender, "&7/" + label + " chasseur set <joueur>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    messages.send(sender, "invalid-player");
                    return true;
                }

                gameManager.addDesignatedHunter(target);
                messages.send(sender, "hunter-added", Map.of("player", target.getName()));
                return true;
            }
            case "clear" -> {
                gameManager.clearDesignatedHunters();
                messages.send(sender, "hunters-cleared");
                return true;
            }
            case "list" -> {
                Set<String> hunters = gameManager.getDesignatedHunters().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .map(Player::getName)
                    .collect(Collectors.toSet());
                messages.sendRaw(sender, "&7Chasseurs: &f" + String.join(", ", hunters));
                return true;
            }
            default -> {
                messages.sendRawNoPrefix(sender, "&7/" + label + " chasseur set <joueur>");
                return true;
            }
        }
    }

    private boolean handleTime(CommandSender sender, String label, String[] args) {
        if (args.length < 4 || !"set".equalsIgnoreCase(args[2])) {
            messages.sendRawNoPrefix(sender, "&7/" + label + " time hide set <secondes>");
            messages.sendRawNoPrefix(sender, "&7/" + label + " time hunt set <secondes>");
            return true;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[3]);
            if (seconds <= 0) {
                throw new NumberFormatException("must be > 0");
            }
        } catch (NumberFormatException ex) {
            messages.send(sender, "invalid-number");
            return true;
        }

        String phase = args[1].toLowerCase(Locale.ROOT);
        if ("hide".equals(phase)) {
            plugin.getConfig().set("hide-time", seconds);
            plugin.saveConfig();
            messages.sendRaw(sender, "&aHide-time = " + seconds + "s");
            return true;
        }

        if ("hunt".equals(phase)) {
            plugin.getConfig().set("hunt-time", seconds);
            plugin.saveConfig();
            messages.sendRaw(sender, "&aHunt-time = " + seconds + "s");
            return true;
        }

        messages.sendRawNoPrefix(sender, "&7/" + label + " time hide set <secondes>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " time hunt set <secondes>");
        return true;
    }

    private boolean handleMap(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendMapHelp(sender, label);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (args.length < 3) {
                    messages.sendRawNoPrefix(sender, "&7/" + label + " map create <nom>");
                    return true;
                }
                String mapName = args[2];
                if (!mapManager.createMap(mapName)) {
                    messages.sendRaw(sender, "&cMap deja existante.");
                    return true;
                }
                messages.send(sender, "map-created", Map.of("map", mapName));
                return true;
            }
            case "delete" -> {
                if (args.length < 3) {
                    messages.sendRawNoPrefix(sender, "&7/" + label + " map delete <nom>");
                    return true;
                }
                String mapName = args[2];
                if (!mapManager.deleteMap(mapName)) {
                    messages.send(sender, "invalid-map");
                    return true;
                }
                messages.send(sender, "map-deleted", Map.of("map", mapName));
                return true;
            }
            case "list" -> {
                String list = mapManager.getMaps().stream().map(MapData::getName).sorted().collect(Collectors.joining(", "));
                messages.sendRaw(sender, "&7Maps: &f" + (list.isEmpty() ? "aucune" : list));
                return true;
            }
            case "setcenter" -> {
                return handleMapLocationUpdate(sender, args, "setcenter");
            }
            case "sethunterspawn" -> {
                return handleMapLocationUpdate(sender, args, "sethunterspawn");
            }
            case "setwaiting" -> {
                return handleMapLocationUpdate(sender, args, "setwaiting");
            }
            case "setborder" -> {
                if (args.length < 4) {
                    messages.sendRawNoPrefix(sender, "&7/" + label + " map setborder <nom> <rayon>");
                    return true;
                }
                String mapName = args[2];
                MapData mapData = mapManager.getMap(mapName);
                if (mapData == null) {
                    messages.send(sender, "invalid-map");
                    return true;
                }
                int radius;
                try {
                    radius = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    messages.send(sender, "invalid-number");
                    return true;
                }

                mapData.setBorderRadius(radius);
                mapManager.save();
                messages.send(sender, "map-updated", Map.of("map", mapData.getName()));
                return true;
            }
            default -> {
                sendMapHelp(sender, label);
                return true;
            }
        }
    }

    private boolean handleMapLocationUpdate(CommandSender sender, String[] args, String mode) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        if (args.length < 3) {
            messages.sendRaw(sender, "&cMap requise.");
            return true;
        }

        String mapName = args[2];
        MapData mapData = mapManager.getMap(mapName);
        if (mapData == null) {
            messages.send(sender, "invalid-map");
            return true;
        }

        Location location = player.getLocation();

        switch (mode) {
            case "setcenter" -> mapData.setCenter(location);
            case "sethunterspawn" -> mapData.setHunterSpawn(location);
            case "setwaiting" -> mapData.setWaitingRoom(location);
            default -> {
                return true;
            }
        }

        mapManager.save();
        messages.send(sender, "map-updated", Map.of("map", mapData.getName()));
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        messages.sendRaw(sender, "&6Commandes:");
        messages.sendRawNoPrefix(sender, "&7/" + label + " start");
        messages.sendRawNoPrefix(sender, "&7/" + label + " stop");
        messages.sendRawNoPrefix(sender, "&7/" + label + " reload");
        messages.sendRawNoPrefix(sender, "&7/" + label + " select <map>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " chasseur set <joueur>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " chasseur clear");
        messages.sendRawNoPrefix(sender, "&7/" + label + " time hide set <secondes>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " time hunt set <secondes>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map <...>");
    }

    private void sendMapHelp(CommandSender sender, String label) {
        messages.sendRaw(sender, "&6Map:");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map create <nom>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map delete <nom>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map list");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map setcenter <nom>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map setborder <nom> <rayon>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map sethunterspawn <nom>");
        messages.sendRawNoPrefix(sender, "&7/" + label + " map setwaiting <nom>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("start", "stop", "reload", "chasseur", "time", "map", "select"), args[0]);
        }

        if (args.length == 2 && "chasseur".equalsIgnoreCase(args[0])) {
            return filter(List.of("set", "add", "clear", "list"), args[1]);
        }

        if (args.length == 3 && "chasseur".equalsIgnoreCase(args[0]) && ("set".equalsIgnoreCase(args[1]) || "add".equalsIgnoreCase(args[1]))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }

        if (args.length == 2 && "time".equalsIgnoreCase(args[0])) {
            return filter(List.of("hide", "hunt"), args[1]);
        }

        if (args.length == 3 && "time".equalsIgnoreCase(args[0])) {
            return filter(List.of("set"), args[2]);
        }

        if (args.length == 2 && "map".equalsIgnoreCase(args[0])) {
            return filter(List.of("create", "delete", "list", "setcenter", "setborder", "sethunterspawn", "setwaiting"), args[1]);
        }

        if (args.length == 3 && "map".equalsIgnoreCase(args[0]) && !"create".equalsIgnoreCase(args[1])) {
            List<String> maps = mapManager.getMaps().stream().map(MapData::getName).toList();
            return filter(maps, args[2]);
        }

        if (args.length == 2 && "select".equalsIgnoreCase(args[0])) {
            List<String> maps = mapManager.getMaps().stream().map(MapData::getName).toList();
            return filter(maps, args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        String search = token.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(search))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
