'use client';

import { useState, useEffect } from 'react';

type Order = {
  orderId: string;
  side: string;
  price: number;
  quantity: number;
  timestamp: number;
};

type Trade = {
  tradeId: string;
  buyOrderId: string;
  sellOrderId: string;
  price: number;
  quantity: number;
  timestamp: number;
};

type OrderBookLevel = {
  price: number;
  quantity: number;
  orders: number;
};

type OrderBook = {
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
};

export default function Home() {
  const [orderBook, setOrderBook] = useState<OrderBook>({ bids: [], asks: [] });
  const [trades, setTrades] = useState<Trade[]>([]);
  const [bestPrices, setBestPrices] = useState<{ bestBid: number | null; bestAsk: number | null }>({
    bestBid: null,
    bestAsk: null,
  });

  // Form state
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('');
  const [message, setMessage] = useState('');

  // Fetch data from backend
  const fetchData = async () => {
    try {
      const [bookRes, tradesRes, pricesRes] = await Promise.all([
        fetch('http://localhost:8080/api/book'),
        fetch('http://localhost:8080/api/trades'),
        fetch('http://localhost:8080/api/prices'),
      ]);

      const bookData = await bookRes.json();
      const tradesData = await tradesRes.json();
      const pricesData = await pricesRes.json();

      setOrderBook(bookData);
      setTrades(tradesData.slice(-10).reverse()); // Last 10 trades, newest first
      setBestPrices(pricesData);
    } catch (error) {
      console.error('Error fetching data:', error);
    }
  };

  // Poll every 1 second
  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 1000);
    return () => clearInterval(interval);
  }, []);

  // Submit order
  const handleSubmitOrder = async (e: React.FormEvent) => {
    e.preventDefault();

    const order = {
      orderId: `order_${Date.now()}`,
      side,
      price: parseFloat(price),
      quantity: parseInt(quantity),
      timestamp: Date.now(),
    };

    try {
      const response = await fetch('http://localhost:8080/api/order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(order),
      });

      const result = await response.json();
      setMessage(`Order submitted! ${result.tradesExecuted} trades executed.`);
      setPrice('');
      setQuantity('');
      fetchData(); // Refresh data
    } catch (error) {
      setMessage('Error submitting order');
      console.error('Error:', error);
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <h1 className="text-4xl font-bold mb-2 text-center">Order Matching Engine</h1>
        <p className="text-gray-400 text-center mb-8">Real-time order book visualization</p>

        {/* Best Prices */}
        <div className="grid grid-cols-2 gap-4 mb-8">
          <div className="bg-green-900/30 border border-green-500 rounded-lg p-6">
            <div className="text-green-400 text-sm mb-1">Best Bid</div>
            <div className="text-3xl font-bold">
              {bestPrices.bestBid ? `$${bestPrices.bestBid.toFixed(2)}` : '—'}
            </div>
          </div>
          <div className="bg-red-900/30 border border-red-500 rounded-lg p-6">
            <div className="text-red-400 text-sm mb-1">Best Ask</div>
            <div className="text-3xl font-bold">
              {bestPrices.bestAsk ? `$${bestPrices.bestAsk.toFixed(2)}` : '—'}
            </div>
          </div>
        </div>

        {/* Order Book */}
        <div className="grid grid-cols-2 gap-4 mb-8">
          {/* Bids */}
          <div className="bg-gray-800 rounded-lg p-6">
            <h2 className="text-xl font-bold mb-4 text-green-400">Bids (Buy Orders)</h2>
            <div className="space-y-2">
              <div className="grid grid-cols-3 text-sm text-gray-400 mb-2">
                <div>Price</div>
                <div>Quantity</div>
                <div>Orders</div>
              </div>
              {orderBook.bids.length === 0 ? (
                <div className="text-gray-500 text-center py-4">No buy orders</div>
              ) : (
                orderBook.bids.map((level, i) => (
                  <div key={i} className="grid grid-cols-3 text-sm">
                    <div className="text-green-400">${level.price.toFixed(2)}</div>
                    <div>{level.quantity}</div>
                    <div>{level.orders}</div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Asks */}
          <div className="bg-gray-800 rounded-lg p-6">
            <h2 className="text-xl font-bold mb-4 text-red-400">Asks (Sell Orders)</h2>
            <div className="space-y-2">
              <div className="grid grid-cols-3 text-sm text-gray-400 mb-2">
                <div>Price</div>
                <div>Quantity</div>
                <div>Orders</div>
              </div>
              {orderBook.asks.length === 0 ? (
                <div className="text-gray-500 text-center py-4">No sell orders</div>
              ) : (
                orderBook.asks.map((level, i) => (
                  <div key={i} className="grid grid-cols-3 text-sm">
                    <div className="text-red-400">${level.price.toFixed(2)}</div>
                    <div>{level.quantity}</div>
                    <div>{level.orders}</div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Recent Trades */}
        <div className="bg-gray-800 rounded-lg p-6 mb-8">
          <h2 className="text-xl font-bold mb-4">Recent Trades</h2>
          <div className="space-y-2">
            {trades.length === 0 ? (
              <div className="text-gray-500 text-center py-4">No trades yet</div>
            ) : (
              trades.map((trade) => (
                <div key={trade.tradeId} className="flex justify-between text-sm border-b border-gray-700 pb-2">
                  <span className="text-gray-400">{trade.tradeId}</span>
                  <span>
                    {trade.quantity} @ ${trade.price.toFixed(2)}
                  </span>
                  <span className="text-gray-500">
                    {new Date(trade.timestamp).toLocaleTimeString()}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Order Form */}
        <div className="bg-gray-800 rounded-lg p-6">
          <h2 className="text-xl font-bold mb-4">Submit Order</h2>
          <form onSubmit={handleSubmitOrder} className="space-y-4">
            <div>
              <label className="block text-sm mb-2">Side</label>
              <div className="flex gap-4">
                <button
                  type="button"
                  onClick={() => setSide('BUY')}
                  className={`flex-1 py-2 px-4 rounded ${
                    side === 'BUY' ? 'bg-green-600' : 'bg-gray-700'
                  }`}
                >
                  Buy
                </button>
                <button
                  type="button"
                  onClick={() => setSide('SELL')}
                  className={`flex-1 py-2 px-4 rounded ${
                    side === 'SELL' ? 'bg-red-600' : 'bg-gray-700'
                  }`}
                >
                  Sell
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm mb-2">Price</label>
              <input
                type="number"
                step="0.01"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                className="w-full bg-gray-700 rounded px-4 py-2"
                placeholder="100.00"
                required
              />
            </div>

            <div>
              <label className="block text-sm mb-2">Quantity</label>
              <input
                type="number"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-full bg-gray-700 rounded px-4 py-2"
                placeholder="10"
                required
              />
            </div>

            <button
              type="submit"
              className="w-full bg-blue-600 hover:bg-blue-700 py-3 rounded font-semibold"
            >
              Submit Order
            </button>

            {message && (
              <div className="text-center text-sm text-green-400">{message}</div>
            )}
          </form>
        </div>
      </div>
    </div>
  );
}