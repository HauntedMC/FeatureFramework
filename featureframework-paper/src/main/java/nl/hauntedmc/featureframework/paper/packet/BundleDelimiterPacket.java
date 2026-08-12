package nl.hauntedmc.featureframework.paper.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import org.bukkit.entity.Player;


public class BundleDelimiterPacket implements Packet {

    private final WrapperPlayServerBundle bundlePacket;

    public BundleDelimiterPacket() {
        this.bundlePacket = new WrapperPlayServerBundle();
    }

    @Override
    public void sendTo(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, bundlePacket);
    }
}
