package at.marcelr.mrpvp.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite database in one file (plugins/MRpvp/mrpvp.db). One storage thread owns the only connection, so every
 * statement runs in the order it was made and SQLite never sees two writers. No Bukkit types in here - the tests
 * run it against a temporary file.
 * <p>
 * Most work is handed off with {@link #submit}. Steps that must be on disk before the game goes on (a player's
 * state before their inventory is cleared, "given back" before it is given back) wait with {@link #call}.
 */
public final class Database {

    /** Functional interface for work on the connection. */
    @FunctionalInterface
    public interface Task<T> {
        T run(Connection connection) throws SQLException;
    }

    /** How long {@link #call} waits before it gives up (the statements it is used for take milliseconds). */
    private static final long CALL_TIMEOUT_SECONDS = 5;

    private final Logger log;
    private final File file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MRpvp-Storage");
        thread.setDaemon(true);
        return thread;
    });
    /** Only touched on the storage thread. */
    private Connection connection;

    public Database(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Opens the file and brings the tables up to date. Blocks until done. */
    public void open() throws SQLException {
        call(c -> null, () -> {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new SQLException("Could not create " + parent);
            }
            try {
                // Paper loads the driver from plugin.yml "libraries" into the plugin's class loader.
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite driver missing (plugin.yml libraries)", e);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");
                // WAL + NORMAL survives a server crash; only a power loss could drop the last commits.
                st.execute("PRAGMA synchronous = NORMAL");
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
            }
            Schema.migrate(connection, log);
        });
    }

    /** Runs {@code task} on the storage thread; failures are logged and complete the future exceptionally. */
    public <T> CompletableFuture<T> submit(Task<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(task.run(requireOpen()));
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "Database error", t);
                    future.completeExceptionally(t);
                }
            });
        } catch (RuntimeException e) {
            // executor already shut down
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Runs {@code task} on the storage thread and waits for it (after everything submitted before). */
    public <T> T call(Task<T> task) throws SQLException {
        return call(task, null);
    }

    private <T> T call(Task<T> task, SqlRunnable before) throws SQLException {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    if (before != null) {
                        before.run();
                    }
                    future.complete(task.run(requireOpen()));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for the database", e);
        } catch (TimeoutException e) {
            throw new SQLException("Database did not answer within " + CALL_TIMEOUT_SECONDS + " s", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sql) {
                throw sql;
            }
            throw new SQLException(cause.getMessage(), cause);
        } catch (RuntimeException e) {
            throw new SQLException("Database is closed", e);
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    private Connection requireOpen() throws SQLException {
        if (connection == null) {
            throw new SQLException("Database is not open");
        }
        return connection;
    }

    /** Finishes the queued work (up to 10 s) and closes the connection. */
    public void close() {
        executor.execute(() -> {
            if (connection != null) {
                try (Statement st = connection.createStatement()) {
                    // Fold the WAL back into the main file, so a copy of mrpvp.db alone is complete.
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                } catch (SQLException ignored) {
                    // closing anyway
                }
                try {
                    connection.close();
                } catch (SQLException e) {
                    log.warning("Could not close the database: " + e.getMessage());
                }
                connection = null;
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warning("Database work still running after 10 s - closing anyway.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Size of the database files in bytes. */
    public long sizeBytes() {
        long size = 0;
        for (String suffix : new String[] {"", "-wal", "-shm"}) {
            size += new File(file.getPath() + suffix).length();
        }
        return size;
    }

    public File file() {
        return file;
    }

    /** First column of the first row as long, or {@code otherwise} if there is no row. */
    public static long single(ResultSet rs, long otherwise) throws SQLException {
        return rs.next() ? rs.getLong(1) : otherwise;
    }
}
