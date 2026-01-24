package com.ctos.trafficlight.model;

import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores the complete state of an item frame for serialization and restoration.
 */
public class ItemFrameStateData {

    private Map<String, Object> serializedItem; // ItemStack.serialize()
    private Rotation rotation;
    private BlockFace facing;
    private boolean visible;
    private boolean glowing;
    private boolean fixed;

    // Exact coordinates for precise spawning/matching
    private double exactX;
    private double exactY;
    private double exactZ;

    public ItemFrameStateData(Map<String, Object> serializedItem, Rotation rotation,
                               BlockFace facing, boolean visible, boolean glowing, boolean fixed) {
        this(serializedItem, rotation, facing, visible, glowing, fixed, 0, 0, 0);
    }

    public ItemFrameStateData(Map<String, Object> serializedItem, Rotation rotation,
                               BlockFace facing, boolean visible, boolean glowing, boolean fixed,
                               double exactX, double exactY, double exactZ) {
        this.serializedItem = serializedItem != null ? new HashMap<>(serializedItem) : null;
        this.rotation = rotation;
        this.facing = facing;
        this.visible = visible;
        this.glowing = glowing;
        this.fixed = fixed;
        this.exactX = exactX;
        this.exactY = exactY;
        this.exactZ = exactZ;
    }

    /**
     * Captures the complete state of an ItemFrame
     */
    public static ItemFrameStateData capture(ItemFrame itemFrame) {
        Map<String, Object> serializedItem = null;
        ItemStack item = itemFrame.getItem();
        if (item != null && !item.getType().isAir()) {
            serializedItem = item.serialize();
        }

        Location loc = itemFrame.getLocation();

        return new ItemFrameStateData(
                serializedItem,
                itemFrame.getRotation(),
                itemFrame.getFacing(),
                itemFrame.isVisible(),
                itemFrame instanceof GlowItemFrame,
                itemFrame.isFixed(),
                loc.getX(),
                loc.getY(),
                loc.getZ()
        );
    }

    /**
     * Applies this state to an ItemFrame
     * @param itemFrame The item frame to update
     * @param playSound Whether to play the item placement sound
     */
    public void applyToItemFrame(ItemFrame itemFrame, boolean playSound) {
        // Temporarily set fixed=true to prevent item drops when changing the item
        boolean wasFixed = itemFrame.isFixed();
        itemFrame.setFixed(true);

        try {
            // Set the item (won't drop because frame is fixed)
            if (serializedItem != null) {
                ItemStack item = ItemStack.deserialize(serializedItem);
                itemFrame.setItem(item, playSound);
            } else {
                itemFrame.setItem(null, playSound);
            }

            // Set rotation
            if (rotation != null) {
                itemFrame.setRotation(rotation);
            }

            // Set visibility
            itemFrame.setVisible(visible);
        } finally {
            // Restore the original fixed state
            itemFrame.setFixed(fixed);
        }
    }

    /**
     * Creates a deep copy
     */
    public ItemFrameStateData copy() {
        Map<String, Object> itemCopy = serializedItem != null ? new HashMap<>(serializedItem) : null;
        return new ItemFrameStateData(itemCopy, rotation, facing, visible, glowing, fixed, exactX, exactY, exactZ);
    }

    // Getters

    public Map<String, Object> getSerializedItem() {
        return serializedItem != null ? new HashMap<>(serializedItem) : null;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public boolean isFixed() {
        return fixed;
    }

    public double getExactX() {
        return exactX;
    }

    public double getExactY() {
        return exactY;
    }

    public double getExactZ() {
        return exactZ;
    }

    public boolean hasExactCoordinates() {
        return exactX != 0 || exactY != 0 || exactZ != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemFrameStateData that = (ItemFrameStateData) o;
        return visible == that.visible &&
                glowing == that.glowing &&
                fixed == that.fixed &&
                Objects.equals(serializedItem, that.serializedItem) &&
                rotation == that.rotation &&
                facing == that.facing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serializedItem, rotation, facing, visible, glowing, fixed);
    }
}
