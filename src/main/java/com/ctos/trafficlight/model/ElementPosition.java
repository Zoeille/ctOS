package com.ctos.trafficlight.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.Objects;

/**
 * Unified position for both blocks and item frames.
 * For blocks: world, x, y, z
 * For item frames: world, x, y, z + BlockFace (direction)
 */
public class ElementPosition {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final BlockFace facing; // null for blocks, non-null for item frames

    /**
     * Creates a position for a block (no facing)
     */
    public ElementPosition(String worldName, int x, int y, int z) {
        this(worldName, x, y, z, null);
    }

    /**
     * Creates a position for an item frame (with facing)
     */
    public ElementPosition(String worldName, int x, int y, int z, BlockFace facing) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }

    /**
     * Creates a position from a Location (for blocks)
     */
    public static ElementPosition fromLocation(Location location) {
        return new ElementPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    /**
     * Creates a position from a Location with facing (for item frames)
     */
    public static ElementPosition fromLocation(Location location, BlockFace facing) {
        return new ElementPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                facing
        );
    }

    /**
     * Creates an ElementPosition from a BlockPosition
     */
    public static ElementPosition fromBlockPosition(BlockPosition blockPosition) {
        return new ElementPosition(
                blockPosition.getWorldName(),
                blockPosition.getX(),
                blockPosition.getY(),
                blockPosition.getZ()
        );
    }

    /**
     * Converts to a BlockPosition (ignores facing)
     */
    public BlockPosition toBlockPosition() {
        return new BlockPosition(worldName, x, y, z);
    }

    /**
     * Converts to a Bukkit Location
     */
    public Location toLocation(Server server) {
        World world = server.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World " + worldName + " is not loaded");
        }
        return new Location(world, x, y, z);
    }

    /**
     * Converts to a Bukkit Location using Bukkit.getServer()
     */
    public Location toLocation() {
        return toLocation(Bukkit.getServer());
    }

    /**
     * Checks if this is an item frame position (has facing)
     */
    public boolean isItemFrame() {
        return facing != null;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockFace getFacing() {
        return facing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementPosition that = (ElementPosition) o;
        return x == that.x &&
                y == that.y &&
                z == that.z &&
                Objects.equals(worldName, that.worldName) &&
                facing == that.facing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z, facing);
    }

    /**
     * Returns string representation.
     * For blocks: "world_x_y_z"
     * For item frames: "world_x_y_z_FACING"
     */
    @Override
    public String toString() {
        if (facing != null) {
            return worldName + "_" + x + "_" + y + "_" + z + "_" + facing.name();
        }
        return worldName + "_" + x + "_" + y + "_" + z;
    }

    /**
     * Creates an ElementPosition from a string representation.
     * Format: "world_x_y_z" or "world_x_y_z_FACING"
     */
    public static ElementPosition fromString(String str) {
        String[] parts = str.split("_");
        if (parts.length == 4) {
            // Block format: world_x_y_z
            return new ElementPosition(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } else if (parts.length == 5) {
            // Item frame format: world_x_y_z_FACING
            return new ElementPosition(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    BlockFace.valueOf(parts[4])
            );
        } else {
            throw new IllegalArgumentException("Invalid ElementPosition string: " + str);
        }
    }
}
