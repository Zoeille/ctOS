package com.ctos.trafficlight.model;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A traffic light element that represents an item frame.
 * Uses position + facing to locate the item frame entity in the world.
 */
public final class ItemFrameElement implements TrafficLightElement {

    private static final Logger LOGGER = Logger.getLogger("ctOS");

    private final ElementPosition position;
    private final ItemFrameStateData frameState;

    public ItemFrameElement(ElementPosition position, ItemFrameStateData frameState) {
        this.position = position;
        this.frameState = frameState;
    }

    /**
     * Captures an item frame from the world
     */
    public static ItemFrameElement capture(ItemFrame itemFrame) {
        Location loc = itemFrame.getLocation();
        BlockFace facing = itemFrame.getFacing();

        ElementPosition pos = ElementPosition.fromLocation(loc, facing);
        ItemFrameStateData state = ItemFrameStateData.capture(itemFrame);

        return new ItemFrameElement(pos, state);
    }

    @Override
    public String getElementType() {
        return "item_frame";
    }

    @Override
    public ElementPosition getPosition() {
        return position;
    }

    public ItemFrameStateData getFrameState() {
        return frameState;
    }

    /**
     * Finds the item frame entity at this position
     * @param world The world to search in
     * @return The ItemFrame or null if not found
     */
    public ItemFrame findItemFrame(World world) {
        Location loc = position.toLocation();

        // Check if chunk is loaded
        if (!loc.isChunkLoaded()) {
            return null;
        }

        Chunk chunk = loc.getChunk();
        if (!chunk.isEntitiesLoaded()) {
            return null;
        }

        // Get all entities in the chunk and filter to item frames
        Entity[] entities = chunk.getEntities();
        BlockFace targetFacing = position.getFacing();

        // Check if we need a glowing frame or regular frame
        boolean needsGlowing = frameState != null && frameState.isGlowing();

        // First pass: if we have exact coordinates, use them for precise matching
        if (frameState != null && frameState.hasExactCoordinates()) {
            double exactX = frameState.getExactX();
            double exactY = frameState.getExactY();
            double exactZ = frameState.getExactZ();

            for (Entity entity : entities) {
                if (entity instanceof ItemFrame frame) {
                    // Check if glowing type matches
                    boolean isGlowing = frame instanceof GlowItemFrame;
                    if (isGlowing != needsGlowing) continue;

                    Location frameLoc = frame.getLocation();
                    // Check within a small tolerance (0.1 blocks)
                    if (Math.abs(frameLoc.getX() - exactX) < 0.1 &&
                        Math.abs(frameLoc.getY() - exactY) < 0.1 &&
                        Math.abs(frameLoc.getZ() - exactZ) < 0.1) {
                        if (targetFacing == null || frame.getFacing() == targetFacing) {
                            return frame;
                        }
                    }
                }
            }
        }

        // Second pass: try to find by block position, facing, and glowing type
        for (Entity entity : entities) {
            if (entity instanceof ItemFrame frame) {
                // Check if glowing type matches
                boolean isGlowing = frame instanceof GlowItemFrame;
                if (frameState != null && isGlowing != needsGlowing) continue;

                Location frameLoc = frame.getLocation();
                if (frameLoc.getBlockX() == position.getX() &&
                    frameLoc.getBlockY() == position.getY() &&
                    frameLoc.getBlockZ() == position.getZ()) {
                    if (targetFacing == null || frame.getFacing() == targetFacing) {
                        return frame;
                    }
                }
            }
        }

        // Third pass: check neighboring blocks (item frame might be stored at different position)
        if (targetFacing != null) {
            // Check block in front
            int offsetX = position.getX() + targetFacing.getModX();
            int offsetY = position.getY() + targetFacing.getModY();
            int offsetZ = position.getZ() + targetFacing.getModZ();

            for (Entity entity : entities) {
                if (entity instanceof ItemFrame frame) {
                    // Check if glowing type matches
                    boolean isGlowing = frame instanceof GlowItemFrame;
                    if (frameState != null && isGlowing != needsGlowing) continue;

                    Location frameLoc = frame.getLocation();
                    if (frameLoc.getBlockX() == offsetX &&
                        frameLoc.getBlockY() == offsetY &&
                        frameLoc.getBlockZ() == offsetZ &&
                        frame.getFacing() == targetFacing) {
                        return frame;
                    }
                }
            }

            // Check block behind (attached block)
            int attachedX = position.getX() - targetFacing.getModX();
            int attachedY = position.getY() - targetFacing.getModY();
            int attachedZ = position.getZ() - targetFacing.getModZ();

            for (Entity entity : entities) {
                if (entity instanceof ItemFrame frame) {
                    // Check if glowing type matches
                    boolean isGlowing = frame instanceof GlowItemFrame;
                    if (frameState != null && isGlowing != needsGlowing) continue;

                    Location frameLoc = frame.getLocation();
                    if (frameLoc.getBlockX() == attachedX &&
                        frameLoc.getBlockY() == attachedY &&
                        frameLoc.getBlockZ() == attachedZ &&
                        frame.getFacing() == targetFacing) {
                        return frame;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Finds the item frame or spawns a new one if it doesn't exist
     * @param world The world to search/spawn in
     * @return The ItemFrame (existing or newly spawned), or null if spawn failed
     */
    public ItemFrame findOrSpawnItemFrame(World world) {
        // First try to find existing frame
        ItemFrame existing = findItemFrame(world);
        if (existing != null) {
            return existing;
        }

        // No frame found, try to spawn one
        if (frameState == null) {
            LOGGER.warning("Cannot spawn item frame at " + position + ": no frame state stored");
            return null;
        }

        Location loc = position.toLocation();

        // Check if chunk is loaded
        if (!loc.isChunkLoaded()) {
            return null;
        }

        BlockFace facing = position.getFacing();
        if (facing == null) {
            facing = frameState.getFacing();
        }
        if (facing == null) {
            facing = BlockFace.NORTH; // Default
        }

        try {
            // Use exact coordinates if available, otherwise use block position
            Location spawnLoc;
            if (frameState.hasExactCoordinates()) {
                spawnLoc = new Location(world,
                    frameState.getExactX(),
                    frameState.getExactY(),
                    frameState.getExactZ());
            } else {
                spawnLoc = loc.clone();
            }

            // Determine if we need a GlowItemFrame or regular ItemFrame
            Class<? extends ItemFrame> frameClass = frameState.isGlowing()
                ? GlowItemFrame.class
                : ItemFrame.class;

            final BlockFace finalFacing = facing;
            ItemFrame newFrame = world.spawn(spawnLoc, frameClass, frame -> {
                // Set facing - this will position the frame on the correct face
                frame.setFacingDirection(finalFacing, true);
                frame.setVisible(frameState.isVisible());
                frame.setFixed(frameState.isFixed());
            });

            LOGGER.info("Spawned new item frame at " + position + " facing " + finalFacing);
            return newFrame;
        } catch (Exception e) {
            LOGGER.warning("Failed to spawn item frame at " + position + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void apply(World world, String direction) {
        if (frameState == null) return;

        ItemFrame itemFrame = findOrSpawnItemFrame(world);
        if (itemFrame == null) {
            LOGGER.warning("Could not find or spawn item frame at " + position);
            return;
        }

        frameState.applyToItemFrame(itemFrame, false);
    }

    @Override
    public void applyWithFacingFrom(World world, TrafficLightElement facingSource) {
        // Item frames don't need facing adjustment from other elements
        // They maintain their own orientation
        apply(world, null);
    }

    @Override
    public String extractFacing() {
        if (position.getFacing() != null) {
            return position.getFacing().name().toLowerCase();
        }
        if (frameState != null && frameState.getFacing() != null) {
            return frameState.getFacing().name().toLowerCase();
        }
        return null;
    }

    @Override
    public TrafficLightElement copy() {
        return new ItemFrameElement(position, frameState != null ? frameState.copy() : null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemFrameElement that = (ItemFrameElement) o;
        return Objects.equals(position, that.position) &&
                Objects.equals(frameState, that.frameState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, frameState);
    }
}
