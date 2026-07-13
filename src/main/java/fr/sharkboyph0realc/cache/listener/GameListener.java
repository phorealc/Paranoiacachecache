package fr.sharkboyph0realc.cache.listener;

import fr.sharkboyph0realc.cache.manager.GameManager;
import org.bukkit.GameMode;
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
            player.setGameMode(GameMode.SPECTATOR);
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
            if (!gameManager.onHunterPearlPreUse(player)) {
                event.setCancelled(true);
            }
            return;
        }

        // Perle de fuite : uniquement pendant la phase cachette, le stock est
        // consomme normalement par le jeu (plus de suppression du reste du stack).
        if (gameManager.isHider(player.getUniqueId()) && !gameManager.onHiderPearlPreUse(player)) {
            event.setCancelled(true);
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
