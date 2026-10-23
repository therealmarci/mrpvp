package at.marcelr.mrpvp.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class InviteManagerTest {

    private static final DuelOptions OPTIONS = new DuelOptions(1, 10, true, true);

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void refusesSelfDoubleAndCooldown() {
        InviteManager invites = new InviteManager();
        assertEquals(InviteManager.Refusal.SELF, invites.create(a, "A", a, "A", "kit", null, OPTIONS, 0, 30_000, 0));
        assertInstanceOf(Invite.class, invites.create(a, "A", b, "B", "kit", null, OPTIONS, 0, 30_000, 0));
        assertEquals(InviteManager.Refusal.ALREADY_INVITED, invites.create(a, "A", b, "B", "kit", null, OPTIONS, 1000, 30_000, 0));
        // to someone else, but within the cooldown
        assertEquals(InviteManager.Refusal.COOLDOWN, invites.create(a, "A", c, "C", "kit", null, OPTIONS, 5_000, 30_000, 10_000));
        assertEquals(5, invites.cooldownLeft(a, 5_000, 10_000));
        assertInstanceOf(Invite.class, invites.create(a, "A", c, "C", "kit", null, OPTIONS, 10_000, 30_000, 10_000));
    }

    @Test
    void expiresAndRemovesPerPlayer() {
        InviteManager invites = new InviteManager();
        Invite first = (Invite) invites.create(a, "A", b, "B", "kit", null, OPTIONS, 0, 30_000, 0);
        invites.create(c, "C", b, "B", "kit", null, OPTIONS, 20_000, 30_000, 0);
        assertEquals(2, invites.to(b, 25_000).size());

        List<Invite> expired = new ArrayList<>();
        invites.expire(31_000, expired::add);
        assertEquals(List.of(first), expired);
        assertNull(invites.get(first.id()));
        assertEquals(1, invites.to(b, 31_000).size());

        assertEquals(1, invites.removeAll(c).size());
        assertEquals(0, invites.to(b, 31_000).size());
    }
}
