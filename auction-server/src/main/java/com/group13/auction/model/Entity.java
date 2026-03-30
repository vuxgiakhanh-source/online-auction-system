package com.group13.auction.model;

import java.time.LocalDateTime;

public abstract class Entity {

    private int id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Entity(int id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    public abstract void printInfo();
}