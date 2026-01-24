package com.ctos.trafficlight.model;

import org.bukkit.World;

/**
 * Sealed interface representing any element of a traffic light (block or item frame).
 */
public sealed interface TrafficLightElement permits BlockElement, ItemFrameElement {

    /**
     * Returns the element type identifier ("block" or "item_frame")
     */
    String getElementType();

    /**
     * Returns the position of this element
     */
    ElementPosition getPosition();

    /**
     * Applies this element's state to the world with automatic direction-based rotation
     * @param world The world to apply to
     * @param direction Cardinal direction (North/South/East/West) for automatic rotation
     */
    void apply(World world, String direction);

    /**
     * Applies this element's state copying the facing from another element
     * @param world The world to apply to
     * @param facingSource The element to copy facing from
     */
    void applyWithFacingFrom(World world, TrafficLightElement facingSource);

    /**
     * Extracts the facing direction from this element
     * @return The facing direction or null if not applicable
     */
    String extractFacing();

    /**
     * Creates a deep copy of this element
     */
    TrafficLightElement copy();
}
