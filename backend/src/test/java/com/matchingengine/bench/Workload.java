package com.matchingengine.bench;

import com.matchingengine.model.Order;
import com.matchingengine.model.Trade;
import com.matchingengine.service.MatchingEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Deterministic, reproducible order flow for benchmarking.
 *
 * The stream is generated once (fixed seed) by playing it against a "shadow"
 * MatchingEngine, so the generator always knows which orders are live and how
 * much quantity rests on each side. Replaying the same stream on a fresh engine
 * reproduces exactly the same book states.
 *
 * Shape of the book:
 *   - mid price 100.00, tick 0.01
 *   - passive bids rest at mid - k ticks, passive asks at mid + k ticks, k in [1, levels]
 *     (so the book never crosses on its own)
 *   - aggressive orders are priced through the whole opposite side and their size
 *     is capped at the resting opposite quantity, so they always fill completely
 *     and never rest (the engine has no IOC order type)
 *
 * Phases:
 *   - fill:     passive adds until the book holds {@code restingOrders} live orders
 *   - measured: mix of ADD / CANCEL / AGGRESSIVE, biased toward cancels whenever the
 *               book is at or above target, which keeps depth roughly steady
 */
public final class Workload {

    public static final byte ADD = 0;
    public static final byte CANCEL = 1;
    public static final byte AGGRESSIVE = 2;

    private static final double MID = 100.00;
    private static final double TICK = 0.01;

    public final int restingOrders;
    public final int levels;
    public final int fillOps;     // ops [0, fillOps) are the untimed book-building prefix
    public final int totalOps;

    final byte[] type;
    final boolean[] buy;
    final double[] price;
    final int[] qty;
    final String[] id;           // order id for ADD/AGGRESSIVE, target id for CANCEL

    public final int addCount, cancelCount, aggressiveCount;

    private Workload(int restingOrders, int levels, int fillOps, int totalOps,
                     byte[] type, boolean[] buy, double[] price, int[] qty, String[] id,
                     int addCount, int cancelCount, int aggressiveCount) {
        this.restingOrders = restingOrders;
        this.levels = levels;
        this.fillOps = fillOps;
        this.totalOps = totalOps;
        this.type = type;
        this.buy = buy;
        this.price = price;
        this.qty = qty;
        this.id = id;
        this.addCount = addCount;
        this.cancelCount = cancelCount;
        this.aggressiveCount = aggressiveCount;
    }

