package com.ctos.trafficlight.service;

import com.ctos.trafficlight.model.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles saving and loading intersections to/from JSON files
 */
public class IntersectionPersistence {
    private static final Logger LOGGER = Logger.getLogger("ctOS");

    private final File dataDirectory;
    private final Gson gson;

    public IntersectionPersistence(File dataDirectory) {
        this.dataDirectory = dataDirectory;

        // Create GSON with custom type adapters
        GsonBuilder gsonBuilder = new GsonBuilder()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .registerTypeAdapter(BlockPosition.class, new BlockPositionAdapter())
                .registerTypeAdapter(BlockStateData.class, new BlockStateDataAdapter())
                .registerTypeAdapter(Material.class, new MaterialAdapter())
                .registerTypeAdapter(UUID.class, new UUIDAdapter())
                .registerTypeAdapter(ElementPosition.class, new ElementPositionAdapter())
                .registerTypeAdapter(TrafficLightElement.class, new TrafficLightElementAdapter())
                .registerTypeAdapter(ItemFrameStateData.class, new ItemFrameStateDataAdapter())
                .registerTypeAdapter(BlockFace.class, new BlockFaceAdapter())
                .registerTypeAdapter(Rotation.class, new RotationAdapter());

        this.gson = gsonBuilder.create();

        // Ensure data directory exists
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
    }

    /**
     * Saves an intersection to a JSON file
     */
    public void saveIntersection(Intersection intersection) throws IOException {
        File tempFile = new File(dataDirectory, intersection.getId().toString() + ".tmp");
        File finalFile = new File(dataDirectory, intersection.getId().toString() + ".json");

        try (Writer writer = new FileWriter(tempFile)) {
            gson.toJson(intersection, writer);
        }

        // Atomic rename
        if (finalFile.exists()) {
            finalFile.delete();
        }
        if (!tempFile.renameTo(finalFile)) {
            throw new IOException("Failed to rename temp file to final file");
        }

        LOGGER.log(Level.INFO, "Saved intersection: " + intersection.getName() + " (" + intersection.getId() + ")");
    }

    /**
     * Loads an intersection from a JSON file
     */
    public Intersection loadIntersection(UUID id) throws IOException {
        File file = new File(dataDirectory, id.toString() + ".json");

        if (!file.exists()) {
            throw new FileNotFoundException("Intersection file not found: " + id);
        }

        try (Reader reader = new FileReader(file)) {
            Intersection intersection = gson.fromJson(reader, Intersection.class);
            LOGGER.log(Level.INFO, "Loaded intersection: " + intersection.getName() + " (" + id + ")");
            return intersection;
        }
    }

    /**
     * Loads all intersections from the data directory
     */
    public List<Intersection> loadAll() {
        List<Intersection> intersections = new ArrayList<>();

        File[] files = dataDirectory.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null) {
            return intersections;
        }

        for (File file : files) {
            try {
                String fileName = file.getName();
                String idString = fileName.substring(0, fileName.length() - 5); // Remove .json
                UUID id = UUID.fromString(idString);

                Intersection intersection = loadIntersection(id);
                intersections.add(intersection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load intersection from file: " + file.getName(), e);
            }
        }

        LOGGER.log(Level.INFO, "Loaded " + intersections.size() + " intersections");
        return intersections;
    }

    /**
     * Deletes an intersection file
     */
    public void deleteIntersection(UUID id) {
        File file = new File(dataDirectory, id.toString() + ".json");

        if (file.exists()) {
            if (file.delete()) {
                LOGGER.log(Level.INFO, "Deleted intersection file: " + id);
            } else {
                LOGGER.log(Level.WARNING, "Failed to delete intersection file: " + id);
            }
        }
    }

    // GSON Type Adapters

    /**
     * Type adapter for BlockPosition
     * Handles both JSON objects and string representations (for Map keys)
     */
    private static class BlockPositionAdapter implements JsonSerializer<BlockPosition>, JsonDeserializer<BlockPosition> {
        @Override
        public JsonElement serialize(BlockPosition src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("world", src.getWorldName());
            obj.addProperty("x", src.getX());
            obj.addProperty("y", src.getY());
            obj.addProperty("z", src.getZ());
            return obj;
        }

        @Override
        public BlockPosition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            // Handle string format (used as Map keys): "world_x_y_z"
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return BlockPosition.fromString(json.getAsString());
            }

