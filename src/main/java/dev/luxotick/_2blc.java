package dev.luxotick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.Collection;

public class _2blc implements ModInitializer {
    private TCPChatServer tcpServer;
    @Override
    public void onInitialize() {
        tcpServer = new TCPChatServer(9090);
        new Thread(tcpServer).start();

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
    }
}
