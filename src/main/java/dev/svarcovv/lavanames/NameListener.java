package dev.svarcovv.lavanames;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

public final class NameListener implements Listener {
    private final LavaNames plugin;

    public NameListener(LavaNames plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        plugin.manager().join(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        plugin.manager().quit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        plugin.manager().changed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void world(PlayerChangedWorldEvent event) {
        plugin.manager().reset(event.getPlayer());
        plugin.manager().clearViewer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {
        plugin.manager().reset(event.getEntity());
        if (event.getEntity().getKiller() != null) {
            plugin.manager().changed(event.getEntity().getKiller());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) {
        plugin.manager().reset(event.getPlayer());
        plugin.manager().clearViewer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mode(PlayerGameModeChangeEvent event) {
        plugin.manager().changed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void sneak(PlayerToggleSneakEvent event) {
        plugin.manager().changed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void effect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.manager().changed(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.manager().changed(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void heal(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.manager().changed(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void track(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.manager().changed(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void untrack(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.manager().untrack(event.getPlayer(), player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void load(EntitiesLoadEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.manager().cleanupLegacy(event.getEntities()));
    }
}
