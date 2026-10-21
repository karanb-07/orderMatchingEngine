package com.matchingengine.model;
public class PriceLevel {
    private Order head;
    private Order tail;

    public void addLast(Order o) {
        if (head == null) {
            head = o;
            tail = o;
        }
        else {
            tail.next=o;
            o.prev=tail;
            tail = o;
        }
    }

    public Order peekFirst() {
        return head;
    }

    public void remove(Order b) {
        if (b.prev != null) b.prev.next = b.next;   // left neighbor skips over b
        else head = b.next;   // b was the head

        if (b.next != null) b.next.prev = b.prev;   // right neighbor skips over b
        else tail = b.prev;   // b was the tail

        b.prev = null;                              // clean up b
        b.next = null;
    }

    public boolean isEmpty() {
        return (head == null && tail == null);
    }
}