package com.ctos.trafficlight.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

/**
 * A traffic light element that wraps a BlockStateData.
 * Implements TrafficLightElement for unified handling with item frames.
 */
public final class BlockElement implements TrafficLightElement {

    private final ElementPosition position;
    private final BlockStateData blockStateData;

    public BlockElement(ElementPosition position, BlockStateData blockStateData) {
        this.position = position;
        this.blockStateData = blockStateData;
    }

    /**
     * Creates a BlockElement from a BlockPosition and BlockStateData
     */
    public static BlockElement fromBlockPosition(BlockPosition blockPosition, BlockStateData blockStateData) {
        return new BlockElement(ElementPosition.fromBlockPosition(blockPosition), blockStateData);
    }

    /**
     * Captures a block from the world
     */
    public static BlockElement capture(Block block) {
        ElementPosition pos = ElementPosition.fromLocation(block.getLocation());
        BlockStateData state = BlockStateData.capture(block);
        return new BlockElement(pos, state);
    }

    @Override
    public String getElementType() {
        return "block";
    }

    @Override
    public ElementPosition getPosition() {
        return position;
    }

    public BlockStateData getBlockStateData() {
        return blockStateData;
    }

    /**
     * Converts this element's position to a BlockPosition
     */
    public BlockPosition toBlockPosition() {
        return position.toBlockPosition();
    }

    @Override
    public void apply(World world, String direction) {
        if (blockStateData == null) return;

        Location location = position.toLocation();
        if (!location.isChunkLoaded()) return;

        Block block = location.getBlock();
        blockStateData.applyToBlock(block, direction);
    }

    @Override
    public void applyWithFacingFrom(World world, TrafficLightElement facingSource) {
        if (blockStateData == null) return;

        Location location = position.toLocation();
        if (!location.isChunkLoaded()) return;

        Block block = location.getBlock();

        if (facingSource instanceof BlockElement) {
            blockStateData.applyToBlockWithFacingFrom(block, ((BlockElement) facingSource).getBlockStateData());
        } else {
            // For item frames, extract facing and use it
            String facing = facingSource != null ? facingSource.extractFacing() : null;
            blockStateData.applyToBlock(block, facing);
        }
    }

    @Override
    public String extractFacing() {
        return blockStateData != null ? blockStateData.extractFacing() : null;
    }

    @Override
    public TrafficLightElement copy() {
        return new BlockElement(position, blockStateData != null ? blockStateData.clone() : null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockElement that = (BlockElement) o;
        return Objects.equals(position, that.position) &&
                Objects.equals(blockStateData, that.blockStateData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, blockStateData);
    }
}
