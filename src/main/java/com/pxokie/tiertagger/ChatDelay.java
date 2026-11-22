package com.pxokie.tiertagger;

import com.pxokie.tiertagger.config.TierTaggerConfig;
import com.pxokie.tiertagger.model.TierList;
import com.pxokie.tiertagger.source.Mojang;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Puts tiers next to every player name in server chat messages, before any chat mod (like Lunar's)
 * sees them. When tiers still have to be fetched, the message waits a moment (max
 * {@link #MAX_WAIT_MS}); messages always come out in the order they arrived.
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
    /**
     * Messages this class already handled, so the chat hook doesn't add tiers a second time.
     */
    private static final Set<Component> HANDLED = Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
    private static boolean delivering = false;

    public static boolean isHandled(Component message) {
        return delivering || HANDLED.remove(message);
    }

    /**
     * @return true when the message was taken over (the caller must not show it now)
     */
    public static boolean intercept(Component message) {
        if (delivering) return false;

        TierTaggerConfig config = TierTagger.getManager().getConfig();
        boolean active = config.isEnabled() && config.isChatTiers() && !config.chatTierLists().isEmpty();
        if (!active) {
            if (QUEUE.isEmpty()) return false;
            enqueue(message, true);
            return true;
        }

        List<TierList> lists = config.chatTierLists();
        String text = message.getString();

        // every player name in the message whose tiers aren't known yet
        List<CompletableFuture<?>> waits = new ArrayList<>();
        for (String word : NameTiers.words(text)) {
            UUID uuid = NameTiers.resolve(word);
            if (uuid != null) {
                CompletableFuture<?> f = fetchMissing(uuid, lists);
                if (f != null) waits.add(f);
            }
        }
        // the sender ("Name » message") may not be on this server instance: ask Mojang who it is
        String sender = ChatTiers.guessSender(text);
        if (sender != null && NameTiers.resolve(sender) == null) {
            waits.add(Mojang.lookup(sender).thenCompose(uuid -> {
                if (uuid == null) return CompletableFuture.completedFuture(null);
                CompletableFuture<?> f = fetchMissing(uuid, lists);
                return f == null ? CompletableFuture.completedFuture(null) : f;
            }));
        }

        if (waits.isEmpty()) {
            if (QUEUE.isEmpty()) {
                Component decorated = decorate(message);
                if (decorated == message) return false;
                deliver(decorated);
                return true;
            }
            enqueue(message, true);
            return true;
        }

        Pending p = enqueue(message, false);
        CompletableFuture.allOf(waits.toArray(CompletableFuture[]::new)).whenComplete((v, t) -> p.ready = true);
        return true;
    }

    @org.jetbrains.annotations.Nullable
    private static CompletableFuture<?> fetchMissing(UUID uuid, List<TierList> lists) {
        List<CompletableFuture<?>> fetches = new ArrayList<>();
        for (TierList list : lists) {
            if (!TierCache.isKnown(list, uuid)) fetches.add(TierCache.fetchFresh(list, uuid, null).exceptionally(t -> null));
        }
        return fetches.isEmpty() ? null : CompletableFuture.allOf(fetches.toArray(CompletableFuture[]::new));
    }

    private static Pending enqueue(Component message, boolean ready) {
        Pending p = new Pending(message);
        p.ready = ready;
        QUEUE.addLast(p);
        return p;
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
            deliver(decorate(head.message));
        }
    }

    public static Component decorate(Component message) {
        TierTaggerConfig config = TierTagger.getManager().getConfig();
        if (!config.isEnabled() || !config.isChatTiers()) return message;
        boolean right = config.getNametagPosition() == TierTaggerConfig.NametagPosition.RIGHT;
        Component decorated = NameTiers.insert(message, config.chatTierLists(), right);
        if (decorated != message) Debug.hit("chat: tiers added", decorated.getString());
        return decorated;
    }

    private static void deliver(Component message) {
        delivering = true;
        HANDLED.add(message);
        try {
            Minecraft.getInstance().getChatListener().handleSystemMessage(message, false);
        } finally {
            delivering = false;
        }
    }

    private ChatDelay() {
    }
}
