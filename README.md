# Real-Time Order Matching Engine

A full-stack limit order book matching engine that simulates a stock exchange, built with Java Spring Boot and Next.js. Orders are matched using **price-time priority**, with O(1) cancels and sub-microsecond p99 latency at 100,000 resting orders.

##  Performance

Single-threaded matching core on an Intel Core Ultra 9 285H, 2M-order mixed workload (adds, cancels, aggressive orders):

| Book depth | All orders p50 | All orders p99 | Cancel p99 |
|---|---:|---:|---:|
| 1,000 resting orders | 100 ns | 400 ns | 300 ns |
| 10,000 resting orders | 100 ns | 400 ns | 400 ns |
| 100,000 resting orders | 200 ns | 900 ns | 1.0 µs |

Profiling showed cancels were O(n): `LinkedList.remove()` searched the queue at each price level. Replacing it with an intrusive doubly linked list made cancel O(1) and cut **cancel p99 at 100k depth from 84 µs to 1 µs (~84×)**, with identical matching results.

Full methodology, before/after tables, and how to reproduce: [BENCHMARKS.md](backend/BENCHMARKS.md)

---

##  Project Overview

This project implements a **limit order book** matching engine similar to what powers exchanges like NASDAQ or NYSE. When users submit buy and sell orders, the system matches them by price-time priority and executes trades.

### Key Features

-  **Price-time priority matching** with partial fills
-  **O(1) order cancellation** via an intrusive doubly linked list
-  **Thread-safe operations** using `ReentrantLock`
-  **RESTful API** for order submission, cancellation, and market data
-  **Live order book visualization** with a Next.js frontend
-  **Reproducible latency benchmarks** (JMH + HdrHistogram)

---

##  Architecture

### Backend
- **Language:** Java 17
- **Framework:** Spring Boot 3.5
- **Data Structures:** TreeMap (price levels, sorted), intrusive doubly linked list per price level (FIFO time priority, O(1) cancel), HashMap (order ID → order)

### Frontend
- **Language:** TypeScript
- **Framework:** Next.js 16 (App Router), React 19
- **Styling:** Tailwind CSS 4

### System Design

```
┌─────────────────┐      REST API       ┌──────────────────┐
│   Next.js UI    │ ◄─────────────────► │   Spring Boot    │
│   (Frontend)    │   JSON over HTTP    │    (Backend)     │
└─────────────────┘                     └────────┬─────────┘
                                                 │
                                                 ▼
                                        ┌──────────────────┐
                                        │ Matching Engine  │
                                        │   (In-Memory)    │
                                        └──────────────────┘
```

---

##  Matching Algorithm

### Price-Time Priority

1. **Price priority:** the best price matches first
    - Buy side: highest bid first
    - Sell side: lowest ask first
2. **Time priority:** at the same price, earlier orders match first (FIFO)

Trades execute at the **resting** order's price.

### Example Flow

```
Order book:
  Asks: $110 (40 shares), $106 (20 shares)
  Bids: $105 (50 shares), $100 (30 shares)

New SELL order: 100 shares @ $100
  → Fills 50 against the $105 bid  (trade @ $105)
  → Fills 30 against the $100 bid  (trade @ $100)
  → Remaining 20 shares rest on the ask side @ $100
```

### Data Structures

```
TreeMap<Double, PriceLevel> asks      → sorted by price (best ask first)
  $100.01 → PriceLevel [o5 ⇄ o9 ⇄ o12]     ← head = oldest order
  $100.02 → PriceLevel [o3 ⇄ o7]

HashMap<String, Order> orderMap       → order ID → the Order node itself
```

Each structure has one job:

- **TreeMap** keeps price levels sorted, so the best price is always `firstKey()`.
- **PriceLevel** is an intrusive doubly linked list: `prev`/`next` pointers live on `Order` itself. New orders join at the tail and matching takes from the head (FIFO).
- **orderMap** finds any order by ID in O(1). Because the order *is* the list node, a cancel jumps straight to it and unlinks it with 4 pointer updates, with no search.

**Why not `java.util.LinkedList`?** It hides its nodes, so `remove(order)` must walk the list to find the order: O(n) in queue depth. At 1,000 orders per price level that made cancels ~30× slower at the median. See [BENCHMARKS.md](backend/BENCHMARKS.md).

### Time Complexity

P = number of price levels, m = number of resting orders matched

| Operation | Complexity |
|---|---|
| Submit order (rests) | O(log P) |
| Submit order (matches) | O(m · log P) worst case |
| Cancel order | O(1) lookup + O(log P) level lookup + O(1) unlink |
| Best bid/ask | O(log P) |

