package at.marcelr.mrpvp.snapshot;

import at.marcelr.mrpvp.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rule everything depends on: what is not marked as given back is owed - exactly once. */
class SnapshotStoreTest {

    @TempDir
    File folder;
    private Database db;

    @BeforeEach
    void open() throws SQLException {
        db = new Database(new File(folder, "mrpvp.db"), Logger.getLogger("test"));
        db.open();
    }

    @AfterEach
    void close() {
        db.close();
    }

    private static Snapshot sample(UUID player, Snapshot.Phase phase) {
        return new Snapshot(0, player, "Tester", Snapshot.Purpose.DEBUG, null, phase, 1000L,
                new byte[] {1, 2, 3}, new byte[] {4}, new byte[] {5}, new byte[] {6}, null,
                12, 0.5f, 300, 17.5, 2, 18, 3.5f, 0.25f, 300, 0, "effects: []\n",
                "SURVIVAL", false, true, "world", 1.5, 64, -2.5, 90f, 10f, null);
    }

    @Test
    void storedSnapshotComesBackUnchanged() throws SQLException {
        UUID player = UUID.randomUUID();
        Snapshot stored = db.call(c -> SnapshotStore.insert(c, sample(player, Snapshot.Phase.JOIN)));
        assertTrue(stored.id() > 0);
        Snapshot read = db.call(c -> SnapshotStore.byId(c, stored.id()));
        assertNotNull(read);
        assertEquals(player, read.uuid());
        assertArrayEquals(new byte[] {1, 2, 3}, read.inventory());
        assertNull(read.toolbarBelts());
        assertNull(read.gameId());
        assertEquals(17.5, read.health());
        assertEquals(0.5f, read.exp());
        assertTrue(read.allowFlight());
        assertFalse(read.flying());
        assertEquals("world", read.world());
        assertTrue(read.owed());
    }

    @Test
    void owedUntilMarkedAndOnlyMarkedOnce() throws SQLException {
        UUID player = UUID.randomUUID();
        Snapshot stored = db.call(c -> SnapshotStore.insert(c, sample(player, Snapshot.Phase.JOIN)));
        db.call(c -> SnapshotStore.insert(c, sample(player, Snapshot.Phase.END)));

        List<Snapshot> owed = db.call(c -> SnapshotStore.owed(c, player));
        assertEquals(1, owed.size(), "END snapshots are never owed");
        assertEquals(1, (int) db.call(SnapshotStore::owedCount));

        boolean first = db.call(c -> SnapshotStore.markRestored(c, stored.id(), 2000L));
        boolean second = db.call(c -> SnapshotStore.markRestored(c, stored.id(), 3000L));
        assertTrue(first);
        assertFalse(second, "second mark must fail");
        assertEquals(List.of(), db.call(c -> SnapshotStore.owed(c, player)));

        db.call(c -> {
            SnapshotStore.unmarkRestored(c, stored.id());
            return null;
        });
        assertEquals(1, db.call(c -> SnapshotStore.owed(c, player)).size(), "owed again after unmark");
    }

    @Test
    void reopeningKeepsData() throws SQLException {
        UUID player = UUID.randomUUID();
        db.call(c -> SnapshotStore.insert(c, sample(player, Snapshot.Phase.JOIN)));
        db.close();
        db = new Database(new File(folder, "mrpvp.db"), Logger.getLogger("test"));
        db.open();
        assertEquals(1, db.call(c -> SnapshotStore.owed(c, player)).size());
    }
}
