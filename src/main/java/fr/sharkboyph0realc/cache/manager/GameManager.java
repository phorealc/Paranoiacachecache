package fr.sharkboyph0realc.cache.manager;

import fr.sharkboyph0realc.cache.game.GameState;
import fr.sharkboyph0realc.cache.model.MapData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameManager {

    /** Duree infinie pour un PotionEffect depuis 1.19.4 (Integer.MAX_VALUE deborde a l'affichage). */
    private static final int INFINITE_DURATION = -1;
    private static final int GLOW_SECONDS = 30;
    private static final Key DEFAULT_BEEP_KEY = Key.key("block.note_block.pling");

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final MapManager mapManager;
    private final NamespacedKey hunterPearlKey;
    private final NamespacedKey hiderPearlKey;

    private final Set<UUID> designatedHunters = new HashSet<>();
    private final Set<UUID> activeHunters = new HashSet<>();
    private final Set<UUID> activeHiders = new HashSet<>();
    private final Set<UUID> foundHiders = new HashSet<>();

    /** Position d'origine des joueurs, restauree en fin de partie. */
    private final Map<UUID, Location> previousLocations = new HashMap<>();

    /** Etat de la worldborder avant la partie, restaure en fin de partie. */
    private BorderSnapshot borderSnapshot;

    private GameState gameState = GameState.IDLE;
    private BossBar bossBar;
    private BukkitTask timerTask;
    private BukkitTask beepTask;
    private BukkitTask timeoutTask;
    private int timeLeft;
    private int initialPhaseTime;
    private int foundCount;

    public GameManager(JavaPlugin plugin, MessageManager messages, MapManager mapManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.mapManager = mapManager;
        this.hunterPearlKey = new NamespacedKey(plugin, "hunter_pearl");
        this.hiderPearlKey = new NamespacedKey(plugin, "hider_pearl");
    }

    // ---------------------------------------------------------------- etat

    public GameState getGameState() {
        return gameState;
    }

    public Set<UUID> getDesignatedHunters() {
        return Set.copyOf(designatedHunters);
    }

    public Set<UUID> getActiveHunters() {
        return Set.copyOf(activeHunters);
    }

    public Set<UUID> getActiveHiders() {
        return Set.copyOf(activeHiders);
    }

    public boolean addDesignatedHunter(Player player) {
        return designatedHunters.add(player.getUniqueId());
    }

    public void clearDesignatedHunters() {
        designatedHunters.clear();
    }

    public boolean isHunter(UUID uuid) {
        return activeHunters.contains(uuid);
    }

    public boolean isHider(UUID uuid) {
        return activeHiders.contains(uuid);
    }

    public boolean isFoundHider(UUID uuid) {
        return foundHiders.contains(uuid);
    }

    public int getFoundCount() {
        return foundCount;
    }

    /** Les caches ne peuvent utiliser leurs perles que pendant la phase cachette. */
    public boolean canHiderUsePearl(UUID uuid) {
        return gameState == GameState.HIDING && activeHiders.contains(uuid) && !foundHiders.contains(uuid);
    }

    // ------------------------------------------------------------- partie

    public boolean startGame(CommandSender sender) {
        if (gameState != GameState.IDLE) {
            messages.send(sender, "state-running");
            return false;
        }

        MapData map = mapManager.getSelectedMap();
        if (map == null) {
            messages.send(sender, "no-selected-map");
            return false;
        }

        if (!map.isReady()) {
            messages.send(sender, "map-not-ready");
            return false;
        }

        resetRuntimeState();

        for (UUID uuid : designatedHunters) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                activeHunters.add(uuid);
            }
        }

        if (activeHunters.isEmpty()) {
            messages.send(sender, "hunter-none");
            return false;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!activeHunters.contains(online.getUniqueId())) {
                activeHiders.add(online.getUniqueId());
            }
        }

        if (activeHiders.isEmpty()) {
            activeHunters.clear();
            messages.send(sender, "hiders-none");
            return false;
        }

        applyWorldBorder(map);

        int hideTime = Math.max(1, getInt("hide-time", 120));
        int pearls = Math.max(0, getInt("player-pearl-uses", 1));
        int beef = Math.max(0, getInt("raw-beef", 64));

        for (UUID uuid : activeHiders) {
            Player hider = Bukkit.getPlayer(uuid);
            if (hider == null) {
                continue;
            }

            previousLocations.put(uuid, hider.getLocation());
            preparePlayerForGame(hider);
            hider.teleport(map.getCenter());
            if (beef > 0) {
                hider.getInventory().addItem(new ItemStack(Material.BEEF, beef));
            }
            if (pearls > 0) {
                hider.getInventory().addItem(createHiderPearl(pearls));
            }
        }

        for (UUID uuid : activeHunters) {
            Player hunter = Bukkit.getPlayer(uuid);
            if (hunter == null) {
                continue;
            }

            previousLocations.put(uuid, hunter.getLocation());
            preparePlayerForGame(hunter);
            hunter.teleport(map.getWaitingRoom());
            hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, hideTime * 20 + 40, 0, false, false));
        }

        gameState = GameState.HIDING;
        startHidePhaseTimer(map, hideTime);
        broadcast(messages.get("game-started"));
        return true;
    }

    public void stopGame(CommandSender sender) {
        if (gameState == GameState.IDLE) {
            messages.send(sender, "state-idle");
            return;
        }

        broadcast(messages.get("game-stopped"));
        endGame("winner-draw", true);
    }

    /** Appele par /cache reload : coupe proprement une partie en cours. */
    public void reloadRuntimeData() {
        if (gameState != GameState.IDLE) {
            endGame("winner-draw", true);
        }
    }

    /** Appele depuis onDisable : on nettoie sans teleporter (le serveur s'arrete). */
    public void shutdown() {
        if (gameState != GameState.IDLE) {
            endGame("winner-draw", false);
        } else {
            restoreWorldBorder();
        }
    }

    private void resetRuntimeState() {
        activeHunters.clear();
        activeHiders.clear();
        foundHiders.clear();
        previousLocations.clear();
        foundCount = 0;
    }

    // -------------------------------------------------------------- phases

    private void startHidePhaseTimer(MapData map, int hideTime) {
        cancelTimerTasks();
        timeLeft = hideTime;
        initialPhaseTime = timeLeft;
        createOrUpdateBossBar("Phase cachette", BarColor.BLUE);

        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateBossBar("Cachette", timeLeft, initialPhaseTime);
            timeLeft--;

            if (timeLeft < 0) {
                removeHiderPearls();
                startHuntPhase(map);
            }
        }, 0L, 20L);
    }

    private void startHuntPhase(MapData map) {
        cancelTimerTaskOnly();

        for (UUID uuid : activeHunters) {
            Player hunter = Bukkit.getPlayer(uuid);
            if (hunter == null) {
                continue;
            }

            hunter.teleport(map.getHunterSpawn());
            hunter.removePotionEffect(PotionEffectType.BLINDNESS);
            giveHunterKit(hunter);
        }

        gameState = GameState.HUNTING;
        timeLeft = Math.max(1, getInt("hunt-time", 600));
        initialPhaseTime = timeLeft;
        createOrUpdateBossBar("Phase recherche", BarColor.RED);
        startBeepTask();

        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateBossBar("Recherche", timeLeft, initialPhaseTime);
            timeLeft--;

            if (timeLeft < 0) {
                onHuntTimeout();
            }
        }, 0L, 20L);
    }

    /**
     * Temps ecoule : les caches survivants ont gagne.
     * Si personne n'a ete trouve, on ajoute 30s de glowing avant la fin (spectacle).
     */
    private void onHuntTimeout() {
        cancelTimerTasks();

        if (foundCount == 0) {
            gameState = GameState.ENDING;
            broadcast(messages.get("time-up-glow"));
            applyGlowingToAll(GLOW_SECONDS * 20);

            timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> endGame("winner-hiders", true), GLOW_SECONDS * 20L);
            return;
        }

        endGame("winner-hiders", true);
    }

    // -------------------------------------------------------- evenements

    public void markFound(Player player) {
        if (gameState != GameState.HUNTING) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!activeHiders.contains(uuid) || foundHiders.contains(uuid)) {
            return;
        }

        foundHiders.add(uuid);
        foundCount++;

        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        broadcast(messages.format("hider-found", Map.of("player", player.getName())));

        if (foundHiders.size() >= activeHiders.size()) {
            endGame("winner-hunters", true);
        }
    }

    public void markFoundByDisconnect(Player player) {
        UUID uuid = player.getUniqueId();

        if (gameState != GameState.HUNTING || !activeHiders.contains(uuid) || foundHiders.contains(uuid)) {
            return;
        }

        foundHiders.add(uuid);
        foundCount++;
        broadcast(messages.format("player-disconnected-found", Map.of("player", player.getName())));

        if (foundHiders.size() >= activeHiders.size()) {
            endGame("winner-hunters", true);
        }
    }

    public void onHunterQuit(Player player) {
        if (gameState == GameState.IDLE) {
            return;
        }

        if (!activeHunters.remove(player.getUniqueId())) {
            return;
        }

        if (activeHunters.isEmpty()) {
            endGame("winner-hiders", true);
        }
    }

    /** Un participant qui se reconnecte retrouve son etat et sa bossbar. */
    public void ensurePlayerStateOnJoin(Player player) {
        if (gameState == GameState.IDLE) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!activeHunters.contains(uuid) && !activeHiders.contains(uuid)) {
            return;
        }

        if (foundHiders.contains(uuid)) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    /**
     * Le stock de perles est desormais gere par le jeu lui-meme :
     * un cache recoit `player-pearl-uses` perles et chaque lancer en consomme une.
     */
    public boolean onHiderPearlPreUse(Player player) {
        if (!activeHiders.contains(player.getUniqueId())) {
            return true;
        }

        if (!canHiderUsePearl(player.getUniqueId())) {
            messages.send(player, "hider-pearl-locked");
            return false;
        }

        return true;
    }

    public boolean onHunterPearlPreUse(Player player) {
        if (gameState != GameState.HUNTING || !activeHunters.contains(player.getUniqueId())) {
            return true;
        }

        Material key = Material.ENDER_PEARL;
        if (player.hasCooldown(key)) {
            int seconds = Math.max(1, player.getCooldown(key) / 20);
            messages.send(player, "hunter-pearl-cooldown", Map.of("seconds", String.valueOf(seconds)));
            return false;
        }

        player.setCooldown(key, Math.max(1, getInt("hunter-pearl-cooldown", 30)) * 20);
        Bukkit.getScheduler().runTask(plugin, () -> ensureInfinitePearl(player));
        return true;
    }

    public boolean isTaggedHunterPearl(ItemStack itemStack) {
        return hasTag(itemStack, hunterPearlKey);
    }

    public boolean isTaggedHiderPearl(ItemStack itemStack) {
        return hasTag(itemStack, hiderPearlKey);
    }

    private boolean hasTag(ItemStack itemStack, NamespacedKey key) {
        if (itemStack == null || itemStack.getType() != Material.ENDER_PEARL || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        Byte value = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void onPearlTeleport(Player player, PlayerTeleportEvent.TeleportCause cause) {
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        if (gameState == GameState.HUNTING && activeHunters.contains(player.getUniqueId())) {
            ensureInfinitePearl(player);
        }
    }

    // ------------------------------------------------------ worldborder

    private record BorderSnapshot(String world, Location center, double size) {
    }

    private void applyWorldBorder(MapData map) {
        Location center = map.getCenter();
        if (center == null || center.getWorld() == null) {
            return;
        }

        World world = center.getWorld();
        WorldBorder border = world.getWorldBorder();

        // Sauvegarde de l'etat AVANT modification, pour pouvoir le restaurer.
        borderSnapshot = new BorderSnapshot(world.getName(), border.getCenter().clone(), border.getSize());

        border.setCenter(center);
        border.setSize(map.getBorderRadius() * 2.0);
    }

    private void restoreWorldBorder() {
        if (borderSnapshot == null) {
            return;
        }

        World world = Bukkit.getWorld(borderSnapshot.world());
        if (world != null) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(borderSnapshot.center());
            border.setSize(borderSnapshot.size());
        }

        borderSnapshot = null;
    }

    // ----------------------------------------------------------- joueurs

    private void preparePlayerForGame(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFireTicks(0);
        player.setCooldown(Material.ENDER_PEARL, 0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }

    private ItemStack createHiderPearl(int amount) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Perle de fuite"));
        meta.getPersistentDataContainer().set(hiderPearlKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHunterPearl() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Perle infinie du chasseur"));
        meta.getPersistentDataContainer().set(hunterPearlKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void giveHunterKit(Player hunter) {
        hunter.getInventory().clear();

        ItemStack bow = new ItemStack(Material.BOW, 1);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.addEnchant(Enchantment.POWER, Math.max(1, getInt("hunter-bow-power", 4)), true);
        bow.setItemMeta(bowMeta);

        int arrows = Math.max(1, getInt("hunter-arrows", 20));
        int speedAmplifier = Math.max(0, getInt("hunter-speed", 1) - 1);

        hunter.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD, 1));
        hunter.getInventory().addItem(bow);
        hunter.getInventory().addItem(new ItemStack(Material.ARROW, arrows));
        hunter.getInventory().addItem(createHunterPearl());
        hunter.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, INFINITE_DURATION, speedAmplifier, false, false));
    }

    private void removeHiderPearls() {
        for (UUID uuid : activeHiders) {
            Player hider = Bukkit.getPlayer(uuid);
            if (hider != null) {
                removePearls(hider);
            }
        }
    }

    private void removePearls(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.ENDER_PEARL) {
                inventory.setItem(i, null);
            }
        }
    }

    private void ensureInfinitePearl(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isTaggedHunterPearl(item)) {
                if (item.getAmount() != 1) {
                    ItemStack refreshed = item.clone();
                    refreshed.setAmount(1);
                    inventory.setItem(i, refreshed);
                }
                return;
            }
        }
        inventory.addItem(createHunterPearl());
    }

    // -------------------------------------------------------------- bips

    private void startBeepTask() {
        cancelBeepTaskOnly();
        int delay = Math.max(1, getInt("beep-delay", 30)) * 20;
        Sound sound = getBeepSound();

        beepTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.HUNTING) {
                return;
            }

            for (UUID hiderUuid : activeHiders) {
                if (foundHiders.contains(hiderUuid)) {
                    continue;
                }

                Player hider = Bukkit.getPlayer(hiderUuid);
                if (hider == null || !hider.isOnline()) {
                    continue;
                }

                Location location = hider.getLocation();
                World hiderWorld = location.getWorld();
                if (hiderWorld == null) {
                    continue;
                }

                for (UUID hunterUuid : activeHunters) {
                    Player hunter = Bukkit.getPlayer(hunterUuid);
                    if (hunter == null || !hunter.isOnline() || !hunter.getWorld().equals(hiderWorld)) {
                        continue;
                    }
                    hunter.playSound(sound, location.getX(), location.getY(), location.getZ());
                }
            }
        }, delay, delay);
    }

    /**
     * Utilise l'API Adventure (cle de son) au lieu de l'enum org.bukkit.Sound :
     * l'enum a ete convertie en registre dans les versions recentes et
     * Sound.valueOf(...) casse a l'execution sur ces serveurs.
     * Les anciens noms (BLOCK_NOTE_BLOCK_PLING) restent acceptes.
     */
    private Sound getBeepSound() {
        String configured = plugin.getConfig().getString("beep-sound");
        return Sound.sound(resolveSoundKey(configured), Sound.Source.MASTER, 1.0f, 1.0f);
    }

    private Key resolveSoundKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_BEEP_KEY;
        }

        String value = configured.trim().toLowerCase(Locale.ROOT);

        // Format moderne : block.note_block.pling ou minecraft:block.note_block.pling
        if (value.indexOf('.') >= 0 || value.indexOf(':') >= 0) {
            try {
                return Key.key(value);
            } catch (Exception ignored) {
                plugin.getLogger().warning("beep-sound invalide: " + configured);
                return DEFAULT_BEEP_KEY;
            }
        }

        // Format historique : nom d'enum Bukkit.
        try {
            org.bukkit.Sound legacy = org.bukkit.Sound.valueOf(configured.trim().toUpperCase(Locale.ROOT));
            return Key.key(legacy.getKey().toString());
        } catch (Throwable ignored) {
            plugin.getLogger().warning("beep-sound '" + configured
                + "' non reconnu, utilise le format cle (ex: block.note_block.pling).");
            return DEFAULT_BEEP_KEY;
        }
    }

    // ----------------------------------------------------------- fin de partie

    private void applyGlowingToAll(int ticks) {
        for (UUID uuid : getAllParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false));
            }
        }
    }

    private Set<UUID> getAllParticipants() {
        Set<UUID> all = new HashSet<>(activeHunters);
        all.addAll(activeHiders);
        return all;
    }

    private void endGame(String winnerMessageKey, boolean restorePlayers) {
        if (gameState == GameState.IDLE) {
            return;
        }

        gameState = GameState.ENDING;
        cancelAllTasks();
        clearBossBar();

        broadcast(messages.get(winnerMessageKey));

        for (UUID uuid : getAllParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.setCooldown(Material.ENDER_PEARL, 0);
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();

            if (restorePlayers) {
                restoreLocation(player);
            }
        }

        restoreWorldBorder();

        activeHunters.clear();
        activeHiders.clear();
        foundHiders.clear();
        previousLocations.clear();
        foundCount = 0;

        gameState = GameState.IDLE;
    }

    /** Renvoie le joueur la ou il etait avant la partie (spawn du monde en secours). */
    private void restoreLocation(Player player) {
        Location target = previousLocations.get(player.getUniqueId());
        if (target == null || target.getWorld() == null) {
            target = player.getWorld().getSpawnLocation();
        }
        player.teleport(target);
    }

    // ------------------------------------------------------------ bossbar

    private void createOrUpdateBossBar(String title, BarColor color) {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(title, color, BarStyle.SEGMENTED_12);
        } else {
            bossBar.setTitle(title);
            bossBar.setColor(color);
        }

        bossBar.removeAll();
        for (UUID uuid : getAllParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void updateBossBar(String phaseLabel, int secondsRemaining, int total) {
        if (bossBar == null) {
            return;
        }

        int safeTotal = Math.max(1, total);
        double progress = Math.max(0.0, Math.min(1.0, (double) secondsRemaining / safeTotal));
        bossBar.setProgress(progress);
        bossBar.setTitle(phaseLabel + " - " + Math.max(0, secondsRemaining) + "s");
    }

    private void clearBossBar() {
        if (bossBar == null) {
            return;
        }
        bossBar.removeAll();
        bossBar = null;
    }

    // -------------------------------------------------------------- taches

    private void cancelTimerTasks() {
        cancelTimerTaskOnly();
        cancelBeepTaskOnly();
    }

    private void cancelTimerTaskOnly() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void cancelBeepTaskOnly() {
        if (beepTask != null) {
            beepTask.cancel();
            beepTask = null;
        }
    }

    private void cancelAllTasks() {
        cancelTimerTasks();

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    private int getInt(String key, int defaultValue) {
        return plugin.getConfig().getInt(key, defaultValue);
    }

    private void broadcast(String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
