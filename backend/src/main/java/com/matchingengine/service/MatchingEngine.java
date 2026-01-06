package com.matchingengine.service;

import com.matchingengine.model.Order;
import com.matchingengine.model.Trade;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MatchingEngine {
    private final TreeMap<Double, LinkedList<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Double, LinkedList<Order>> asks = new TreeMap<>();
    private final Map<String, Order> orderMap = new ConcurrentHashMap<>();
    private final List<Trade> trades = (Collections.synchronizedList(new ArrayList<>()));
    private final ReentrantLock lock = new ReentrantLock();
    private int tradeIdCounter = 0;

    public List<Trade> processOrder(Order order) {
        lock.lock();
        try {
            List<Trade> tradesExecuted = new ArrayList<>();
            if (order.getSide().equals("BUY")) {
                tradesExecuted = matchBuyOrder(order);
            } else {
                tradesExecuted = matchSellOrder(order);
            }
            if (order.getQuantity() > 0) {
                addToBook(order);
            }
            return tradesExecuted;
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelOrder(String orderId) {
        lock.lock();
        try {
            Order order = orderMap.get(orderId);
            if (order != null) {
                TreeMap<Double, LinkedList<Order>> orders = order.getSide().equals("BUY") ? bids : asks;
                List<Order> queue = orders.get(order.getPrice());
                if (queue != null) {
                    queue.remove(order);
                    if (queue.isEmpty()) {
                        orders.remove(order.getPrice());
                    }
                }
                orderMap.remove(orderId);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    private List<Trade> matchBuyOrder(Order buyOrder) {
        List<Trade> executedTrades = new ArrayList<>();

        while (!asks.isEmpty() && buyOrder.getQuantity() > 0) {
            Double bestAskPrice = asks.firstKey();
            if (buyOrder.getPrice() >= bestAskPrice) {
                LinkedList<Order> askQueue = asks.get(bestAskPrice);
                Order sellOrder = askQueue.getFirst();
                Trade trade = executeTrade(buyOrder, sellOrder, bestAskPrice);
                executedTrades.add(trade);
                trades.add(trade);
                if (sellOrder.getQuantity() == 0) {
                    askQueue.removeFirst();
                    orderMap.remove(sellOrder.getOrderId());
                    if (askQueue.isEmpty()) {
                        asks.remove(bestAskPrice);
                    }
                }
            } else {
                break;
            }
        }
        return executedTrades;
    }

    private List<Trade> matchSellOrder(Order sellOrder) {
        List<Trade> executedTrades = new ArrayList<>();

        while (!bids.isEmpty() && sellOrder.getQuantity() > 0) {
            Double bestBidPrice = bids.firstKey();
            if (sellOrder.getPrice() <= bestBidPrice) {
                LinkedList<Order> bidQueue = bids.get(bestBidPrice);
                Order buyOrder = bidQueue.getFirst();
                Trade trade = executeTrade(buyOrder, sellOrder, bestBidPrice);
                executedTrades.add(trade);
                trades.add(trade);
                if (buyOrder.getQuantity() == 0) {
                    bidQueue.removeFirst();
                    orderMap.remove(buyOrder.getOrderId());
                    if (bidQueue.isEmpty()) {
                        bids.remove(bestBidPrice);
                    }
                }
            } else {
                break;
            }
        }
        return executedTrades;
    }

    private Trade executeTrade(Order buyOrder, Order sellOrder, double executionPrice) {
        int tradeQuantity = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());
        buyOrder.setQuantity(buyOrder.getQuantity() - tradeQuantity);
        sellOrder.setQuantity(sellOrder.getQuantity() - tradeQuantity);
        String tradeId = "trade_" + (++tradeIdCounter);
        return new Trade(tradeId, buyOrder.getOrderId(), sellOrder.getOrderId(), executionPrice, tradeQuantity);
    }

    private void addToBook(Order order) {
        TreeMap<Double, LinkedList<Order>> book =
                "BUY".equals(order.getSide()) ? bids : asks;

        book.putIfAbsent(order.getPrice(), new LinkedList<>());
        book.get(order.getPrice()).addLast(order);
        orderMap.put(order.getOrderId(), order);
    }

    public List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }

    public Map<String, Double> getBestPrices() {
        lock.lock();
        try {
            Map<String, Double> prices = new HashMap<>();
            prices.put("bestBid", bids.isEmpty() ? null : bids.firstKey());
            prices.put("bestAsk", asks.isEmpty() ? null : asks.firstKey());
            return prices;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> getOrderBook() {
        lock.lock();
        try {
            Map<String, Object> bookData = new HashMap<>();
            bookData.put("bids", formatBookSide(bids));
            bookData.put("asks", formatBookSide(asks));
            return bookData;
        } finally {
            lock.unlock();
        }
    }

    private List<Map<String, Object>> formatBookSide(TreeMap<Double, LinkedList<Order>> bookSide) {
        List<Map<String, Object>> formatted = new ArrayList<>();

        for (Map.Entry<Double, LinkedList<Order>> entry : bookSide.entrySet()) {
            Map<String, Object> level = new HashMap<>();
            level.put("price", entry.getKey());
            level.put("quantity", entry.getValue().stream()
                    .mapToInt(Order::getQuantity)
                    .sum());
            level.put("orders", entry.getValue().size());
            formatted.add(level);
        }

        return formatted;
    }
}

