package nl.hauntedmc.featureframework.paper.packet;

import org.bukkit.entity.Player;

/**
 * Interface for all packet types, enabling abstraction from PacketEvents or other libraries.
 */
public interface Packet {
    void sendTo(Player player);
}
