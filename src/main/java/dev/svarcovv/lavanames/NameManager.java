package dev.svarcovv.lavanames;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NameManager {
    private record NameTag(int ownerId, UUID world, List<PacketBridge.VirtualDisplay> displays, Set<UUID> viewers) {
        int[] ids() { return displays.stream().mapToInt(d -> d.display().getEntityId()).toArray(); }
    }

    private final LavaNames plugin;
    private Settings settings;
    private final PacketBridge bridge;
    private final Placeholders placeholders;
    private final Map<UUID, NameTag> tags = new HashMap<>();
    private final Map<UUID, ViewerSession> sessions = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private BukkitTask task;
    private int tick;
    private long nextWarning;
    private List<String> onlineNames = List.of();

    public NameManager(LavaNames plugin, Settings settings, PacketBridge bridge) {
        this.plugin = plugin;
        this.settings = settings;
        this.bridge = bridge;
        this.placeholders = new Placeholders(plugin);
    }

    public void start() {
        plugin.getServer().getOnlinePlayers().forEach(this::join);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void join(Player player) {
        if (!player.isOnline() || sessions.containsKey(player.getUniqueId())) {
            return;
        }
        ViewerSession session = new ViewerSession(bridge, bridge.channel(player), settings.hideVanilla());
        sessions.put(player.getUniqueId(), session);
        session.open(bridge.scoreboardTeams(player.getScoreboard()), names());
        dirty.add(player.getUniqueId());
        updateNames();
    }

    public void quit(Player player) {
        remove(player.getUniqueId());
        ViewerSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.close(false);
        }
        dirty.remove(player.getUniqueId());
    }

    public void changed(Player player) {
        dirty.add(player.getUniqueId());
    }

    public void reset(Player player) {
        remove(player.getUniqueId());
        dirty.add(player.getUniqueId());
    }

    public void clearViewer(Player viewer) {
        ViewerSession session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            return;
        }
        for (int vehicle : session.vehicles()) {
            detach(viewer, session, vehicle);
        }
    }

    public void untrack(Player viewer, Player owner) {
        ViewerSession session = sessions.get(viewer.getUniqueId());
        if (session != null) {
            detach(viewer, session, owner.getEntityId());
        }
    }

    private void tick() {
        tick++;
        try {
            Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
            for (Player owner : players) {
                try {
                    if (!visible(owner)) {
                        remove(owner.getUniqueId());
                        continue;
                    }
                    NameTag tag = tags.get(owner.getUniqueId());
                    if (tag != null && (tag.ownerId() != owner.getEntityId() || !tag.world().equals(owner.getWorld().getUID()))) {
                        remove(owner.getUniqueId());
                        tag = null;
                    }
                    if (tag == null) {
                        tag = create(owner);
                        tags.put(owner.getUniqueId(), tag);
                    } else if (tick % settings.updateInterval() == 0 || dirty.contains(owner.getUniqueId())) {
                        update(owner, tag);
                    }
                    if (tick % settings.visibilityInterval() == 0 || dirty.contains(owner.getUniqueId())) {
                        reconcile(owner, tag);
                    }
                } catch (RuntimeException error) {
                    warn(error);
                }
            }
            dirty.clear();
            if (tick % 20 == 0) {
                updateNames();
                for (ViewerSession session : sessions.values()) {
                    Throwable error = session.takeFailure();
                    if (error != null) {
                        warn(error);
                    }
                }
            }
        } catch (RuntimeException error) {
            warn(error);
        }
    }

    private NameTag create(Player owner) {
        List<PacketBridge.VirtualDisplay> displays = new ArrayList<>();
        for (Settings.Line line : settings.lines()) {
            Component text = TextFormat.parse(placeholders.resolve(owner, line.text()));
            displays.add(bridge.createDisplay(owner, settings, line, text));
        }
        return new NameTag(owner.getEntityId(), owner.getWorld().getUID(), List.copyOf(displays), new HashSet<>());
    }

    private void update(Player owner, NameTag tag) {
        for (int i = 0; i < tag.displays().size(); i++) {
            PacketBridge.VirtualDisplay display = tag.displays().get(i);
            Component text = TextFormat.parse(placeholders.resolve(owner, settings.lines().get(i).text()));
            if (!display.display().text().equals(text)) {
                display.display().text(text);
                Object metadata = bridge.metadata(display, false);
                for (UUID viewerId : tag.viewers()) {
                    ViewerSession session = sessions.get(viewerId);
                    if (session != null && session.has(tag.ownerId())) {
                        Player viewer = plugin.getServer().getPlayer(viewerId);
                        if (viewer != null && viewer.isOnline()) {
                            bridge.send(viewer, metadata);
                        }
                    }
                }
            }
        }
    }

    private void reconcile(Player owner, NameTag tag) {
        Set<Player> tracked = owner.getTrackedBy();
        Location location = owner.getLocation();
        double rangeSquared = settings.viewDistance() * settings.viewDistance();
        Set<UUID> candidates = new HashSet<>(tag.viewers());
        tracked.forEach(viewer -> candidates.add(viewer.getUniqueId()));
        for (UUID viewerId : candidates) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            ViewerSession session = sessions.get(viewerId);
            if (session == null) {
                tag.viewers().remove(viewerId);
                continue;
            }
            boolean shouldSee = viewer != null && viewer.isOnline() && !viewer.equals(owner)
                    && viewer.getWorld().equals(owner.getWorld()) && tracked.contains(viewer)
                    && viewer.canSee(owner) && viewer.getLocation().distanceSquared(location) <= rangeSquared;
            if (shouldSee && !session.has(tag.ownerId())) {
                List<Object> packets = new ArrayList<>();
                for (PacketBridge.VirtualDisplay display : tag.displays()) {
                    packets.add(bridge.spawn(display, location));
                    Object metadata = bridge.metadata(display, true);
                    if (metadata != null) {
                        packets.add(metadata);
                    }
                }
                session.attach(tag.ownerId(), tag.ids());
                int[] actual = owner.getPassengers().stream().mapToInt(Entity::getEntityId).toArray();
                packets.add(bridge.mount(tag.ownerId(), actual));
                bridge.send(viewer, bridge.bundle(packets));
            } else if (!shouldSee && session.has(tag.ownerId())) {
                detach(viewer, session, tag.ownerId());
            }
            if (shouldSee) {
                tag.viewers().add(viewerId);
            } else {
                tag.viewers().remove(viewerId);
            }
        }
    }

    private boolean visible(Player owner) {
        return owner.isOnline() && !owner.isDead() && owner.getGameMode() != GameMode.SPECTATOR
                && (!settings.hideSneaking() || !owner.isSneaking())
                && (!settings.hideInvisible() || !owner.isInvisible())
                && owner.getMetadata("vanished").stream().noneMatch(MetadataValue::asBoolean);
    }

    private void remove(UUID id) {
        NameTag tag = tags.remove(id);
        if (tag == null) {
            return;
        }
        for (UUID viewerId : tag.viewers()) {
            ViewerSession session = sessions.get(viewerId);
            if (session != null) {
                detach(plugin.getServer().getPlayer(viewerId), session, tag.ownerId());
            }
        }
    }

    private void detach(Player viewer, ViewerSession session, int vehicle) {
        int[] ids = session.detach(vehicle);
        if (ids != null && viewer != null && viewer.isOnline()) {
            bridge.send(viewer, bridge.remove(ids));
        }
    }

    private List<String> names() {
        return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).sorted().toList();
    }

    private void updateNames() {
        List<String> current = names();
        if (!current.equals(onlineNames)) {
            onlineNames = current;
            sessions.values().forEach(session -> session.names(current));
        }
    }

    public void cleanupLegacy(Collection<? extends Entity> entities) {
        NamespacedKey legacy = new NamespacedKey("elyboxnames", "skript_display");
        NamespacedKey own = new NamespacedKey("lavanames", "display");
        for (Entity entity : entities) {
            if (entity instanceof TextDisplay && (entity.getPersistentDataContainer().has(legacy)
                    || entity.getPersistentDataContainer().has(own))) {
                entity.remove();
            }
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        sessions.values().forEach(session -> session.close(true));
        sessions.clear();
        tags.clear();
        dirty.clear();
    }

    public void reconfigure(Settings next) {
        for (UUID id : List.copyOf(tags.keySet())) {
            remove(id);
        }
        settings = next;
        sessions.values().forEach(session -> session.hideVanilla(next.hideVanilla()));
        plugin.getServer().getOnlinePlayers().forEach(this::changed);
    }

    public Map<String, String> status() {
        return Map.of("players", Integer.toString(plugin.getServer().getOnlinePlayers().size()),
                "displays", Integer.toString(tags.values().stream().mapToInt(t -> t.displays().size()).sum()),
                "viewers", Integer.toString(sessions.size()));
    }

    private void warn(Throwable error) {
        long now = System.currentTimeMillis();
        if (now >= nextWarning) {
            nextWarning = now + 60_000;
            plugin.log("packet-failed", Map.of("error", error.toString()));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "&6☀ | &fОшибка LavaNames", error);
        }
    }
}
