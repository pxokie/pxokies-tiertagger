package com.pxokie.tiertagger;

import com.pxokie.tiertagger.config.TierTaggerConfig;
import com.pxokie.tiertagger.mixin.ChatComponentAccessor;
import com.pxokie.tiertagger.model.TierList;
import com.pxokie.tiertagger.source.Mojang;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiers in chat for players the client doesn't know yet (e.g. players in another lobby): looks the
 * sender up in the background and adds the tiers to the message once they are known.
 */
public final class ChatTiers {
    /**
     * A name right before a chat separator, like "Name » hi", "[VIP] Name: hi" or "&lt;Name&gt; hi".
     */
    private static final Pattern SENDER = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})\\s*(?:»|:|>|›|➜|\\|)");

    @Nullable
    public static String guessSender(String text) {
        Matcher m = SENDER.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Looks up the sender and their tiers, then adds the tiers to the message that is already in chat.
     */
    public static void resolveLater(Component message, String senderName) {
        resolveLater(message, Mojang.lookup(senderName));
    }

    public static void resolveLater(Component message, UUID sender) {
        resolveLater(message, CompletableFuture.completedFuture(sender));
    }

    private static void resolveLater(Component message, CompletableFuture<UUID> senderFuture) {
        TierTaggerConfig config = TierTagger.getManager().getConfig();
        List<TierList> lists = config.chatTierLists();
        if (lists.isEmpty()) return;

        senderFuture
                .thenCompose(uuid -> {
                    if (uuid == null || !TierTagger.showsTiersFor(uuid)) return CompletableFuture.completedFuture((UUID) null);

                    List<CompletableFuture<?>> fetches = new ArrayList<>();
                    for (TierList list : lists) {
                        if (!TierCache.isKnown(list, uuid)) {
                            fetches.add(TierCache.fetchFresh(list, uuid, null).exceptionally(t -> null));
                        }
                    }
                    return CompletableFuture.allOf(fetches.toArray(CompletableFuture[]::new)).thenApply(v -> uuid);
                })
                .thenAccept(uuid -> {
                    if (uuid == null) return;
                    Minecraft.getInstance().execute(() -> replace(message, uuid, lists));
                })
                .exceptionally(t -> null);
    }

    private static void replace(Component original, UUID uuid, List<TierList> lists) {
        MutableComponent tiers = TierTagger.buildTiers(uuid, lists, TierTagger.Placement.LEFT);
        if (tiers == null) return;

        ChatComponent chat = Minecraft.getInstance().gui.getChat();
        List<GuiMessage> messages = ((ChatComponentAccessor) chat).pxtiertagger$getAllMessages();

        for (int i = 0; i < messages.size(); i++) {
            GuiMessage msg = messages.get(i);
            if (msg.content() == original) {
                messages.set(i, new GuiMessage(msg.addedTime(), tiers.append(original), msg.signature(), msg.tag()));
                chat.rescaleChat();
                Debug.hit("chat: tiers added later", tiers.getString() + original.getString());
                return;
            }
        }
    }

    private ChatTiers() {
    }
}