    public static Workload generate(long seed, int restingOrders, int levels, int measuredOps) {
        SplittableRandom rnd = new SplittableRandom(seed);
        int total = restingOrders + measuredOps;

        byte[] type = new byte[total];
        boolean[] buy = new boolean[total];
        double[] price = new double[total];
        int[] qty = new int[total];
        String[] id = new String[total];

        MatchingEngine shadow = new MatchingEngine();
        LiveSet live = new LiveSet();
        long[] sideQty = new long[2]; // [0] = resting bid qty, [1] = resting ask qty
        int nextId = 0;
        int adds = 0, cancels = 0, aggressive = 0;

        for (int i = 0; i < total; i++) {
            boolean fill = i < restingOrders;
            byte op;
            if (fill) {
                op = ADD;
            } else {
                int r = rnd.nextInt(100);
                if (live.size() >= restingOrders) {
                    op = r < 40 ? ADD : r < 85 ? CANCEL : AGGRESSIVE;
                } else {
                    op = r < 60 ? ADD : r < 85 ? CANCEL : AGGRESSIVE;
                }
                if (op == CANCEL && live.size() == 0) op = ADD;
            }

            if (op == AGGRESSIVE) {
                boolean isBuy = rnd.nextBoolean();
                long available = isBuy ? sideQty[1] : sideQty[0];
                if (available == 0) {
                    op = ADD;
                } else {
                    int q = (int) Math.min(1 + rnd.nextInt(150), available);
                    double p = isBuy ? round(MID + (levels + 1) * TICK) : round(MID - (levels + 1) * TICK);
                    String oid = "o" + (nextId++);
                    record(type, buy, price, qty, id, i, AGGRESSIVE, isBuy, p, q, oid);

                    List<Trade> trades = shadow.processOrder(new Order(oid, isBuy ? "BUY" : "SELL", p, q, i));
                    int filled = 0;
                    for (Trade t : trades) {
                        String restingId = isBuy ? t.getSellOrderId() : t.getBuyOrderId();
                        live.reduce(restingId, t.getQuantity());
                        filled += t.getQuantity();
                    }
                    if (filled != q) throw new IllegalStateException("aggressive order did not fully fill at op " + i);
                    sideQty[isBuy ? 1 : 0] -= q;
                    aggressive++;
                    continue;
                }
            }

            if (op == CANCEL) {
                String target = live.randomId(rnd);
                LiveSet.Entry e = live.get(target);
                record(type, buy, price, qty, id, i, CANCEL, e.buy, 0, 0, target);
                if (!shadow.cancelOrder(target)) throw new IllegalStateException("cancel missed at op " + i);
                sideQty[e.buy ? 0 : 1] -= e.remaining;
                live.remove(target);
                cancels++;
                continue;
            }

            // ADD (passive, never crosses)
            boolean isBuy = rnd.nextBoolean();
            int k = 1 + rnd.nextInt(levels);
            double p = isBuy ? round(MID - k * TICK) : round(MID + k * TICK);
            int q = 1 + rnd.nextInt(100);
            String oid = "o" + (nextId++);
            record(type, buy, price, qty, id, i, ADD, isBuy, p, q, oid);
            if (!shadow.processOrder(new Order(oid, isBuy ? "BUY" : "SELL", p, q, i)).isEmpty()) {
                throw new IllegalStateException("passive order crossed at op " + i);
            }
            live.add(oid, isBuy, q);
            sideQty[isBuy ? 0 : 1] += q;
            if (!fill) adds++;
        }

        return new Workload(restingOrders, levels, restingOrders, total,
                type, buy, price, qty, id, adds, cancels, aggressive);
    }

    /** Fresh, mutable Order objects for one replay (the engine mutates quantities). */
    public Order[] materialize() {
        Order[] orders = new Order[totalOps];
        for (int i = 0; i < totalOps; i++) {
            if (type[i] != CANCEL) {
                orders[i] = new Order(id[i], buy[i] ? "BUY" : "SELL", price[i], qty[i], i);
            }
        }
        return orders;
    }

    public byte type(int i) { return type[i]; }
    public String id(int i) { return id[i]; }

    private static void record(byte[] type, boolean[] buy, double[] price, int[] qty, String[] id,
                               int i, byte t, boolean b, double p, int q, String oid) {
        type[i] = t; buy[i] = b; price[i] = p; qty[i] = q; id[i] = oid;
    }

    private static double round(double p) {
        return Math.round(p * 100.0) / 100.0;
    }

    /** Live orders with O(1) add, remove, and uniform random pick. */
    private static final class LiveSet {
        static final class Entry {
            final String id; final boolean buy; int remaining; int index;
            Entry(String id, boolean buy, int remaining, int index) {
                this.id = id; this.buy = buy; this.remaining = remaining; this.index = index;
            }
        }
        private final ArrayList<Entry> list = new ArrayList<>();
        private final Map<String, Entry> byId = new HashMap<>();

        int size() { return list.size(); }
        Entry get(String id) { return byId.get(id); }

        void add(String id, boolean buy, int qty) {
            Entry e = new Entry(id, buy, qty, list.size());
            list.add(e);
            byId.put(id, e);
        }

        void reduce(String id, int filled) {
            Entry e = byId.get(id);
            if (e == null) throw new IllegalStateException("fill against unknown order " + id);
            e.remaining -= filled;
            if (e.remaining == 0) remove(id);
        }

        void remove(String id) {
            Entry e = byId.remove(id);
            Entry last = list.remove(list.size() - 1);
            if (last != e) {
                list.set(e.index, last);
                last.index = e.index;
            }
        }

        String randomId(SplittableRandom rnd) {
            return list.get(rnd.nextInt(list.size())).id;
        }
    }
}
