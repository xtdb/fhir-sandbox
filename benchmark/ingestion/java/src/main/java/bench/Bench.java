package bench;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class Bench {

    public static void main(String[] args) throws Exception {
        String host          = env("XTDB_HOST", "localhost");
        int    port          = Integer.parseInt(env("XTDB_PORT", "5432"));
        String db            = env("XTDB_DB", "xtdb");
        String user          = env("XTDB_USER", "xtdb");
        String mode          = env("MODE", "batched");
        int    totalRows     = Integer.parseInt(env("TOTAL_ROWS", "500000"));
        int    rowsPerTxn    = Integer.parseInt(env("ROWS_PER_TXN", "1000"));
        int    warmupTxns    = Integer.parseInt(env("WARMUP_TXNS", "5"));
        boolean rewrite      = Boolean.parseBoolean(env("REWRITE_BATCHED_INSERTS", "false"));
        boolean asyncTx      = Boolean.parseBoolean(env("ASYNC_TX", "false"));

        if (!mode.equals("batched") && !mode.equals("sequential")) {
            throw new IllegalArgumentException("MODE must be 'batched' or 'sequential', got: " + mode);
        }
        int numTxns = totalRows / rowsPerTxn;
        if (numTxns <= warmupTxns) {
            throw new IllegalArgumentException("TOTAL_ROWS/ROWS_PER_TXN must exceed WARMUP_TXNS");
        }

        String url = String.format(
            "jdbc:postgresql://%s:%d/%s?reWriteBatchedInserts=%s",
            host, port, db, rewrite);

        Properties props = new Properties();
        props.setProperty("user", user);

        System.out.printf("=== bench-jdbc ===%n");
        System.out.printf("url=%s%n", url);
        System.out.printf("mode=%s total_rows=%d rows_per_txn=%d warmup_txns=%d async_tx=%s%n",
            mode, totalRows, rowsPerTxn, warmupTxns, asyncTx);

        try (Connection conn = DriverManager.getConnection(url, props)) {
            // Manage transaction boundaries explicitly so the XTDB-specific
            // `(async=true)` option can ride on BEGIN when requested. The
            // driver must stay in autoCommit=true, otherwise pgJDBC injects
            // its own BEGIN before our explicit one (which the server would
            // then ignore as "transaction already in progress").
            conn.setAutoCommit(true);
            String beginSql = asyncTx
                ? "BEGIN TRANSACTION READ WRITE WITH (ASYNC = TRUE)"
                : "BEGIN TRANSACTION READ WRITE";

            String sql = "INSERT INTO bench (_id, payload, _valid_to) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL 'PT1M')";
            int measuredCount = numTxns - warmupTxns;
            List<Long> totalNs  = new ArrayList<>(measuredCount);
            List<Long> buildNs  = new ArrayList<>(measuredCount);
            List<Long> waitNs   = new ArrayList<>(measuredCount);
            List<Long> commitNs = new ArrayList<>(measuredCount);

            long wallMeasuredStart = 0L;

            Statement txStmt = conn.createStatement();

            for (int t = 0; t < numTxns; t++) {
                if (t == warmupTxns) {
                    wallMeasuredStart = System.nanoTime();
                }

                long phaseBuild = 0L;
                long phaseWait  = 0L;
                long txStart = System.nanoTime();

                long beginStart = System.nanoTime();
                txStmt.execute(beginSql);
                phaseWait += System.nanoTime() - beginStart;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (mode.equals("batched")) {
                        long b0 = System.nanoTime();
                        for (int i = 0; i < rowsPerTxn; i++) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setString(2, "payload-" + t + "-" + i);
                            ps.addBatch();
                        }
                        long b1 = System.nanoTime();
                        ps.executeBatch();
                        long w1 = System.nanoTime();
                        phaseBuild = b1 - b0;
                        phaseWait  = w1 - b1;
                    } else {
                        for (int i = 0; i < rowsPerTxn; i++) {
                            long b0 = System.nanoTime();
                            ps.setObject(1, UUID.randomUUID());
                            ps.setString(2, "payload-" + t + "-" + i);
                            long b1 = System.nanoTime();
                            ps.executeUpdate();
                            long w1 = System.nanoTime();
                            phaseBuild += (b1 - b0);
                            phaseWait  += (w1 - b1);
                        }
                    }
                }

                long beforeCommit = System.nanoTime();
                txStmt.execute("COMMIT");
                long afterCommit = System.nanoTime();
                long phaseCommit = afterCommit - beforeCommit;
                long took = afterCommit - txStart;

                if (took > 1_000_000_000L) {
                    System.out.printf("[%s] slow txn #%d took %.2f ms%n",
                        Instant.now(), t, took / 1e6);
                }

                if (t >= warmupTxns) {
                    totalNs.add(took);
                    buildNs.add(phaseBuild);
                    waitNs.add(phaseWait);
                    commitNs.add(phaseCommit);
                }
            }

            long wallMeasuredEnd = System.nanoTime();
            int measuredRows = measuredCount * rowsPerTxn;
            double secs = (wallMeasuredEnd - wallMeasuredStart) / 1e9;

            printStats(mode, measuredRows, secs, rowsPerTxn, totalNs, buildNs, waitNs, commitNs);
        }
    }

    private static void printStats(String mode, int rows, double secs, int rowsPerTxn,
                                   List<Long> totalNs, List<Long> buildNs,
                                   List<Long> waitNs, List<Long> commitNs) {
        double rps = rows / secs;

        System.out.println();
        System.out.println("=== results ===");
        System.out.printf("mode             : %s%n", mode);
        System.out.printf("measured rows    : %d%n", rows);
        System.out.printf("measured seconds : %.3f%n", secs);
        System.out.printf("rows/sec         : %.1f%n", rps);
        System.out.printf("txn/sec          : %.2f%n", rps / rowsPerTxn);
        System.out.println();
        System.out.println("txn phase latencies (per transaction):");
        System.out.printf("%-10s %10s %10s %10s%n", "phase", "mean(ms)", "p50(ms)", "p99(ms)");
        printPhase("total",  totalNs);
        printPhase("build",  buildNs);
        printPhase("wait",   waitNs);
        printPhase("commit", commitNs);
        System.out.println();
        System.out.println("  build  = client CPU (UUID, param binding, addBatch)");
        System.out.println("  wait   = client blocked on server (BEGIN + executeBatch / executeUpdate sum)");
        System.out.println("  commit = explicit 'COMMIT' round trip");
    }

    private static void printPhase(String name, List<Long> ns) {
        List<Long> sorted = new ArrayList<>(ns);
        Collections.sort(sorted);
        double p50 = sorted.get((int) (sorted.size() * 0.50)) / 1e6;
        double p99 = sorted.get(Math.min((int) (sorted.size() * 0.99), sorted.size() - 1)) / 1e6;
        long sum = 0L;
        for (long d : sorted) sum += d;
        double mean = (sum / (double) sorted.size()) / 1e6;
        System.out.printf("%-10s %10.2f %10.2f %10.2f%n", name, mean, p50, p99);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private Bench() {}
}