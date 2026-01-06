package com.matchingengine.controller;

import com.matchingengine.model.Order;
import com.matchingengine.model.Trade;
import com.matchingengine.service.MatchingEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {

    @Autowired
    private MatchingEngine matchingEngine;

    // Health check endpoint
    @GetMapping("/health")
    public String health() {
        return "Matching Engine is running!";
    }

    // Submit a new order
    @PostMapping("/order")
    public Map<String, Object> submitOrder(@RequestBody Order order) {
        // Set timestamp if not provided
        if (order.getTimestamp() == 0) {
            order.setTimestamp(System.currentTimeMillis());
        }

        // Process order through matching engine
        List<Trade> executedTrades = matchingEngine.processOrder(order);

        // Return response
        return Map.of(
                "order", order,
                "tradesExecuted", executedTrades.size(),
                "trades", executedTrades
        );
    }

    // Get current order book
    @GetMapping("/book")
    public Map<String, Object> getOrderBook() {
        return matchingEngine.getOrderBook();
    }

    // Get all trades
    @GetMapping("/trades")
    public List<Trade> getAllTrades() {
        return matchingEngine.getTrades();
    }

    // Cancel an order
    @DeleteMapping("/order/{orderId}")
    public Map<String, Object> cancelOrder(@PathVariable String orderId) {
        boolean cancelled = matchingEngine.cancelOrder(orderId);

        return Map.of(
                "orderId", orderId,
                "cancelled", cancelled,
                "message", cancelled ? "Order cancelled successfully" : "Order not found"
        );
    }

    // Get best bid and ask prices
    @GetMapping("/prices")
    public Map<String, Double> getBestPrices() {
        return matchingEngine.getBestPrices();
    }
}