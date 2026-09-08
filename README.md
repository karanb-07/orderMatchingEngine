\# Real-Time Order Matching Engine



A full-stack order matching engine that simulates a stock exchange, built with Java Spring Boot and Next.js. Orders are matched using \*\*price-time priority\*\* with \*\*O(log n)\*\* efficiency.




\## 🎯 Project Overview



This project implements a \*\*limit order book\*\* matching engine similar to what powers stock exchanges like NASDAQ or NYSE. When users submit buy and sell orders, the system automatically matches them based on price-time priority and executes trades.



\### Key Features



\- ✅ \*\*Real-time order matching\*\* with price-time priority algorithm

\- ✅ \*\*Thread-safe operations\*\* using ReentrantLock

\- ✅ \*\*Partial order fills\*\* when quantities don't match exactly

\- ✅ \*\*RESTful API\*\* for order submission and data retrieval

\- ✅ \*\*Live order book visualization\*\* with Next.js frontend

\- ✅ \*\*Trade execution tracking\*\* with complete audit trail



---



\## 🏗️ Architecture



\### Backend (Spring Boot)

\- \*\*Language:\*\* Java 17

\- \*\*Framework:\*\* Spring Boot 3.2+

\- \*\*Data Structures:\*\* TreeMap (sorted order book), LinkedList (FIFO queues), ConcurrentHashMap



\### Frontend (Next.js)

\- \*\*Language:\*\* TypeScript

\- \*\*Framework:\*\* Next.js 14 (App Router)

\- \*\*Styling:\*\* Tailwind CSS

\- \*\*State Management:\*\* React hooks



\### System Design

```

┌─────────────────┐      REST API       ┌──────────────────┐

│   Next.js UI    │ ◄─────────────────► │  Spring Boot     │

│   (Frontend)    │   JSON over HTTP    │   (Backend)      │

└─────────────────┘                     └──────────────────┘

&nbsp;                                             │

&nbsp;                                             ▼

&nbsp;                                       ┌──────────────┐

&nbsp;                                       │ Matching     │

&nbsp;                                       │ Engine       │

&nbsp;                                       │ (In-Memory)  │

&nbsp;                                       └──────────────┘

```



---



\## 🧠 Matching Algorithm



\### Price-Time Priority



Orders are matched using industry-standard price-time priority:



1\. \*\*Price Priority:\*\* Best prices match first

&nbsp;  - Buy orders: Highest price gets priority

&nbsp;  - Sell orders: Lowest price gets priority



2\. \*\*Time Priority:\*\* At the same price level, earlier orders match first (FIFO)



\### Example Flow

```

Order Book State:

&nbsp; Bids: $105 (50 shares), $100 (30 shares)

&nbsp; Asks: $102 (20 shares), $110 (40 shares)



New sell order: 100 shares @ $100

&nbsp; → Matches with $105 bid (50 shares) - Trade 1

&nbsp; → Matches with $100 bid (30 shares) - Trade 2

&nbsp; → Remaining 20 shares added to ask side @ $100

```



\### Data Structures



\*\*Order Book:\*\*

\- `TreeMap<Double, LinkedList<Order>> bids` - Buy orders (descending price)

\- `TreeMap<Double, LinkedList<Order>> asks` - Sell orders (ascending price)



\*\*Why TreeMap?\*\*

\- O(log n) insertion and removal

\- Automatically sorted by price

\- Efficient best bid/ask lookup



\*\*Why LinkedList?\*\*

\- FIFO ordering at each price level

\- O(1) insertion at end

\- O(1) removal from front



\### Time Complexity

\- \*\*Order submission:\*\* O(log n) + O(m) where n = price levels, m = matched orders

\- \*\*Order cancellation:\*\* O(1) lookup + O(log n) removal

\- \*\*Best price retrieval:\*\* O(1)



---



\## 🔒 Concurrency



The matching engine uses a `ReentrantLock` to ensure thread-safe operations:

```java

public List<Trade> processOrder(Order order) {

&nbsp;   lock.lock();

&nbsp;   try {

&nbsp;       // Only one thread can match orders at a time

&nbsp;       // Prevents race conditions and double-matching

&nbsp;   } finally {

&nbsp;       lock.unlock();

&nbsp;   }

}

```



This prevents:

\- ❌ Double-matching (same order matched twice)

\- ❌ Negative quantities

\- ❌ Corrupted order book state



---



\## 🚀 Getting Started



\### Prerequisites



