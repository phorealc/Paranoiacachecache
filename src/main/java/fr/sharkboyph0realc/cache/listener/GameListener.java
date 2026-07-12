package fr.sharkboyph0realc.cache.listener;

import fr.sharkboyph0realc.cache.manager.GameManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (gameManager.isHider(player.getUniqueId())) {
            gameManager.markFound(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isFoundHider(player.getUniqueId())) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }

        Player player = event.getPlayer();

        if (gameManager.isHunter(player.getUniqueId())) {
            boolean allowed = gameManager.onHunterPearlPreUse(player);
            if (!allowed) {
                event.setCancelled(true);
            }
            return;
        }

        if (gameManager.isHider(player.getUniqueId()) && gameManager.isTaggedHiderPearl(item)) {
            gameManager.onHiderPearlUse(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        gameManager.onPearlTeleport(event.getPlayer(), event.getCause());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        gameManager.markFoundByDisconnect(player);
        gameManager.onHunterQuit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        gameManager.ensurePlayerStateOnJoin(event.getPlayer());
    }
}
