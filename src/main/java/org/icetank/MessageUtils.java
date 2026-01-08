package org.icetank;


import com.zenith.Proxy;
import com.zenith.cache.data.entity.Entity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.Sound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

/*
 * @author IceTank
 * @since 05.01.2026
 */
public class MessageUtils {
    public static class TextColors {
        public static final TextColor RED = TextColor.color(255, 85, 85);
        public static final TextColor GREEN = TextColor.color(85, 255, 85);
        public static final TextColor YELLOW = TextColor.color(255, 255, 85);
        public static final TextColor BLUE = TextColor.color(85, 85, 255);
        public static final TextColor AQUA = TextColor.color(85, 255, 255);
        public static final TextColor LIGHT_PURPLE = TextColor.color(255, 85, 255);
        public static final TextColor GOLD = TextColor.color(255, 170, 0);
        public static final TextColor GRAY = TextColor.color(170, 170, 170);
        public static final TextColor DARK_GRAY = TextColor.color(85, 85, 85);
        public static final TextColor BLACK = TextColor.color(0, 0, 0);
        public static final TextColor WHITE = TextColor.color(255, 255, 255);
    }

    public static void broadcastMessage(Component message) {
        Proxy.getInstance().getActiveConnections().forEach(connection -> {
            connection.sendAsyncMessage(message);
        });
    }

    public static void broadcastMessage(Component message, boolean overlay) {
        if (overlay) {
            Proxy.getInstance().getActiveConnections().forEach(connection -> {
                connection.sendAsync(new ClientboundSystemChatPacket(message, true));
            });
        } else {
            broadcastMessage(message);
        }
    }

    public static void broadcastSound(Sound sound, SoundCategory category) {
        Proxy.getInstance().getActiveConnections().forEach(connection -> {
            Entity cameraEntity = connection.getCameraTarget();
            Vector3d position;
            if (cameraEntity != null) {
                position = cameraEntity.position();
            } else {
                position = connection.getSpectatorPlayerCache().getThePlayer().position();
            }
            connection.sendAsync(
                    new ClientboundSoundPacket(sound, category,
                            position.getX(), position.getY(), position.getZ(), 1.0f, 1.0f, 0)
            );
        });
    }

    public static void broadcastPingSound() {
        broadcastSound(BuiltinSound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER);
    }
}