\- \*\*Java 17+\*\* (\[Download](https://adoptium.net/))

\- \*\*Node.js 18+\*\* (\[Download](https://nodejs.org/))

\- \*\*Maven\*\* (included via wrapper)



\### Backend Setup

```bash

\# Navigate to backend folder

cd backend



\# Run Spring Boot server

./mvnw spring-boot:run



\# Server starts on http://localhost:8080

```



\### Frontend Setup

```bash

\# Navigate to frontend folder

cd frontend



\# Install dependencies

npm install



\# Start development server

npm run dev



\# Frontend runs on http://localhost:3000

```



---



\## 📡 API Endpoints



| Method | Endpoint | Description | Response |

|--------|----------|-------------|----------|

| GET | `/api/health` | Health check | String |

| GET | `/api/book` | Get order book | `{ bids: \[], asks: \[] }` |

| GET | `/api/trades` | Get all trades | `Trade\[]` |

| GET | `/api/prices` | Get best bid/ask | `{ bestBid: number, bestAsk: number }` |

| POST | `/api/order` | Submit order | `{ order: Order, tradesExecuted: number, trades: Trade\[] }` |

| DELETE | `/api/order/{id}` | Cancel order | `{ cancelled: boolean, message: string }` |



\### Example: Submit Order

```bash

curl -X POST http://localhost:8080/api/order \\

&nbsp; -H "Content-Type: application/json" \\

&nbsp; -d '{

&nbsp;   "orderId": "order\_123",

&nbsp;   "side": "BUY",

&nbsp;   "price": 105.0,

&nbsp;   "quantity": 50

&nbsp; }'

```



---



\## 💡 How to Use



1\. \*\*Start both servers\*\* (backend on :8080, frontend on :3000)

2\. \*\*Open browser\*\* to `http://localhost:3000`

3\. \*\*Submit orders\*\* using the form:

&nbsp;  - Select \*\*BUY\*\* or \*\*SELL\*\*

&nbsp;  - Enter \*\*price\*\* and \*\*quantity\*\*

&nbsp;  - Click \*\*Submit Order\*\*

4\. \*\*Watch the order book update\*\* in real-time

5\. \*\*See trades execute\*\* when buy and sell prices overlap



\### Try This Scenario



1\. Submit \*\*BUY\*\* order: 100 shares @ $105

2\. Submit \*\*SELL\*\* order: 30 shares @ $100

&nbsp;  - ✅ Trade executes: 30 @ $100

&nbsp;  - ✅ Buy order reduced to 70 remaining

3\. Submit \*\*SELL\*\* order: 70 shares @ $104

&nbsp;  - ✅ Trade executes: 70 @ $104

&nbsp;  - ✅ Buy order fully filled



---



\## 🛠️ Tech Stack



\*\*Backend:\*\*

\- Java 17

\- Spring Boot 3.2

\- Maven



\*\*Frontend:\*\*

\- Next.js 14

\- TypeScript

\- Tailwind CSS

\- React Hooks



\*\*Data Persistence:\*\*

\- In-memory (order book resets on restart)



---



\## 📂 Project Structure

```

matching-engine/

├── backend/

│   ├── src/main/java/com/matchingengine/

│   │   ├── config/

│   │   │   └── CorsConfig.java

│   │   ├── controller/

│   │   │   └── OrderController.java

│   │   ├── model/

│   │   │   ├── Order.java

│   │   │   └── Trade.java

│   │   ├── service/

│   │   │   └── MatchingEngine.java

│   │   └── MatchingEngineApplication.java

│   └── pom.xml

│

└── frontend/

&nbsp;   ├── app/

&nbsp;   │   ├── page.tsx

&nbsp;   │   └── layout.tsx

&nbsp;   └── package.json

```



---



\## 🎓 What I Learned



\- \*\*Data Structures:\*\* Practical application of TreeMap and LinkedList

\- \*\*Algorithms:\*\* Price-time priority matching algorithm

\- \*\*Concurrency:\*\* Thread safety with locks and race condition prevention

\- \*\*System Design:\*\* Order book architecture

\- \*\*Full-Stack Development:\*\* Connecting Spring Boot backend to Next.js frontend

\- \*\*RESTful APIs:\*\* Designing clean, predictable endpoints



---



\## 🔮 Future Enhancements



\- \[ ] WebSocket support for true real-time updates

\- \[ ] Market orders (execute at best available price)

\- \[ ] Stop-loss orders

\- \[ ] Order modification without canceling

\- \[ ] User authentication and portfolios

\- \[ ] Trading statistics (VWAP, volume, spreads)

\- \[ ] Persistent storage (database integration)

\- \[ ] Order history and audit logs



---



\## 📝 License



MIT License - feel free to use this project for learning!



---



\## 👤 Author



\*\*Karan Bhargava\*\*

\- GitHub: \karanb-07(https://github.com/karanb-07)

\- LinkedIn: \(https://linkedin.com/in/kbharga)



---



\## 🙏 Acknowledgments



Built as a learning project to understand how stock exchanges work at a fundamental level.