---

##  Concurrency

The matching engine uses a `ReentrantLock` so only one thread mutates the book at a time:

```java
public List<Trade> processOrder(Order order) {
    lock.lock();
    try {
        // match, then rest any remaining quantity
    } finally {
        lock.unlock();
    }
}
```

This prevents:
-  Double-matching (same order matched twice)
-  Negative quantities
-  Corrupted order book state

---

##  Getting Started

### Prerequisites
- **Java 17+** ([Download](https://adoptium.net/))
- **Node.js 18+** ([Download](https://nodejs.org/))
- **Maven** (included via wrapper)

### Backend

```bash
cd backend
./mvnw spring-boot:run
# Server starts on http://localhost:8080
```

### Frontend

```bash
cd frontend
npm install
npm run dev
# Frontend runs on http://localhost:3000
```

### Benchmarks

```bash
cd backend
./mvnw test-compile exec:exec@latency   # per-operation latency percentiles (~15 s)
./mvnw test-compile exec:exec@jmh       # throughput via JMH (~10 min)
```

See [BENCHMARKS.md](backend/BENCHMARKS.md) for methodology and results.

---

##  API Endpoints

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| GET | `/api/health` | Health check | String |
| GET | `/api/book` | Get order book | `{ bids: [], asks: [] }` |
| GET | `/api/trades` | Get all trades | `Trade[]` |
| GET | `/api/prices` | Get best bid/ask | `{ bestBid: number, bestAsk: number }` |
| POST | `/api/order` | Submit order | `{ order: Order, tradesExecuted: number, trades: Trade[] }` |
| DELETE | `/api/order/{id}` | Cancel order | `{ cancelled: boolean, message: string }` |

### Example: Submit an Order

```bash
curl -X POST http://localhost:8080/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "order_123",
    "side": "BUY",
    "price": 105.0,
    "quantity": 50
  }'
```

---

##  How to Use

1. **Start both servers** (backend on :8080, frontend on :3000)
2. **Open** `http://localhost:3000`
3. **Submit orders** using the form: choose BUY or SELL, enter price and quantity, click Submit Order
4. **Watch the order book update** as orders rest and trade
5. **See trades execute** when buy and sell prices cross

### Try This Scenario

1. Submit **BUY** 100 shares @ $105
2. Submit **SELL** 30 shares @ $100
    -  Trade executes: 30 @ $105 (the resting buy's price)
    -  Buy order has 70 remaining
3. Submit **SELL** 70 shares @ $104
    -  Trade executes: 70 @ $105
    -  Buy order fully filled

---

##  Project Structure

```
orderMatchingEngine/
├── backend/
│   ├── src/main/java/com/matchingengine/
│   │   ├── config/CorsConfig.java
│   │   ├── controller/OrderController.java
│   │   ├── model/
│   │   │   ├── Order.java           # order + prev/next list pointers
│   │   │   ├── PriceLevel.java      # intrusive doubly linked list (one per price)
│   │   │   └── Trade.java
│   │   ├── service/MatchingEngine.java
│   │   └── MatchingEngineApplication.java
│   ├── src/test/java/com/matchingengine/bench/
│   │   ├── Workload.java                  # deterministic order-flow generator
│   │   ├── LatencyReport.java             # per-operation latency percentiles
│   │   └── MatchingEngineBenchmark.java   # JMH throughput benchmark
│   ├── BENCHMARKS.md
│   └── pom.xml
│
└── frontend/
    ├── app/
    │   ├── page.tsx
    │   └── layout.tsx
    └── package.json
```

---

##  What I Learned

- **Measure before optimizing.** I assumed matching would be the bottleneck. Benchmarking showed it was cancels: an O(n) `LinkedList.remove()` hidden behind a one-line call.
- **Big-O isn't the whole story.** The O(n) cancel got slower *faster* than linear, because walking scattered list nodes causes cache misses. Even after the O(1) fix, cancel is 6× slower at 100k orders than at 1k, because the data no longer fits in CPU cache.
- **Averages hide problems.** Before the fix, the median order took 300 ns at 100k depth, but p99 was 64 µs. Tail percentiles are what matter for a matching engine.
- **Benchmarking the JVM is easy to get wrong.** Without JIT warmup, a fixed workload seed, and consuming results (so the JIT can't delete the work), the numbers are meaningless.

---

##  Future Enhancements

- [ ] WebSocket support for true real-time updates
- [ ] Market and IOC (immediate-or-cancel) order types
- [ ] Reject duplicate order IDs
- [ ] Bound the trade history (currently grows without limit)
- [ ] Reduce GC pressure (object pooling) to tighten max latency
