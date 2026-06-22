package com.pof.plugin.listener;

import com.pof.plugin.PillarsOfFortune;
import com.pof.plugin.game.GameManager;
import com.pof.plugin.game.GameState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GameListener implements Listener {

    private final PillarsOfFortune plugin;

    public GameListener(PillarsOfFortune plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ //
    //  FREEZE — block XZ movement for waiting/countdown players
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getArenaService().findGameForPlayer(player);
        if (gm == null) return;

        // Only freeze during pre-game phases
        GameState st = gm.getState();
        if (st != GameState.WAITING && st != GameState.COUNTDOWN) return;

        if (!gm.isPlayerFrozen(player)) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        // Allow looking around (yaw/pitch change) but cancel any XZ/Y movement
        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {

            // Snap back to the exact from-position but keep the new head rotation
            Location cancel = from.clone();
            cancel.setYaw(to.getYaw());
            cancel.setPitch(to.getPitch());
            event.setTo(cancel);
        }
    }

    // ------------------------------------------------------------------ //
    //  FALL DAMAGE — cancel during a live game if configured
    // ------------------------------------------------------------------ //

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        GameManager gm = plugin.getArenaService().findGameForPlayer(player);
        if (gm == null || gm.getState() != GameState.RUNNING) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && gm.shouldCancelFallDamage()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ //
    //  DEATH
    // ------------------------------------------------------------------ //

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        GameManager gm = plugin.getArenaService().findGameForPlayer(player);
        if (gm == null || gm.getState() != GameState.RUNNING) return;

        // Suppress drops/XP/death message for game players
        event.getDrops().clear();
        event.setDeathMessage(null);
        event.setDroppedExp(0);

        // handleDeath eliminates the player and teleports them to lobby.
        // We must NOT call it here because the player is in the DEAD state —
        // teleporting a dead player causes the ghost/frozen bug.
        // Instead just store that they need elimination; the respawn event handles cleanup.
        plugin.getPendingEliminations().add(player.getUniqueId());
    }

    // ------------------------------------------------------------------ //
    //  RESPAWN / QUIT / JOIN
    // ------------------------------------------------------------------ //

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getPendingEliminations().remove(player.getUniqueId())) return;

        // Find the game they were in (they haven't been removed yet)
        GameManager gm = plugin.getArenaService().findGameForPlayer(player);

        // Set respawn point to lobby BEFORE Bukkit moves them to world spawn
        if (gm != null && gm.getArena().getLobby() != null) {
            event.setRespawnLocation(gm.getArena().getLobby());
        }

        // Eliminate on the next tick so the respawn teleport completes first
        final GameManager gmFinal = gm;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (gmFinal != null) {
                gmFinal.handleDeath(player);
            } else {
                // Safety: if we lost track of their arena, just clean them up
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getArenaService().findGameForPlayer(player);
        if (gm != null) gm.handleQuit(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (GameManager gm : plugin.getArenaService().getAllGameManagers()) {
            gm.checkPendingCrashRecovery(player);
        }
    }
}
