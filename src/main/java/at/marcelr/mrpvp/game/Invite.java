package at.marcelr.mrpvp.game;

import java.util.UUID;

/**
 * An open invitation.
 *
 * @param id      short number for the chat link (/duel invite &lt;id&gt;), only valid while the server runs
 * @param arena   wanted arena template, or null for any
 * @param options rounds, time limit, hunger, regeneration
 * @param expires epoch ms
 */
public record Invite(int id, UUID from, String fromName, UUID to, String toName, String kit, String arena, DuelOptions options,
                     long expires) {

    public boolean expired(long now) {
        return now >= expires;
    }
}
