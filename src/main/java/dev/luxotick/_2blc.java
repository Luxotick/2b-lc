package dev.luxotick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class _2blc implements ModInitializer {
    private TCPChatServer tcpServer;
    @Override
    public void onInitialize() {
        tcpServer = new TCPChatServer(9090);
        new Thread(tcpServer).start();

        final int ANTI_AFK_INTERVAL_TICKS = 2 * 10;
        // Number of ticks to simulate forward movement.
        final int MOVE_TICKS = 5;

        // Counter used to simulate holding the forward key.
        AtomicInteger antiAfkMoveTicks = new AtomicInteger();

        ClientReceiveMessageEvents.CHAT.register(
                ( message,  signedMessage,  sender,  params,  receptionTimestamp) -> {
                    String chatText = message.getString();
                    System.out.println("CHAT mesajı: " + chatText);
                    tcpServer.broadcastMessage(chatText);
                }
        );

        ClientReceiveMessageEvents.GAME.register(
                ( message,  params) -> {
                    String gameText = message.getString();
                    System.out.println("GAME mesajı: " + gameText);
                    tcpServer.broadcastMessage(gameText);
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.player.isDead()) {
                client.execute(() -> client.player.requestRespawn());
            }
        });

        new Thread(() -> {
            while (true) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getNetworkHandler() != null) {
                    Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
                    if (playerList != null && !playerList.isEmpty()) {
                        StringBuilder sb = new StringBuilder("Tab List: ");
                        for (PlayerListEntry entry : playerList) {
                            sb.append(entry.getProfile().getName()).append(" ");
                        }
                        String tabListMessage = sb.toString();
                        System.out.println(tabListMessage);
                        tcpServer.broadcastMessage(tabListMessage);
                    }
                }
                try {
                    Thread.sleep(30000); // Update every 30 seconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Anti-AFK routine:
        // Every ANTI_AFK_INTERVAL_TICKS, perform a small action (jump + slight rotate)
        // and simulate forward movement for a few ticks to prevent the server from marking
        // the client as idle.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (client.player.age % ANTI_AFK_INTERVAL_TICKS == 0) {
                    client.execute(() -> {
                        client.player.jump();
                        // Rotate the player's view slightly (by 10 degrees).
                        float newYaw = client.player.getYaw() + 10.0F;
                        client.player.setYaw(newYaw);
                        antiAfkMoveTicks.set(MOVE_TICKS);
                        System.out.println("Performed anti-AFK action (jump, rotate, and move forward).");
                    });
                }

                if (antiAfkMoveTicks.get() > 0) {
                    // Mark the forward key as pressed.
                    client.options.forwardKey.setPressed(true);
                    antiAfkMoveTicks.getAndDecrement();
                    if (antiAfkMoveTicks.get() == 0) {
                        client.options.forwardKey.setPressed(false);
                    }
                }
            }
        });
    }
}
