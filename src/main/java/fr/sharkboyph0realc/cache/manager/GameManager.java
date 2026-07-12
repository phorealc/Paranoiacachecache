package fr.sharkboyph0realc.cache.manager;

import fr.sharkboyph0realc.cache.game.GameState;
import fr.sharkboyph0realc.cache.model.MapData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameManager {

	private final JavaPlugin plugin;
	private final MessageManager messages;
	private final MapManager mapManager;
	private final NamespacedKey hunterPearlKey;
	private final NamespacedKey hiderPearlKey;

	private final Set<UUID> designatedHunters = new HashSet<>();
	private final Set<UUID> activeHunters = new HashSet<>();
	private final Set<UUID> activeHiders = new HashSet<>();
	private final Set<UUID> foundHiders = new HashSet<>();
	private final Set<UUID> usedHiderPearls = new HashSet<>();

	private final Map<UUID, Location> previousLocations = new HashMap<>();

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

		activeHunters.clear();
		activeHiders.clear();
		foundHiders.clear();
		usedHiderPearls.clear();
		previousLocations.clear();
		foundCount = 0;

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
			sender.sendMessage(messages.get("invalid-player") + " Aucun joueur cache disponible.");
			return false;
		}

		applyWorldBorder(map);

		for (UUID uuid : activeHiders) {
			Player hider = Bukkit.getPlayer(uuid);
			if (hider == null) {
				continue;
			}

			previousLocations.put(uuid, hider.getLocation());
			preparePlayerForGame(hider);
			hider.teleport(map.getCenter());
			hider.getInventory().addItem(new ItemStack(Material.BEEF, getInt("raw-beef", 64)));
			hider.getInventory().addItem(createHiderPearl());
		}

		for (UUID uuid : activeHunters) {
			Player hunter = Bukkit.getPlayer(uuid);
			if (hunter == null) {
				continue;
			}

			previousLocations.put(uuid, hunter.getLocation());
			preparePlayerForGame(hunter);
			hunter.teleport(map.getWaitingRoom());
			hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * Math.max(getInt("hide-time", 120), 1) + 40, 0, false, false));
		}

		gameState = GameState.HIDING;
		startHidePhaseTimer(map);
		broadcast(messages.get("game-started"));
		return true;
	}

	public void stopGame(CommandSender sender) {
		if (gameState == GameState.IDLE) {
			messages.send(sender, "state-idle");
			return;
		}

		endGame("winner-draw", true);
		broadcast(messages.get("game-stopped"));
	}

	public void reloadRuntimeData() {
		if (gameState != GameState.IDLE) {
			endGame("winner-draw", true);
		}
	}

	private void startHidePhaseTimer(MapData map) {
		cancelTimerTasks();
		timeLeft = Math.max(1, getInt("hide-time", 120));
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

	private void onHuntTimeout() {
		cancelTimerTasks();

		if (foundCount == 0) {
			broadcast(messages.get("time-up-glow"));
			applyGlowingToAll(30 * 20);

			timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
				teleportAllToWorldSpawn();
				endGame("winner-draw", true);
			}, 30L * 20L);
			return;
		}

		endGame("winner-hiders", true);
	}

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

	public void ensurePlayerStateOnJoin(Player player) {
		if (gameState == GameState.IDLE) {
			return;
		}

		if (foundHiders.contains(player.getUniqueId())) {
			player.setGameMode(GameMode.SPECTATOR);
		}
	}

	public void onHiderPearlUse(Player player) {
		if (gameState != GameState.HIDING || !activeHiders.contains(player.getUniqueId())) {
			return;
		}

		UUID uuid = player.getUniqueId();
		if (usedHiderPearls.contains(uuid)) {
			messages.send(player, "hider-pearl-used");
			return;
		}

		usedHiderPearls.add(uuid);
		Bukkit.getScheduler().runTask(plugin, () -> removePearls(player));
	}

	public boolean onHunterPearlPreUse(Player player) {
		if (gameState != GameState.HUNTING || !activeHunters.contains(player.getUniqueId())) {
			return true;
		}

		Material key = Material.ENDER_PEARL;
		if (player.hasCooldown(key)) {
			int ticks = player.getCooldown(key);
			int seconds = Math.max(1, ticks / 20);
			messages.send(player, "hunter-pearl-cooldown", Map.of("seconds", String.valueOf(seconds)));
			return false;
		}

		player.setCooldown(key, Math.max(1, getInt("hunter-pearl-cooldown", 30)) * 20);
		Bukkit.getScheduler().runTask(plugin, () -> ensureInfinitePearl(player));
		return true;
	}

	public boolean isTaggedHunterPearl(ItemStack itemStack) {
		if (itemStack == null || itemStack.getType() != Material.ENDER_PEARL || !itemStack.hasItemMeta()) {
			return false;
		}
		ItemMeta meta = itemStack.getItemMeta();
		Byte value = meta.getPersistentDataContainer().get(hunterPearlKey, PersistentDataType.BYTE);
		return value != null && value == (byte) 1;
	}

	public boolean isTaggedHiderPearl(ItemStack itemStack) {
		if (itemStack == null || itemStack.getType() != Material.ENDER_PEARL || !itemStack.hasItemMeta()) {
			return false;
		}
		ItemMeta meta = itemStack.getItemMeta();
		Byte value = meta.getPersistentDataContainer().get(hiderPearlKey, PersistentDataType.BYTE);
		return value != null && value == (byte) 1;
	}

	public void onPearlTeleport(Player player, PlayerTeleportEvent.TeleportCause cause) {
		if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
			return;
		}

		if (gameState == GameState.HIDING && activeHiders.contains(player.getUniqueId())) {
			onHiderPearlUse(player);
			return;
		}

		if (gameState == GameState.HUNTING && activeHunters.contains(player.getUniqueId())) {
			ensureInfinitePearl(player);
		}
	}

	private void applyWorldBorder(MapData map) {
		Location center = map.getCenter();
		if (center == null || center.getWorld() == null) {
			return;
		}

		World world = center.getWorld();
		WorldBorder border = world.getWorldBorder();
		border.setCenter(center);
		border.setSize(map.getBorderRadius() * 2.0);
	}

	private void preparePlayerForGame(Player player) {
		player.getInventory().clear();
		player.getInventory().setArmorContents(null);
		player.setHealth(20.0);
		player.setFoodLevel(20);
		player.setSaturation(20f);
		player.setGameMode(GameMode.SURVIVAL);
		player.setFireTicks(0);
		player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
	}

	private ItemStack createHiderPearl() {
		ItemStack item = new ItemStack(Material.ENDER_PEARL, Math.max(1, getInt("player-pearl-uses", 1)));
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

		ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
		ItemStack bow = new ItemStack(Material.BOW, 1);
		ItemMeta bowMeta = bow.getItemMeta();
		bowMeta.addEnchant(Enchantment.POWER, Math.max(1, getInt("hunter-bow-power", 4)), true);
		bow.setItemMeta(bowMeta);

		int arrows = Math.max(1, getInt("hunter-arrows", 20));
		int speedAmplifier = Math.max(0, getInt("hunter-speed", 1) - 1);

		hunter.getInventory().addItem(sword);
		hunter.getInventory().addItem(bow);
		hunter.getInventory().addItem(new ItemStack(Material.ARROW, arrows));
		hunter.getInventory().addItem(createHunterPearl());
		hunter.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedAmplifier, false, false));
	}

	private void removeHiderPearls() {
		for (UUID uuid : activeHiders) {
			Player hider = Bukkit.getPlayer(uuid);
			if (hider == null) {
				continue;
			}
			removePearls(hider);
		}
	}

	private void removePearls(Player player) {
		ItemStack[] contents = player.getInventory().getContents();
		if (contents == null) {
			return;
		}
		for (int i = 0; i < contents.length; i++) {
			ItemStack item = contents[i];
			if (item == null) {
				continue;
			}
			if (item.getType() == Material.ENDER_PEARL) {
				contents[i] = null;
			}
		}
		player.getInventory().setContents(contents);
	}

	private void ensureInfinitePearl(Player player) {
		ItemStack[] contents = player.getInventory().getContents();
		if (contents == null) {
			player.getInventory().addItem(createHunterPearl());
			return;
		}

		for (ItemStack item : contents) {
			if (isTaggedHunterPearl(item)) {
				item.setAmount(1);
				return;
			}
		}
		player.getInventory().addItem(createHunterPearl());
	}

	private void startBeepTask() {
		cancelBeepTaskOnly();
		int delay = Math.max(1, getInt("beep-delay", 30)) * 20;

		beepTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (gameState != GameState.HUNTING) {
				return;
			}

			Sound sound = getBeepSound();
			for (UUID hiderUuid : activeHiders) {
				if (foundHiders.contains(hiderUuid)) {
					continue;
				}

				Player hider = Bukkit.getPlayer(hiderUuid);
				if (hider == null || !hider.isOnline()) {
					continue;
				}

				Location location = hider.getLocation();
				if (location == null) {
					continue;
				}
				World hiderWorld = location.getWorld();
				if (hiderWorld == null) {
					continue;
				}
				for (UUID hunterUuid : activeHunters) {
					Player hunter = Bukkit.getPlayer(hunterUuid);
					if (hunter == null || !hunter.isOnline()) {
						continue;
					}
					if (hunter.getWorld().equals(hiderWorld)) {
						hunter.playSound(location, sound, 1.0f, 1.0f);
					}
				}
			}
		}, delay, delay);
	}

	private Sound getBeepSound() {
		String configured = plugin.getConfig().getString("beep-sound", Sound.BLOCK_NOTE_BLOCK_PLING.name());
		if (configured == null || configured.isBlank()) {
			return Sound.BLOCK_NOTE_BLOCK_PLING;
		}
		try {
			return Sound.valueOf(configured.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return Sound.BLOCK_NOTE_BLOCK_PLING;
		}
	}

	private void applyGlowingToAll(int ticks) {
		for (UUID uuid : getAllParticipants()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null || !player.isOnline()) {
				continue;
			}
			player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false));
		}
	}

	private void teleportAllToWorldSpawn() {
		for (UUID uuid : getAllParticipants()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null || !player.isOnline()) {
				continue;
			}

			Location target = player.getWorld().getSpawnLocation();
			if (!previousLocations.containsKey(uuid)) {
				player.teleport(target);
				continue;
			}

			player.teleport(target);
		}
	}

	private Set<UUID> getAllParticipants() {
		Set<UUID> all = new HashSet<>();
		all.addAll(activeHunters);
		all.addAll(activeHiders);
		return all;
	}

	private void endGame(String winnerMessageKey, boolean teleportToSpawn) {
		gameState = GameState.ENDING;
		cancelAllTasks();
		clearBossBar();

		broadcast(messages.get(winnerMessageKey));

		Collection<UUID> participants = getAllParticipants();
		for (UUID uuid : participants) {
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

			if (teleportToSpawn) {
				player.teleport(player.getWorld().getSpawnLocation());
			}
		}

		activeHunters.clear();
		activeHiders.clear();
		foundHiders.clear();
		usedHiderPearls.clear();
		previousLocations.clear();
		foundCount = 0;

		gameState = GameState.IDLE;
	}

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
