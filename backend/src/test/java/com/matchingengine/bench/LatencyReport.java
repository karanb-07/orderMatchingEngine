package com.matchingengine.bench;

import com.matchingengine.model.Order;
import com.matchingengine.model.Trade;
import com.matchingengine.service.MatchingEngine;
import org.HdrHistogram.Histogram;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-operation latency percentiles, recorded with HdrHistogram.
 *
 * Same workload as the JMH benchmark. Every order is timed individually with
 * System.nanoTime() around the engine call, and recorded into a histogram for its
 * operation type. Aggressive orders are split by how many resting orders they hit.
 *
 * JIT warmup: the full stream is replayed WARMUP_RUNS times on throwaway engines first.
 * Note: each sample includes ~15-30 ns of nanoTime() overhead; JMH (MatchingEngineBenchmark)
 * is the authoritative throughput number, this report is for the latency distribution.
 *
 * Output: a Markdown table per book depth (paste into the README) plus .hgrm
 * percentile files in target/latency/ for plotting (https://hdrhistogram.github.io/HdrHistogram/plotFiles.html).
 *
 * Args (all optional): restingOrders list, measured ops, seed, warmup runs
 *   e.g.  1000,10000,100000 2000000 42 5
 */
public final class LatencyReport {

    private static final String[] NAMES = {"Add limit (rests)", "Cancel", "Match, 1 fill", "Match, 2+ fills", "All orders"};
    private static final int ADD = 0, CANCEL = 1, MATCH_1 = 2, MATCH_N = 3, ALL = 4;

    private static long sink; // keeps results observable so the JIT can't drop the calls

    public static void main(String[] args) throws Exception {
        String[] depths = (args.length > 0 ? args[0] : "1000,10000,100000").split(",");
        int measuredOps = args.length > 1 ? Integer.parseInt(args[1]) : 2_000_000;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        int warmupRuns = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        int levels = MatchingEngineBenchmark.PRICE_LEVELS;

        File outDir = new File("target/latency");
        outDir.mkdirs();

        printEnvironment(measuredOps, seed, warmupRuns, levels);

        for (String d : depths) {
            int resting = Integer.parseInt(d.trim());
            Workload w = Workload.generate(seed, resting, levels, measuredOps);

            for (int r = 0; r < warmupRuns; r++) {
                run(w, null);
            }

            Histogram[] h = new Histogram[NAMES.length];
            for (int k = 0; k < h.length; k++) h[k] = new Histogram(3_600_000_000_000L, 3);
            MatchingEngine finalBook = run(w, h);

            Map<String, Double> best = finalBook.getBestPrices();
            if (best.get("bestBid") != null && best.get("bestAsk") != null
                    && best.get("bestBid") >= best.get("bestAsk")) {
                throw new IllegalStateException("book crossed after replay: " + best);
            }

            System.out.printf(Locale.US, "%n### Book depth: %,d resting orders (~%,d per price level)%n%n",
                    resting, resting / (2 * levels));
            System.out.println("| Operation | Count | p50 | p90 | p99 | p99.9 | p99.99 | Max |");
            System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|");
            for (int k = 0; k < h.length; k++) {
                Histogram x = h[k];
                if (x.getTotalCount() == 0) continue;
                System.out.printf(Locale.US, "| %s | %,d | %s | %s | %s | %s | %s | %s |%n",
                        NAMES[k], x.getTotalCount(),
                        fmt(x.getValueAtPercentile(50)), fmt(x.getValueAtPercentile(90)),
                        fmt(x.getValueAtPercentile(99)), fmt(x.getValueAtPercentile(99.9)),
                        fmt(x.getValueAtPercentile(99.99)), fmt(x.getMaxValue()));

                String file = NAMES[k].toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("-$", "") + "-" + resting + ".hgrm";
                try (PrintStream ps = new PrintStream(new FileOutputStream(new File(outDir, file)))) {
                    x.outputPercentileDistribution(ps, 1000.0); // microseconds
                }
            }
        }
        System.out.println();
        System.out.println("(sink=" + sink + ")");
    }

    /** Replays the workload on a fresh engine; records latencies if h != null. */
    private static MatchingEngine run(Workload w, Histogram[] h) {
        MatchingEngine engine = new MatchingEngine();
        Order[] orders = w.materialize();
        for (int i = 0; i < w.fillOps; i++) {
            engine.processOrder(orders[i]);
        }
        long s = 0;
        for (int i = w.fillOps; i < w.totalOps; i++) {
            byte t = w.type(i);
            long start, elapsed;
            int bucket;
            if (t == Workload.CANCEL) {
                start = System.nanoTime();
                boolean ok = engine.cancelOrder(w.id(i));
                elapsed = System.nanoTime() - start;
                s += ok ? 1 : 0;
                bucket = CANCEL;
            } else {
                start = System.nanoTime();
                List<Trade> trades = engine.processOrder(orders[i]);
                elapsed = System.nanoTime() - start;
                int fills = trades.size();
                s += fills;
                bucket = t == Workload.ADD ? ADD : (fills <= 1 ? MATCH_1 : MATCH_N);
            }
            if (h != null) {
                h[bucket].recordValue(elapsed);
                h[ALL].recordValue(elapsed);
            }
        }
        sink += s;
        return engine;
    }

    /** ns -> human-readable string in ns or µs. */
    private static String fmt(long ns) {
        if (ns < 1_000) return ns + " ns";
        if (ns < 1_000_000) return String.format(Locale.US, "%.2f µs", ns / 1_000.0);
        return String.format(Locale.US, "%.2f ms", ns / 1_000_000.0);
    }

    private static void printEnvironment(int ops, long seed, int warmup, int levels) {
        Runtime rt = Runtime.getRuntime();
        System.out.println("## Matching engine latency report");
        System.out.println();
        System.out.printf(Locale.US, "- JVM: %s %s (%s)%n", System.getProperty("java.vm.name"),
                System.getProperty("java.version"), System.getProperty("java.vm.vendor"));
        System.out.printf(Locale.US, "- OS: %s %s (%s), %d logical CPUs, max heap %,d MB%n",
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                rt.availableProcessors(), rt.maxMemory() / (1024 * 1024));
        System.out.println("- CPU model: <Fill in>");
        System.out.printf(Locale.US, "- Workload: %,d measured orders per depth, seed %d, %d price levels per side, %d warmup replays%n",
                ops, seed, levels, warmup);
        System.out.println("- Single-threaded, matching core called directly (no Spring/HTTP); times include ~20 ns nanoTime() overhead");
    }
}
