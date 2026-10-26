package dev.svarcovv.lavanames;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViewerSession extends ChannelDuplexHandler {
    private static final String HANDLER = "lavanames";
    private final String fallback = "ln_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final PacketBridge bridge;
    private final Channel channel;
    private boolean hideVanilla;
    private final Map<Integer, int[]> mounts = new ConcurrentHashMap<>();
    private final Map<String, String> membership = new HashMap<>();
    private final Map<String, Object> originals = new HashMap<>();
    private final Set<String> managed = new HashSet<>();
    private volatile boolean closed;
    private volatile Throwable failure;

    public ViewerSession(PacketBridge bridge, Channel channel, boolean hideVanilla) {
        this.bridge = bridge;
        this.channel = channel;
        this.hideVanilla = hideVanilla;
    }

    public void open(List<Object> existingTeams, Collection<String> names) {
        channel.eventLoop().execute(() -> {
            if (closed || !channel.isActive()) {
                return;
            }
            try {
                channel.pipeline().addBefore("packet_handler", HANDLER, this);
                for (Object packet : existingTeams) {
                    PacketBridge.TeamUpdate update = bridge.teamUpdate(packet);
                    originals.put(update.name(), bridge.restoreTeam(packet));
                    update.players().forEach(name -> membership.put(name, update.name()));
                    if (hideVanilla) {
                        channel.write(bridge.restoreTeam(packet));
                    }
                }
                if (hideVanilla) {
                    channel.write(bridge.fallbackTeam(fallback, 0, List.of()));
                }
                updateNames(names);
                channel.flush();
            } catch (RuntimeException error) {
                failure = error;
            }
        });
    }

    public void names(Collection<String> names) {
        List<String> snapshot = List.copyOf(names);
        channel.eventLoop().execute(() -> {
            if (!closed) {
                updateNames(snapshot);
                channel.flush();
            }
        });
    }

    private void updateNames(Collection<String> names) {
        managed.clear();
        managed.addAll(names);
        if (!hideVanilla) {
            return;
        }
        List<String> add = new ArrayList<>();
        for (String name : names) {
            if (!membership.containsKey(name)) {
                membership.put(name, fallback);
                add.add(name);
            }
        }
        List<String> remove = membership.entrySet().stream()
                .filter(e -> e.getValue().equals(fallback) && !managed.contains(e.getKey()))
                .map(Map.Entry::getKey).toList();
        remove.forEach(membership::remove);
        if (!remove.isEmpty()) {
            channel.write(bridge.fallbackTeam(fallback, 4, remove));
        }
        if (!add.isEmpty()) {
            channel.write(bridge.fallbackTeam(fallback, 3, add));
        }
    }

    public void attach(int vehicle, int[] ids) {
        mounts.put(vehicle, ids.clone());
    }

    public int[] detach(int vehicle) {
        return mounts.remove(vehicle);
    }

    public boolean has(int vehicle) {
        return mounts.containsKey(vehicle);
    }

    public int[] attached(int vehicle) {
        return mounts.get(vehicle);
    }

    public Set<Integer> vehicles() {
        return Set.copyOf(mounts.keySet());
    }

    public Throwable takeFailure() {
        Throwable error = failure;
        failure = null;
        return error;
    }

    public void hideVanilla(boolean value) {
        channel.eventLoop().execute(() -> {
            if (closed || hideVanilla == value) {
                return;
            }
            hideVanilla = value;
            if (value) {
                channel.write(bridge.fallbackTeam(fallback, 0, List.of()));
                originals.values().stream().map(bridge::hideTeam).forEach(packet -> channel.pipeline().context(this).write(packet));
                updateNames(List.copyOf(managed));
            } else {
                channel.write(bridge.fallbackTeam(fallback, 1, List.of()));
                originals.values().forEach(packet -> channel.pipeline().context(this).write(packet));
                membership.values().removeIf(fallback::equals);
            }
            channel.flush();
        });
    }

    @Override
    public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
        if (closed) {
            context.write(packet, promise);
            return;
        }
        try {
            List<Object> output = rewrite(packet);
            if (output.size() == 1) {
                context.write(output.getFirst(), promise);
            } else {
                context.write(bridge.bundle(output), promise);
            }
        } catch (RuntimeException error) {
            failure = error;
            context.write(packet, promise);
        }
    }

    List<Object> rewrite(Object packet) {
        if (bridge.isBundle(packet)) {
            List<Object> nested = new ArrayList<>();
            for (Object child : bridge.packets(packet)) {
                nested.addAll(rewrite(child));
            }
            return List.of(bridge.bundle(nested));
        }
        if (bridge.isMount(packet)) {
            int vehicle = bridge.vehicle(packet);
            int[] ids = mounts.get(vehicle);
            if (ids != null) {
                int[] actual = bridge.passengers(packet);
                int[] combined = Arrays.copyOf(actual, actual.length + ids.length);
                int size = actual.length;
                for (int id : ids) {
                    if (Arrays.stream(actual).noneMatch(value -> value == id)) {
                        combined[size++] = id;
                    }
                }
                return List.of(bridge.mount(vehicle, Arrays.copyOf(combined, size)));
            }
        }
        if (bridge.isRemoval(packet)) {
            List<Integer> virtual = new ArrayList<>();
            for (int vehicle : bridge.removed(packet)) {
                int[] ids = mounts.remove(vehicle);
                if (ids != null) {
                    Arrays.stream(ids).forEach(virtual::add);
                }
            }
            if (!virtual.isEmpty()) {
                return List.of(packet, bridge.remove(virtual.stream().mapToInt(Integer::intValue).toArray()));
            }
        }
        if (bridge.isTeam(packet)) {
            PacketBridge.TeamUpdate update = bridge.teamUpdate(packet);
            if (update.name().equals(fallback)) {
                return List.of(packet);
            }
            if (update.hasParameters()) {
                originals.put(update.name(), bridge.restoreTeam(packet));
            }
            Set<String> lost = new HashSet<>();
            if (update.mode() == 1) {
                originals.remove(update.name());
                membership.entrySet().removeIf(entry -> {
                    if (entry.getValue().equals(update.name())) {
                        lost.add(entry.getKey());
                        return true;
                    }
                    return false;
                });
            } else if (update.mode() == 0 || update.mode() == 3) {
                for (String name : update.players()) {
                    membership.put(name, update.name());
                }
            } else if (update.mode() == 4) {
                for (String name : update.players()) {
                    if (membership.remove(name, update.name())) {
                        lost.add(name);
                    }
                }
            }
            lost.retainAll(managed);
            if (hideVanilla && !lost.isEmpty()) {
                lost.forEach(name -> membership.put(name, fallback));
                return List.of(bridge.hideTeam(packet), bridge.fallbackTeam(fallback, 3, lost));
            }
            return List.of(hideVanilla ? bridge.hideTeam(packet) : packet);
        }
        return List.of(packet);
    }

    public void close(boolean restore) {
        closed = true;
        int[] ids = mounts.values().stream().flatMapToInt(Arrays::stream).toArray();
        mounts.clear();
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER) == this) {
                channel.pipeline().remove(this);
            }
            if (restore && channel.isActive()) {
                if (ids.length > 0) {
                    channel.write(bridge.remove(ids));
                }
                if (hideVanilla) {
                    channel.write(bridge.fallbackTeam(fallback, 1, List.of()));
                    originals.values().forEach(channel::write);
                }
                channel.flush();
            }
            originals.clear();
            membership.clear();
            managed.clear();
        });
    }
}
