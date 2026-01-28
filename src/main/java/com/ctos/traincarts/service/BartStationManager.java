package com.ctos.traincarts.service;

import com.ctos.traincarts.model.BartStationConfig;

import java.util.*;
import java.util.logging.Logger;

/**
 * Central registry and management of all BART station configurations.
 * Provides fast lookups by ID or station name.
 */
public class BartStationManager {
    private static final Logger LOGGER = Logger.getLogger("ctOS");

    private final Map<UUID, BartStationConfig> configs;
    private final Map<String, UUID> stationNameToConfig;

    public BartStationManager() {
        this.configs = new HashMap<>();
        this.stationNameToConfig = new HashMap<>();
    }

    /**
     * Registers a BART station configuration
     */
    public void registerConfig(BartStationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        configs.put(config.getId(), config);
        stationNameToConfig.put(config.getStationName().toLowerCase(), config.getId());

        LOGGER.info("Registered BART station config: " + config.getId() +
                " for station '" + config.getStationName() + "'");
    }

    /**
     * Removes a configuration by ID
     */
    public void removeConfig(UUID id) {
        BartStationConfig config = configs.remove(id);

        if (config != null) {
            stationNameToConfig.remove(config.getStationName().toLowerCase());
            LOGGER.info("Removed BART station config: " + id);
        }
    }

    /**
     * Gets a configuration by ID
     */
    public Optional<BartStationConfig> getConfig(UUID id) {
        return Optional.ofNullable(configs.get(id));
    }

    /**
     * Gets a configuration by station name
     */
    public BartStationConfig getByStationName(String stationName) {
        if (stationName == null || stationName.isEmpty()) {
            return null;
        }
        UUID id = stationNameToConfig.get(stationName.toLowerCase());
        if (id == null) {
            return null;
        }
        return configs.get(id);
    }

    /**
     * Checks if a station name has a configuration
     */
    public boolean hasConfigForStation(String stationName) {
        if (stationName == null || stationName.isEmpty()) {
            return false;
        }
        return stationNameToConfig.containsKey(stationName.toLowerCase());
    }

    /**
     * Gets all registered configurations
     */
    public Collection<BartStationConfig> getAllConfigs() {
        return new ArrayList<>(configs.values());
    }

    /**
     * Gets the number of registered configurations
     */
    public int getConfigCount() {
        return configs.size();
    }

    /**
     * Checks if a configuration with the given ID exists
     */
    public boolean hasConfig(UUID id) {
        return configs.containsKey(id);
    }

    /**
     * Clears all configurations from memory
     */
    public void clear() {
        configs.clear();
        stationNameToConfig.clear();
        LOGGER.info("Cleared all BART station configurations from memory");
    }
}
