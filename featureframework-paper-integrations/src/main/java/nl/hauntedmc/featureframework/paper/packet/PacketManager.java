package nl.hauntedmc.featureframework.paper.packet;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Manages sending packets to players, allowing unicast, multicast, and broadcast.
 */
public class PacketManager {

    /**
     * Sends packets to a single player.
     *
     * @param player  The recipient player.
     * @param packets The packets to send.
     */
    public static void sendUnicast(Player player, Packet... packets) {
        for (Packet packet : packets) {
            packet.sendTo(player);
        }
    }

    /**
     * Sends packets to multiple players in range of a specific player.
     *
     * @param players The center player.
     * @param packets The packets to send.
     */
    public static void sendMulticast(List<Player> players, Packet... packets) {
        for (Player target : players) {
            for (Packet packet : packets) {
                packet.sendTo(target);
            }
        }
    }

    /**
     * Sends packets to all online players.
     *
     * @param packets The packets to send.
     */
    public static void sendBroadcast(Packet... packets) {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            for (Packet packet : packets) {
                packet.sendTo(player);
            }
        }
    }
}
