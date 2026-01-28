package com.ctos.traincarts.service;

import com.ctos.trafficlight.model.BlockPosition;
import com.ctos.traincarts.model.BartStationConfig;
import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles saving and loading BART station configurations to/from JSON files
 */
public class BartStationPersistence {
    private static final Logger LOGGER = Logger.getLogger("ctOS");

    private final File dataDirectory;
    private final Gson gson;

    public BartStationPersistence(File dataDirectory) {
        this.dataDirectory = dataDirectory;

        // Create GSON with custom type adapters
        GsonBuilder gsonBuilder = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(BlockPosition.class, new BlockPositionAdapter())
                .registerTypeAdapter(UUID.class, new UUIDAdapter());

        this.gson = gsonBuilder.create();

        // Ensure data directory exists
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
    }

    /**
     * Saves a BART station configuration to a JSON file
     */
    public void saveConfig(BartStationConfig config) throws IOException {
        File tempFile = new File(dataDirectory, config.getId().toString() + ".tmp");
        File finalFile = new File(dataDirectory, config.getId().toString() + ".json");

        try (Writer writer = new FileWriter(tempFile)) {
            gson.toJson(config, writer);
        }

        // Atomic rename
        if (finalFile.exists()) {
            finalFile.delete();
        }
        if (!tempFile.renameTo(finalFile)) {
            throw new IOException("Failed to rename temp file to final file");
        }

        LOGGER.log(Level.INFO, "Saved BART station config: " + config.getId());
    }

    /**
     * Loads a BART station configuration from a JSON file
     */
    public BartStationConfig loadConfig(UUID id) throws IOException {
        File file = new File(dataDirectory, id.toString() + ".json");

        if (!file.exists()) {
            throw new FileNotFoundException("BART station config file not found: " + id);
        }

        try (Reader reader = new FileReader(file)) {
            BartStationConfig config = gson.fromJson(reader, BartStationConfig.class);
            LOGGER.log(Level.INFO, "Loaded BART station config: " + id);
            return config;
        }
    }

    /**
     * Loads all BART station configurations from the data directory
     */
    public List<BartStationConfig> loadAll() {
        List<BartStationConfig> configs = new ArrayList<>();

        File[] files = dataDirectory.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null) {
            return configs;
        }

        for (File file : files) {
            try {
                String fileName = file.getName();
                String idString = fileName.substring(0, fileName.length() - 5); // Remove .json
                UUID id = UUID.fromString(idString);

                BartStationConfig config = loadConfig(id);
                configs.add(config);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load BART station config from file: " + file.getName(), e);
            }
        }

        LOGGER.log(Level.INFO, "Loaded " + configs.size() + " BART station configurations");
        return configs;
    }

    /**
     * Deletes a BART station configuration file
     */
    public void deleteConfig(UUID id) {
        File file = new File(dataDirectory, id.toString() + ".json");

        if (file.exists()) {
            if (file.delete()) {
                LOGGER.log(Level.INFO, "Deleted BART station config file: " + id);
            } else {
                LOGGER.log(Level.WARNING, "Failed to delete BART station config file: " + id);
            }
        }
    }

    // GSON Type Adapters

    /**
     * Type adapter for BlockPosition
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
            // Handle string format: "world_x_y_z"
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
}
