package at.marcelr.mrpvp.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Open invitations and the invite cooldown. Main thread only; no Bukkit types, so it can be tested. */
public final class InviteManager {

    /** Why an invitation could not be sent - the name is the end of the language key "invite-error-...". */
    public enum Refusal { SELF, ALREADY_INVITED, COOLDOWN }

    private final Map<Integer, Invite> invites = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();
    private int nextId = 1;

    /**
     * Opens an invitation.
     *
     * @param cooldownMillis wait since the inviter's last invitation (0 = none)
     * @return the invitation, or the reason it was refused
     */
    public Object create(UUID from, String fromName, UUID to, String toName, String kit, String arena, DuelOptions options,
                         long now, long lifetimeMillis, long cooldownMillis) {
        if (from.equals(to)) {
            return Refusal.SELF;
        }
        for (Invite invite : invites.values()) {
            if (invite.from().equals(from) && invite.to().equals(to) && !invite.expired(now)) {
                return Refusal.ALREADY_INVITED;
            }
        }
        Long last = lastSent.get(from);
        if (cooldownMillis > 0 && last != null && now - last < cooldownMillis) {
            return Refusal.COOLDOWN;
        }
        Invite invite = new Invite(nextId++, from, fromName, to, toName, kit, arena, options, now + lifetimeMillis);
        invites.put(invite.id(), invite);
        lastSent.put(from, now);
        return invite;
    }

    /** Seconds the player still has to wait before inviting again (0 = may invite). */
    public long cooldownLeft(UUID from, long now, long cooldownMillis) {
        Long last = lastSent.get(from);
        if (last == null || cooldownMillis <= 0) {
            return 0;
        }
        return Math.max(0, (cooldownMillis - (now - last) + 999) / 1000);
    }

    public Invite get(int id) {
        return invites.get(id);
    }

    /** Removes and returns the invitation (accepting or declining it). */
    public Invite remove(int id) {
        return invites.remove(id);
    }

    /** Open invitations to the player, newest first. */
    public List<Invite> to(UUID player, long now) {
        List<Invite> result = new ArrayList<>();
        for (Invite invite : invites.values()) {
            if (invite.to().equals(player) && !invite.expired(now)) {
                result.add(invite);
            }
        }
        result.sort(Comparator.comparingLong(Invite::expires).reversed());
        return result;
    }

    /** Removes expired invitations and hands each to {@code expired}. */
    public void expire(long now, Consumer<Invite> expired) {
        Iterator<Invite> it = invites.values().iterator();
        while (it.hasNext()) {
            Invite invite = it.next();
            if (invite.expired(now)) {
                it.remove();
                expired.accept(invite);
            }
        }
    }

    /** Removes every invitation from or to the player (quit, round started). */
    public List<Invite> removeAll(UUID player) {
        List<Invite> removed = new ArrayList<>();
        Iterator<Invite> it = invites.values().iterator();
        while (it.hasNext()) {
            Invite invite = it.next();
            if (invite.from().equals(player) || invite.to().equals(player)) {
                it.remove();
                removed.add(invite);
            }
        }
        return removed;
    }
}
