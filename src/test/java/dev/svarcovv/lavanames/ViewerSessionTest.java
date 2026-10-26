package dev.svarcovv.lavanames;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ViewerSessionTest {
    private final PacketBridge bridge = mock(PacketBridge.class);
    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final ViewerSession session = new ViewerSession(bridge, channel, true);

    @AfterEach
    void cleanup() {
        session.close(false);
        channel.runPendingTasks();
        channel.finishAndReleaseAll();
    }

    @Test
    void preservesRealPassengersAndAddsVirtualPassengers() {
        Object packet = new Object();
        Object rewritten = new Object();
        when(bridge.isMount(packet)).thenReturn(true);
        when(bridge.vehicle(packet)).thenReturn(7);
        when(bridge.passengers(packet)).thenReturn(new int[]{10, 11});
        when(bridge.mount(eq(7), any())).thenReturn(rewritten);
        session.attach(7, new int[]{20, 21});
        assertEquals(List.of(rewritten), session.rewrite(packet));
        verify(bridge).mount(7, new int[]{10, 11, 20, 21});
    }

    @Test
    void doesNotDuplicateVirtualPassengersOnPluginMountPackets() {
        Object packet = new Object();
        when(bridge.isMount(packet)).thenReturn(true);
        when(bridge.vehicle(packet)).thenReturn(7);
        when(bridge.passengers(packet)).thenReturn(new int[]{10, 20});
        when(bridge.mount(eq(7), any())).thenReturn(new Object());
        session.attach(7, new int[]{20});
        session.rewrite(packet);
        verify(bridge).mount(7, new int[]{10, 20});
    }

    @Test
    void destroysVirtualPassengersWhenOwnerLeavesTracking() {
        Object packet = new Object();
        Object remove = new Object();
        when(bridge.isRemoval(packet)).thenReturn(true);
        when(bridge.removed(packet)).thenReturn(new int[]{7});
        when(bridge.remove(any())).thenReturn(remove);
        session.attach(7, new int[]{20, 21});
        assertEquals(List.of(packet, remove), session.rewrite(packet));
        assertFalse(session.has(7));
        verify(bridge).remove(new int[]{20, 21});
    }

    @Test
    void restoresFallbackMembershipWhenAnotherPluginRemovesTeam() {
        open(List.of("Steve"));
        Object add = team("tab", 0, List.of("Steve"));
        Object remove = team("tab", 1, List.of());
        session.rewrite(add);
        List<Object> result = session.rewrite(remove);
        assertEquals(2, result.size());
        verify(bridge, atLeastOnce()).fallbackTeam(anyString(), eq(3), argThat(names -> names.contains("Steve")));
    }

    @Test
    void ignoresOutdatedRemovalAfterPlayerMovedToAnotherTeam() {
        open(List.of("Steve"));
        session.rewrite(team("old", 0, List.of("Steve")));
        session.rewrite(team("new", 0, List.of("Steve")));
        assertEquals(1, session.rewrite(team("old", 4, List.of("Steve"))).size());
    }

    @Test
    void handlesPassengerChangesInsideBundles() {
        Object bundle = new Object();
        Object mount = new Object();
        Object output = new Object();
        when(bridge.isBundle(bundle)).thenReturn(true);
        when(bridge.packets(bundle)).thenReturn(List.of(mount));
        when(bridge.isMount(mount)).thenReturn(true);
        when(bridge.vehicle(mount)).thenReturn(7);
        when(bridge.passengers(mount)).thenReturn(new int[0]);
        when(bridge.mount(eq(7), any())).thenReturn(output);
        when(bridge.bundle(anyList())).thenReturn(new Object());
        session.attach(7, new int[]{20});
        session.rewrite(bundle);
        verify(bridge).bundle(List.of(output));
    }

    @Test
    void turningOffHidingRestoresOriginalTeamParameters() {
        open(List.of("Steve"));
        Object packet = team("tab", 0, List.of("Steve"));
        Object original = new Object();
        when(bridge.teamUpdate(packet)).thenReturn(new PacketBridge.TeamUpdate("tab", 0, List.of("Steve"), true));
        when(bridge.restoreTeam(packet)).thenReturn(original);
        session.rewrite(packet);
        while (channel.readOutbound() != null) {}
        session.hideVanilla(false);
        channel.runPendingTasks();
        boolean restored = false;
        Object next;
        while ((next = channel.readOutbound()) != null) {
            restored |= next == original;
        }
        assertTrue(restored);
    }

    private void open(Collection<String> names) {
        when(bridge.fallbackTeam(anyString(), anyInt(), anyCollection())).thenAnswer(call -> new Object());
        channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
        session.open(List.of(), names);
        channel.runPendingTasks();
    }

    private Object team(String name, int mode, Collection<String> players) {
        Object packet = new Object();
        when(bridge.isTeam(packet)).thenReturn(true);
        when(bridge.teamUpdate(packet)).thenReturn(new PacketBridge.TeamUpdate(name, mode, players, false));
        when(bridge.hideTeam(packet)).thenReturn(packet);
        return packet;
    }
}
