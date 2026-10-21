package com.matchingengine.bench;

import com.matchingengine.model.Order;
import com.matchingengine.service.MatchingEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Throughput of the matching core under a realistic mixed workload.
 *
 * Each invocation replays the same pre-generated stream of MEASURED_OPS orders
 * against a freshly built book (built untimed in setup), so every measurement
 * sees identical book states. Score = average ns per order; throughput = 1e9 / score.
 *
 * No Spring, no HTTP: MatchingEngine is constructed directly.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
@Fork(value = 2, jvmArgsAppend = {"-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch"})
@State(Scope.Benchmark)
public class MatchingEngineBenchmark {

    static final int MEASURED_OPS = 500_000;
    static final int PRICE_LEVELS = 50;   // per side
    static final long SEED = 42;

    /** Resting orders in the book; per-level queue depth ~= restingOrders / (2 * PRICE_LEVELS). */
    @Param({"1000", "10000", "100000"})
    public int restingOrders;

    private Workload workload;
    private MatchingEngine engine;
    private Order[] orders;

    @Setup(Level.Trial)
    public void generate() {
        workload = Workload.generate(SEED, restingOrders, PRICE_LEVELS, MEASURED_OPS);
    }

    // Level.Invocation is appropriate here: each invocation is one long single-shot replay.
    @Setup(Level.Invocation)
    public void buildBook() {
        engine = new MatchingEngine();
        orders = workload.materialize();
        for (int i = 0; i < workload.fillOps; i++) {
            engine.processOrder(orders[i]);
        }
    }

    @Benchmark
    @OperationsPerInvocation(MEASURED_OPS)
    public void mixedWorkload(Blackhole bh) {
        Workload w = workload;
        MatchingEngine e = engine;
        Order[] o = orders;
        for (int i = w.fillOps; i < w.totalOps; i++) {
            if (w.type(i) == Workload.CANCEL) {
                bh.consume(e.cancelOrder(w.id(i)));
            } else {
                bh.consume(e.processOrder(o[i]));
            }
        }
    }
}
