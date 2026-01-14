package com.opentable.privatedining.model;

import java.util.UUID;

public class Space {

    private UUID id;
    private String name;
    private Integer minCapacity;
    private Integer maxCapacity;

    public Space() {
        this.id = UUID.randomUUID();
    }

    public Space(String name, Integer minCapacity, Integer maxCapacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.minCapacity = minCapacity;
        this.maxCapacity = maxCapacity;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMinCapacity() {
        return minCapacity;
    }

    public void setMinCapacity(Integer minCapacity) {
        this.minCapacity = minCapacity;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }
}


