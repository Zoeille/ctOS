package com.ctos.traincarts.model;

import com.ctos.trafficlight.model.BlockPosition;

import java.util.Objects;
import java.util.UUID;

/**
 * Configuration for a BART station redstone trigger.
 * Links a station name (from sign line 3) to a redstone block placement position.
 */
public class BartStationConfig {
    private final UUID id;
    private final String stationName;
    private BlockPosition redstonePosition;
    private int delayTicks;

    public BartStationConfig(UUID id, String stationName, BlockPosition redstonePosition) {
        this.id = id;
        this.stationName = stationName.toLowerCase();
        this.redstonePosition = redstonePosition;
        this.delayTicks = 200; // 10 seconds default
    }

    public BartStationConfig(String stationName, BlockPosition redstonePosition) {
        this(UUID.randomUUID(), stationName, redstonePosition);
    }

    public UUID getId() {
        return id;
    }

    public String getStationName() {
        return stationName;
    }

    public BlockPosition getRedstonePosition() {
        return redstonePosition;
    }

    public void setRedstonePosition(BlockPosition redstonePosition) {
        this.redstonePosition = redstonePosition;
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public void setDelayTicks(int delayTicks) {
        this.delayTicks = delayTicks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BartStationConfig that = (BartStationConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BartStationConfig{" +
                "id=" + id +
                ", stationName='" + stationName + '\'' +
                ", redstonePosition=" + redstonePosition +
                ", delayTicks=" + delayTicks +
                '}';
    }
}
