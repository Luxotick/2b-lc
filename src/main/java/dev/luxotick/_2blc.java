package dev.luxotick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class _2blc implements ModInitializer {
    private TCPChatServer tcpServer;
    private boolean attemptingReconnect = false;
    private long lastKickTime = 0;
    private static final long RECONNECT_DELAY = 30000; // 30 saniye

    @Override
    public void onInitialize() {
        tcpServer = new TCPChatServer(9090);
        new Thread(tcpServer).start();

        final int ANTI_AFK_INTERVAL_TICKS = 2 * 10;
        final int MOVE_TICKS = 5;
        AtomicInteger antiAfkMoveTicks = new AtomicInteger();

        // Chat mesajlarını işleme
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String chatText = message.getString();
            System.out.println("CHAT mesajı: " + chatText);
            tcpServer.broadcastMessage(chatText);
        });

        ClientReceiveMessageEvents.GAME.register((message, params) -> {
            String gameText = message.getString();
            System.out.println("GAME mesajı: " + gameText);
            tcpServer.broadcastMessage(gameText);
        });

        // Ölüm durumunda otomatik respawn
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.player.isDead()) {
                client.execute(() -> client.player.requestRespawn());
            }
        });

        // Tab listesini güncelleme ve TCP sunucusuna gönderme
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
                    Thread.sleep(30000); // 30 saniyede bir güncelle
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Anti-AFK sistemi
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (client.player.age % ANTI_AFK_INTERVAL_TICKS == 0) {
                    client.execute(() -> {
                        client.player.jump();
                        float newYaw = client.player.getYaw() + 10.0F;
                        client.player.setYaw(newYaw);
                        antiAfkMoveTicks.set(MOVE_TICKS);
                        System.out.println("Anti-AFK eylemi gerçekleştirildi (zıplama, dönme ve ileri gitme).");
                    });
                }

                if (antiAfkMoveTicks.get() > 0) {
                    client.options.forwardKey.setPressed(true);
                    antiAfkMoveTicks.getAndDecrement();
                    if (antiAfkMoveTicks.get() == 0) {
                        client.options.forwardKey.setPressed(false);
                    }
                }
            }
        });

        // 2b2t'ye otomatik bağlanma
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getNetworkHandler() == null && !attemptingReconnect) {
                attemptingReconnect = true;
                client.execute(() -> connectToServer(client));
            }
        });

        // Bağlantı kesildiğinde yeniden bağlanma
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastKickTime = System.currentTimeMillis();
            attemptingReconnect = false;
            System.out.println("Bağlantı kesildi! Yeniden bağlanılıyor...");
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY);
                    connectToServer(MinecraftClient.getInstance());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }

private void connectToServer(MinecraftClient client) {
    if (System.currentTimeMillis() - lastKickTime < RECONNECT_DELAY) return;
    System.out.println("2b2t'ye bağlanılıyor...");
    ServerAddress serverAddress = ServerAddress.parse("2b2t.org:25565");
    client.getNetworkHandler().connect(serverAddress);
}
}