            // Handle object format
            JsonObject obj = json.getAsJsonObject();
            return new BlockPosition(
                    obj.get("world").getAsString(),
                    obj.get("x").getAsInt(),
                    obj.get("y").getAsInt(),
                    obj.get("z").getAsInt()
            );
        }
    }

    /**
     * Type adapter for BlockStateData
     */
    private static class BlockStateDataAdapter implements JsonSerializer<BlockStateData>, JsonDeserializer<BlockStateData> {
        @Override
        public JsonElement serialize(BlockStateData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("material", src.getMaterial().name());
            obj.addProperty("blockData", src.getBlockDataString());

            if (src.getPlayerProfileData() != null) {
                obj.addProperty("playerProfile", src.getPlayerProfileData());
            }

            return obj;
        }

        @Override
        public BlockStateData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            Material material = Material.valueOf(obj.get("material").getAsString());
            String blockData = obj.get("blockData").getAsString();
            String playerProfile = obj.has("playerProfile") ? obj.get("playerProfile").getAsString() : null;

            return new BlockStateData(material, blockData, playerProfile);
        }
    }

    /**
     * Type adapter for Material enum
     */
    private static class MaterialAdapter implements JsonSerializer<Material>, JsonDeserializer<Material> {
        @Override
        public JsonElement serialize(Material src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.name());
        }

        @Override
        public Material deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Material.valueOf(json.getAsString());
        }
    }

    /**
     * Type adapter for UUID
     */
    private static class UUIDAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
        @Override
        public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return UUID.fromString(json.getAsString());
        }
    }

    /**
     * Type adapter for ElementPosition
     */
    private static class ElementPositionAdapter implements JsonSerializer<ElementPosition>, JsonDeserializer<ElementPosition> {
        @Override
        public JsonElement serialize(ElementPosition src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("world", src.getWorldName());
            obj.addProperty("x", src.getX());
            obj.addProperty("y", src.getY());
            obj.addProperty("z", src.getZ());
            if (src.getFacing() != null) {
                obj.addProperty("facing", src.getFacing().name());
            }
            return obj;
        }

        @Override
        public ElementPosition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            // Handle string format (used as Map keys)
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return ElementPosition.fromString(json.getAsString());
            }

            JsonObject obj = json.getAsJsonObject();
            String world = obj.get("world").getAsString();
            int x = obj.get("x").getAsInt();
            int y = obj.get("y").getAsInt();
            int z = obj.get("z").getAsInt();
            BlockFace facing = null;
            if (obj.has("facing") && !obj.get("facing").isJsonNull()) {
                facing = BlockFace.valueOf(obj.get("facing").getAsString());
            }
            return new ElementPosition(world, x, y, z, facing);
        }
    }

    /**
     * Type adapter for TrafficLightElement (polymorphic serialization)
     */
    private class TrafficLightElementAdapter implements JsonSerializer<TrafficLightElement>, JsonDeserializer<TrafficLightElement> {
        @Override
        public JsonElement serialize(TrafficLightElement src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", src.getElementType());
            obj.add("position", context.serialize(src.getPosition(), ElementPosition.class));

            if (src instanceof BlockElement) {
                BlockElement block = (BlockElement) src;
                obj.add("blockState", context.serialize(block.getBlockStateData(), BlockStateData.class));
            } else if (src instanceof ItemFrameElement) {
                ItemFrameElement frame = (ItemFrameElement) src;
                obj.add("frameState", context.serialize(frame.getFrameState(), ItemFrameStateData.class));
            }

            return obj;
        }

        @Override
        public TrafficLightElement deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String type = obj.get("type").getAsString();
            ElementPosition position = context.deserialize(obj.get("position"), ElementPosition.class);

            if ("block".equals(type)) {
                BlockStateData blockState = context.deserialize(obj.get("blockState"), BlockStateData.class);
                return new BlockElement(position, blockState);
            } else if ("item_frame".equals(type)) {
                ItemFrameStateData frameState = context.deserialize(obj.get("frameState"), ItemFrameStateData.class);
                return new ItemFrameElement(position, frameState);
            }

            throw new JsonParseException("Unknown TrafficLightElement type: " + type);
        }
    }

    /**
     * Type adapter for ItemFrameStateData
     */
    private static class ItemFrameStateDataAdapter implements JsonSerializer<ItemFrameStateData>, JsonDeserializer<ItemFrameStateData> {
        @Override
        public JsonElement serialize(ItemFrameStateData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            if (src.getSerializedItem() != null) {
                obj.add("item", context.serialize(src.getSerializedItem()));
            }
            if (src.getRotation() != null) {
                obj.addProperty("rotation", src.getRotation().name());
            }
            if (src.getFacing() != null) {
                obj.addProperty("facing", src.getFacing().name());
            }
            obj.addProperty("visible", src.isVisible());
            obj.addProperty("glowing", src.isGlowing());
            obj.addProperty("fixed", src.isFixed());

            return obj;
        }

        @Override
        public ItemFrameStateData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            Map<String, Object> serializedItem = null;
            if (obj.has("item") && !obj.get("item").isJsonNull()) {
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                serializedItem = context.deserialize(obj.get("item"), mapType);
            }

            Rotation rotation = null;
            if (obj.has("rotation") && !obj.get("rotation").isJsonNull()) {
                rotation = Rotation.valueOf(obj.get("rotation").getAsString());
            }

            BlockFace facing = null;
            if (obj.has("facing") && !obj.get("facing").isJsonNull()) {
                facing = BlockFace.valueOf(obj.get("facing").getAsString());
            }

            boolean visible = obj.has("visible") && obj.get("visible").getAsBoolean();
            boolean glowing = obj.has("glowing") && obj.get("glowing").getAsBoolean();
            boolean fixed = obj.has("fixed") && obj.get("fixed").getAsBoolean();

            return new ItemFrameStateData(serializedItem, rotation, facing, visible, glowing, fixed);
        }
    }

    /**
     * Type adapter for BlockFace enum
     */
    private static class BlockFaceAdapter implements JsonSerializer<BlockFace>, JsonDeserializer<BlockFace> {
        @Override
        public JsonElement serialize(BlockFace src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.name());
        }

        @Override
        public BlockFace deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return BlockFace.valueOf(json.getAsString());
        }
    }

    /**
     * Type adapter for Rotation enum
     */
    private static class RotationAdapter implements JsonSerializer<Rotation>, JsonDeserializer<Rotation> {
        @Override
        public JsonElement serialize(Rotation src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.name());
        }

        @Override
        public Rotation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Rotation.valueOf(json.getAsString());
        }
    }
}
