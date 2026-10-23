package at.marcelr.mrpvp.game;

import at.marcelr.mrpvp.MRPvp;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Players excluded from duels (by an admin, with an optional end). Kept in memory - checked on every invitation -
 * and written to the bans table.
 */
public final class Bans {

    /**
     * @param until epoch ms, null = permanent
     */
    public record Ban(UUID uuid, String name, Long until, String reason, String by, long createdAt) {
        public boolean active(long now) {
            return until == null || now < until;
        }
    }

    private final MRPvp plugin;
    private final Map<UUID, Ban> bans = new ConcurrentHashMap<>();

    public Bans(MRPvp plugin) {
        this.plugin = plugin;
    }

    public void load() {
        bans.clear();
        try {
            plugin.database().call(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, until, reason, by, created_at FROM bans");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long until = rs.getLong(3);
                        Ban ban = new Ban(UUID.fromString(rs.getString(1)), rs.getString(2), rs.wasNull() ? null : until,
                                rs.getString(4), rs.getString(5), rs.getLong(6));
                        bans.put(ban.uuid(), ban);
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the duel bans", e);
        }
    }

    /** The active ban of a player, or null (expired ones are removed on the way). */
    public Ban get(UUID player) {
        Ban ban = bans.get(player);
        if (ban != null && !ban.active(System.currentTimeMillis())) {
            unban(player);
            return null;
        }
        return ban;
    }

    public List<Ban> all() {
        long now = System.currentTimeMillis();
        return bans.values().stream().filter(b -> b.active(now)).toList();
    }

    public void ban(UUID player, String name, Long until, String reason, String by) {
        Ban ban = new Ban(player, name, until, reason, by, System.currentTimeMillis());
        bans.put(player, ban);
        plugin.database().submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO bans (uuid, name, until, reason, by, "
                    + "created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, player.toString());
                ps.setString(2, name);
                if (until == null) {
                    ps.setNull(3, Types.INTEGER);
                } else {
                    ps.setLong(3, until);
                }
                ps.setString(4, reason);
                ps.setString(5, by);
                ps.setLong(6, ban.createdAt());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void unban(UUID player) {
        if (bans.remove(player) == null) {
            return;
        }
        plugin.database().submit(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM bans WHERE uuid = ?")) {
                ps.setString(1, player.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }
}
