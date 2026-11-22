package com.pxokie.tiertagger;

import com.pxokie.tiertagger.config.TierTaggerConfig;
import com.pxokie.tiertagger.model.TierList;
import com.pxokie.tiertagger.source.Mojang;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Adds tiers to server chat messages before any chat mod (like Lunar's) sees them. When the sender's
 * tiers still have to be fetched, the message waits a moment (max {@link #MAX_WAIT_MS}); messages
 * always come out in the order they arrived.
 */
public final class ChatDelay {
    private static final long MAX_WAIT_MS = 2000;

    private static final class Pending {
        final Component message;
        final long arrived = System.currentTimeMillis();
        volatile boolean ready = false;

        Pending(Component message) {
            this.message = message;
        }
    }

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static boolean delivering = false;

    /**
     * @return true when the message was taken over (the caller must not show it now)
     */
    public static boolean intercept(Component message) {
        if (delivering) return false;

        TierTaggerConfig config = TierTagger.getManager().getConfig();
        if (!config.isEnabled() || !config.isChatTiers() || config.chatTierLists().isEmpty()) {
            return !QUEUE.isEmpty() && enqueueReady(message);
        }

        String text = message.getString();
        UUID sender = TierTagger.findPlayerInText(text);
        String guess = sender == null ? ChatTiers.guessSender(text) : null;

        boolean needsWait = false;
        CompletableFuture<UUID> lookup = null;
        if (sender != null) {
            if (!allKnown(sender, config.chatTierLists())) {
                needsWait = true;
                lookup = CompletableFuture.completedFuture(sender);
            }
        } else if (guess != null) {
            needsWait = true;
            lookup = Mojang.lookup(guess);
        }

        if (!needsWait) {
            if (QUEUE.isEmpty()) {
                Component decorated = TierTagger.decorateChat(message);
                if (decorated == message) return false;
                deliver(decorated);
                return true;
            }
            return enqueueReady(message);
        }

        Pending p = new Pending(message);
        QUEUE.addLast(p);
        List<TierList> lists = config.chatTierLists();
        lookup.thenCompose(uuid -> {
                    if (uuid == null) return CompletableFuture.completedFuture(null);
                    List<CompletableFuture<?>> fetches = new ArrayList<>();
                    for (TierList list : lists) {
                        if (!TierCache.isKnown(list, uuid)) fetches.add(TierCache.fetchFresh(list, uuid, null).exceptionally(t -> null));
                    }
                    return CompletableFuture.allOf(fetches.toArray(CompletableFuture[]::new));
                })
                .whenComplete((v, t) -> p.ready = true);
        return true;
    }

    private static boolean enqueueReady(Component message) {
        Pending p = new Pending(message);
        p.ready = true;
        QUEUE.addLast(p);
        return true;
    }

    private static boolean allKnown(UUID uuid, List<TierList> lists) {
        for (TierList list : lists) {
            if (!TierCache.isKnown(list, uuid)) return false;
        }
        return true;
    }

    /**
     * Shows the messages at the front of the queue that are ready (or waited long enough).
     */
    public static void tick() {
        long now = System.currentTimeMillis();
        while (!QUEUE.isEmpty()) {
            Pending head = QUEUE.peekFirst();
            if (!head.ready && now - head.arrived < MAX_WAIT_MS) break;
            QUEUE.pollFirst();
            deliver(decorateKnown(head.message));
        }
    }

    /**
     * Adds tiers using whatever is known now, including senders that were looked up through Mojang.
     */
    private static Component decorateKnown(Component message) {
        return TierTagger.decorateChat(message);
    }

    private static void deliver(Component message) {
        delivering = true;
        try {
            Minecraft.getInstance().getChatListener().handleSystemMessage(message, false);
        } finally {
            delivering = false;
        }
    }

    private ChatDelay() {
    }
}
