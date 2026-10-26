package dev.svarcovv.lavanames;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PacketBridge {
    public record VirtualDisplay(Object handle, TextDisplay display) {}
    public record TeamUpdate(String name, int mode, Collection<String> players, boolean hasParameters) {}

    private final Class<?> entity = type("net.minecraft.world.entity.Entity");
    private final Class<?> entityType = type("net.minecraft.world.entity.EntityType");
    private final Class<?> addPacket = type("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
    private final Class<?> mountPacket = type("net.minecraft.network.protocol.game.ClientboundSetPassengersPacket");
    private final Class<?> removePacket = type("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
    private final Class<?> teamPacket = type("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
    private final Class<?> teamParameters = type("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket$Parameters");
    private final Class<?> bundlePacket = type("net.minecraft.network.protocol.game.ClientboundBundlePacket");
    private final Class<?> playerTeam = type("net.minecraft.world.scores.PlayerTeam");
    private final Class<?> nmsScoreboard = type("net.minecraft.world.scores.Scoreboard");
    private final Class<?> friendlyBuffer = type("net.minecraft.network.FriendlyByteBuf");
    private final Constructor<?> textConstructor;
    private final Constructor<?> addConstructor;
    private final Constructor<?> metadataConstructor;
    private final Constructor<?> removeConstructor;
    private final Constructor<?> mountConstructor;
    private final Constructor<?> bufferConstructor;
    private final Constructor<?> teamConstructor;
    private final Constructor<?> parametersConstructor;
    private final Constructor<?> playerTeamConstructor;
    private final Constructor<?> bundleConstructor;
    private final Method playerHandle;
    private final Method worldHandle;
    private final Method scoreboardHandle;
    private final Method bukkitEntity;
    private final Method entityData;
    private final Method fullData;
    private final Method dirtyData;
    private final Method send;
    private final Method mountVehicle;
    private final Method mountPassengers;
    private final Method removedIds;
    private final Method teamName;
    private final Method teamPlayers;
    private final Method getParameters;
    private final Method subPackets;
    private final Method teamModify;
    private final Field listenerField;
    private final Field connectionField;
    private final Field channelField;
    private final Field teamMode;
    private final Field visibility;
    private final List<Field> parameterFields;
    private final Object displayType;
    private final Object zeroVelocity;
    private final Object emptyTeam;

    public PacketBridge() throws ReflectiveOperationException {
        Class<?> serverPlayer = type("net.minecraft.server.level.ServerPlayer");
        Class<?> listener = type("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> data = type("net.minecraft.network.syncher.SynchedEntityData");
        textConstructor = type("net.minecraft.world.entity.Display$TextDisplay").getConstructor(entityType, type("net.minecraft.world.level.Level"));
        addConstructor = addPacket.getConstructor(int.class, UUID.class, double.class, double.class, double.class,
                float.class, float.class, entityType, int.class, type("net.minecraft.world.phys.Vec3"), double.class);
        metadataConstructor = type("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket").getConstructor(int.class, List.class);
        removeConstructor = removePacket.getConstructor(int[].class);
        bufferConstructor = friendlyBuffer.getConstructor(ByteBuf.class);
        mountConstructor = accessible(mountPacket.getDeclaredConstructor(friendlyBuffer));
        teamConstructor = accessible(teamPacket.getDeclaredConstructor(String.class, int.class, Optional.class, Collection.class));
        parametersConstructor = teamParameters.getConstructor(playerTeam);
        playerTeamConstructor = playerTeam.getConstructor(nmsScoreboard, String.class);
        bundleConstructor = bundlePacket.getConstructor(Iterable.class);
        playerHandle = type("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
        worldHandle = type("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
        scoreboardHandle = type("org.bukkit.craftbukkit.scoreboard.CraftScoreboard").getMethod("getHandle");
        bukkitEntity = entity.getMethod("getBukkitEntity");
        entityData = entity.getMethod("getEntityData");
        fullData = data.getMethod("getNonDefaultValues");
        dirtyData = data.getMethod("packDirty");
        send = listener.getMethod("send", type("net.minecraft.network.protocol.Packet"));
        listenerField = serverPlayer.getField("connection");
        connectionField = listener.getField("connection");
        channelField = type("net.minecraft.network.Connection").getField("channel");
        mountVehicle = mountPacket.getMethod("getVehicle");
        mountPassengers = mountPacket.getMethod("getPassengers");
        removedIds = removePacket.getMethod("getEntityIds");
        teamName = teamPacket.getMethod("getName");
        teamPlayers = teamPacket.getMethod("getPlayers");
        getParameters = teamPacket.getMethod("getParameters");
        teamMode = accessible(teamPacket.getDeclaredField("method"));
        visibility = accessible(teamParameters.getDeclaredField("nametagVisibility"));
        parameterFields = new ArrayList<>();
        for (Field field : teamParameters.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                parameterFields.add(accessible(field));
            }
        }
        subPackets = type("net.minecraft.network.protocol.BundlePacket").getMethod("subPackets");
        teamModify = teamPacket.getMethod("createAddOrModifyPacket", playerTeam, boolean.class);
        displayType = entityType.getField("TEXT_DISPLAY").get(null);
        zeroVelocity = type("net.minecraft.world.phys.Vec3").getField("ZERO").get(null);
        emptyTeam = playerTeamConstructor.newInstance(nmsScoreboard.getConstructor().newInstance(), "lavanames");
        playerTeam.getMethod("setSeeFriendlyInvisibles", boolean.class).invoke(emptyTeam, false);
    }

    public VirtualDisplay createDisplay(Player owner, Settings settings, Settings.Line line, Component text) {
        try {
            Object handle = textConstructor.newInstance(displayType, worldHandle.invoke(owner.getWorld()));
            TextDisplay display = (TextDisplay) bukkitEntity.invoke(handle);
            display.setPersistent(false);
            display.setGravity(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0));
            display.setShadowed(settings.shadow());
            display.setSeeThrough(settings.seeThrough());
            display.setLineWidth(settings.lineWidth());
            display.setViewRange((float) (settings.viewDistance() / 64));
            display.setInterpolationDuration(2);
            display.setInterpolationDelay(0);
            display.setTeleportDuration(0);
            display.setTransformation(new Transformation(new Vector3f(0, line.height(), 0), new Quaternionf(),
                    new Vector3f(line.scale()), new Quaternionf()));
            display.text(text);
            dirtyData.invoke(entityData.invoke(handle));
            return new VirtualDisplay(handle, display);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Object spawn(VirtualDisplay display, Location location) {
        try {
            return addConstructor.newInstance(display.display().getEntityId(), display.display().getUniqueId(),
                    location.getX(), location.getY(), location.getZ(), 0f, 0f, displayType, 0, zeroVelocity, 0d);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Object metadata(VirtualDisplay display, boolean full) {
        try {
            Object data = (full ? fullData : dirtyData).invoke(entityData.invoke(display.handle()));
            return data == null ? null : metadataConstructor.newInstance(display.display().getEntityId(), data);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Object remove(int[] ids) {
        try {
            return removeConstructor.newInstance((Object) ids);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Object mount(int vehicle, int[] passengers) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writeVarInt(buffer, vehicle);
            writeVarInt(buffer, passengers.length);
            for (int id : passengers) {
                writeVarInt(buffer, id);
            }
            return mountConstructor.newInstance(bufferConstructor.newInstance(buffer));
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        } finally {
            buffer.release();
        }
    }

    public void send(Player player, Object packet) {
        if (packet == null) {
            return;
        }
        try {
            send.invoke(listenerField.get(playerHandle.invoke(player)), packet);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Channel channel(Player player) {
        try {
            return (Channel) channelField.get(connectionField.get(listenerField.get(playerHandle.invoke(player))));
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public boolean isMount(Object packet) { return mountPacket.isInstance(packet); }
    public boolean isRemoval(Object packet) { return removePacket.isInstance(packet); }
    public boolean isTeam(Object packet) { return teamPacket.isInstance(packet); }
    public boolean isBundle(Object packet) { return bundlePacket.isInstance(packet); }

    public int vehicle(Object packet) {
        return (int) invoke(mountVehicle, packet);
    }

    public int[] passengers(Object packet) {
        return (int[]) invoke(mountPassengers, packet);
    }

    public int[] removed(Object packet) {
        return ((it.unimi.dsi.fastutil.ints.IntList) invoke(removedIds, packet)).toIntArray();
    }

    @SuppressWarnings("unchecked")
    public Iterable<Object> packets(Object packet) {
        return (Iterable<Object>) invoke(subPackets, packet);
    }

    public Object bundle(List<Object> packets) {
        try {
            return bundleConstructor.newInstance(packets);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    @SuppressWarnings("unchecked")
    public TeamUpdate teamUpdate(Object packet) {
        try {
            return new TeamUpdate((String) teamName.invoke(packet), teamMode.getInt(packet),
                    (Collection<String>) teamPlayers.invoke(packet), ((Optional<?>) getParameters.invoke(packet)).isPresent());
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Object hideTeam(Object packet) {
        try {
            Optional<?> parameters = (Optional<?>) getParameters.invoke(packet);
            if (parameters.isEmpty()) {
                return packet;
            }
            Object copy = parametersConstructor.newInstance(emptyTeam);
            for (Field field : parameterFields) {
                field.set(copy, field.get(parameters.get()));
            }
            visibility.set(copy, "never");
            return teamConstructor.newInstance(teamName.invoke(packet), teamMode.getInt(packet), Optional.of(copy), teamPlayers.invoke(packet));
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Object restoreTeam(Object packet) {
        try {
            return teamConstructor.newInstance(teamName.invoke(packet), 2, getParameters.invoke(packet), List.of());
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public Object fallbackTeam(String name, int mode, Collection<String> players) {
        try {
            Object parameters = parametersConstructor.newInstance(emptyTeam);
            visibility.set(parameters, "never");
            return teamConstructor.newInstance(name, mode, mode == 0 ? Optional.of(parameters) : Optional.empty(), players);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    public List<Object> scoreboardTeams(Scoreboard scoreboard) {
        try {
            Collection<?> teams = (Collection<?>) nmsScoreboard.getMethod("getPlayerTeams").invoke(scoreboardHandle.invoke(scoreboard));
            List<Object> packets = new ArrayList<>();
            for (Object team : teams) {
                packets.add(teamModify.invoke(null, team, true));
            }
            return packets;
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    private static void writeVarInt(ByteBuf buffer, int value) {
        while ((value & ~0x7F) != 0) {
            buffer.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buffer.writeByte(value);
    }

    private static Object invoke(Method method, Object receiver) {
        try {
            return method.invoke(receiver);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException error) {
            throw failure(error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T object) {
        object.setAccessible(true);
        return object;
    }

    private static IllegalStateException failure(ReflectiveOperationException error) {
        return new IllegalStateException("Paper 1.21.4: " + error, error);
    }
}
