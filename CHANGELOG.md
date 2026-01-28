# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-01-28

### Added

#### TrainCarts BART Station Integration
- TrainCarts integration with `sf-bart-station` sign action
- BART station redstone trigger system for automatic train station automation
- Automatic redstone block placement when trains reach speed 0 at stations
- Configurable delay for redstone activation (1-300 seconds, default 10s)
- `BartRedstoneController` service for managing redstone placement/removal
- `BartStationManager` for centralized station configuration management
- `BartStationPersistence` for JSON-based configuration storage
- Speed polling system to detect when moving trains stop (checks every 0.25s)
- Sign validation with automatic station name detection from line 3

#### BART Commands
- `/ctos bart setup` - Interactive wand-based station configuration
- `/ctos bart list` - Display all configured stations with clickable edit/remove buttons
- `/ctos bart edit <station|id>` - Edit delay configuration for existing stations
- `/ctos bart remove <station|id>` - Delete station configurations
- Auto-completion suggestions for station names and IDs

#### Debug & Logging
- Comprehensive debug logging for BART train tracking (enabled with `debug: true` in config.yml):
  - Train entry detection with speed monitoring
  - Real-time speed polling logs during train deceleration
  - Redstone activation/deactivation events with timestamps
  - Countdown logs at 75%, 50%, and 25% of delay duration
  - Train departure tracking
- Debug logs respect `debug` config setting to reduce console spam in production

#### Traffic Lights
- Item Frame support for traffic lights (regular and GlowItemFrame)
- `TrafficLightElement` sealed interface for unified block/item frame handling
- `BlockElement` wrapper class for block-based elements
- `ItemFrameElement` class with entity lookup and auto-spawn capabilities
- `ElementPosition` class for unified positioning with optional BlockFace
- `ItemFrameStateData` class storing complete item frame state
- Right-click item frame selection during setup
- Item frame as neutral element option
- "cancel" command available at any setup step
- Exact coordinate storage for precise item frame matching
- Automatic item frame respawn when destroyed
- Json adapters for new types (ElementPosition, TrafficLightElement, ItemFrameStateData, BlockFace, Rotation)

### Changed

- All BART-related messages translated to English
- `paper-plugin.yml` now properly requires `BKCommonLib` and `Train_Carts` plugins as dependencies
- BART station list now displays delay duration alongside redstone position
- `TrafficLightAnimator` handles both block and element-based updates
- `/ctos info` displays neutral element type (Block or Item Frame)
- Edit mode detection includes `neutralElement` check
- Item frame contents changed without dropping items (uses temporary `fixed=true`)

### Fixed

- TrainCarts dependency loading order (BKCommonLib loads before Train_Carts)
- `NoClassDefFoundError` for TrainCarts SignAction classes
- Plugin dependency name mismatch (`Train_Carts` vs `TrainCarts`)
- BART configurations not persisting between server restarts
- Item frames not found due to position mismatch
- Item frames not updating to neutral state when neutral was a block
- Items dropping when changing item frame contents
- "Neutral block not set" message for item frame intersections
- Intersection animation continuing after removal
- "You need at least 2 sides" error with element-based setup

## [1.0.0] - 2024-01-01

### Added

- Traffic light intersection management system
- Block-based traffic light configuration
- Automatic traffic light cycling
- Configurable timing (green, orange, gap durations)
- Direction-based phase grouping (North-South / East-West)
- Pedestrian light support (green/red)
- Wand-based interactive setup system
- JSON persistence for intersections
- Debug mode for troubleshooting
- `/ctos` command with subcommands (wand, create, edit, remove, list, info, reload)

[1.1.0]: https://github.com/Zoeille/ctOS/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Zoeille/ctOS/releases/tag/v1.0.0
