# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
- Gson adapters for new types (ElementPosition, TrafficLightElement, ItemFrameStateData, BlockFace, Rotation)

### Changed

- `TrafficLightAnimator` handles both block and element-based updates
- `/ctos info` displays neutral element type (Block or Item Frame)
- Edit mode detection includes `neutralElement` check
- Item frame contents changed without dropping items (uses temporary `fixed=true`)

### Fixed

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

[Unreleased]: https://github.com/username/ctOS/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/username/ctOS/releases/tag/v1.0.0
