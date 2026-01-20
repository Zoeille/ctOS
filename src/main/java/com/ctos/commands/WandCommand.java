package com.ctos.commands;

import com.ctos.CtOSPlugin;
import com.ctos.trafficlight.model.Intersection;
import com.ctos.trafficlight.service.IntersectionManager;
import com.ctos.trafficlight.service.IntersectionPersistence;
import com.ctos.trafficlight.state.SetupSession;
import com.ctos.trafficlight.state.WandState;
import com.ctos.trafficlight.state.WandStateManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.ctos.trafficlight.model.BlockPosition;
import com.ctos.trafficlight.model.TrafficLightSide;
import com.ctos.trafficlight.model.LightPhase;

/**
 * Handles all ctOS commands
 */
public class WandCommand {
    private final CtOSPlugin plugin;
    private final WandStateManager wandStateManager;
    private final IntersectionManager intersectionManager;
    private final IntersectionPersistence intersectionPersistence;

    public WandCommand(CtOSPlugin plugin, WandStateManager wandStateManager, IntersectionManager intersectionManager, IntersectionPersistence intersectionPersistence) {
        this.plugin = plugin;
        this.wandStateManager = wandStateManager;
        this.intersectionManager = intersectionManager;
        this.intersectionPersistence = intersectionPersistence;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("ctos")
                .requires(requirement -> requirement.getSender().hasPermission("ctos.use"))
                .then(Commands.literal("wand")
                        .executes(context -> {
                            handleWand(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("list")
                        .executes(context -> {
                            handleList(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("info")
                        .then(Commands.argument("identifier", StringArgumentType.string())
                                .suggests(this::intersectionSuggestions)
                                .executes(context -> {
                                    String identifier = context.getArgument("identifier", String.class);
                                    handleInfo(context.getSource().getSender(), new String[]{"info", identifier});
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("identifier", StringArgumentType.string())
                                .suggests(this::intersectionSuggestions)
                                .executes(context -> {
                                    String identifier = context.getArgument("identifier", String.class);
                                    handleRemove(context.getSource().getSender(), new String[]{"remove", identifier});
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("edit")
                        .then(Commands.argument("identifier", StringArgumentType.string())
                                .suggests(this::intersectionSuggestions)
                                .executes(context -> {
                                    String identifier = context.getArgument("identifier", String.class);
                                    handleEdit(context.getSource().getSender(), new String[]{"edit", identifier});
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("cancel")
                        .executes(context -> {
                            handleCancel(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("reload")
                        .executes(context -> {
                            handleReload(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .executes(context -> {
                    sendHelp(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    /**
     * Gives the player a wand and starts a setup session
     */
    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command").color(NamedTextColor.RED));
            return;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("ctos.wand")) {
            player.sendMessage(Component.text("You don't have permission to use the wand").color(NamedTextColor.RED));
            return;
        }

        // Give wand item
        ItemStack wand = WandState.createWand();
        player.getInventory().addItem(wand);

        // Start setup session
        SetupSession session = wandStateManager.startSession(player);
        session.sendPrompt(player);

        player.sendMessage(Component.text("You received the ctOS Wand!").color(NamedTextColor.GREEN));

        return;
    }

    /**
     * Lists all intersections with clickable actions
     */
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("ctos.use")) {
            sender.sendMessage(Component.text("You don't have permission to list intersections").color(NamedTextColor.RED));
            return;
        }

        List<Intersection> intersections = new ArrayList<>(intersectionManager.getAllIntersections());

        if (intersections.isEmpty()) {
            sender.sendMessage(Component.text("No intersections configured").color(NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("=== Intersections ===").color(NamedTextColor.GOLD));
        for (Intersection intersection : intersections) {
            String id = intersection.getId().toString();

            // Info button
            Component infoButton = Component.text("[Info]")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/ctos info " + id))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to view details")));

            // Edit button
            Component editButton = Component.text("[Edit]")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/ctos edit " + id))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to edit this intersection")));

            // Remove button
            Component removeButton = Component.text("[Remove]")
                    .color(NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.suggestCommand("/ctos remove " + id))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to remove this intersection")));

            Component message = Component.text("- ")
                    .append(Component.text(intersection.getName()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" (" + intersection.getSides().size() + " sides) ").color(NamedTextColor.GRAY))
                    .append(infoButton)
                    .append(Component.text(" "))
                    .append(editButton)
                    .append(Component.text(" "))
                    .append(removeButton);

            sender.sendMessage(message);
        }

        return;
    }

    /**
     * Removes an intersection
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ctos.admin")) {
            sender.sendMessage(Component.text("You don't have permission to remove intersections").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /ctos remove <id|name>").color(NamedTextColor.RED));
            return;
        }

        String identifier = args[1];

        // Try to parse as UUID
        try {
            UUID id = UUID.fromString(identifier);
            if (intersectionManager.hasIntersection(id)) {
                intersectionManager.removeIntersection(id);
                this.intersectionPersistence.deleteIntersection(id);
                sender.sendMessage(Component.text("Removed intersection").color(NamedTextColor.GREEN));
                return;
            }
        } catch (IllegalArgumentException e) {
            // Not a UUID, try by name
            List<Intersection> matches = intersectionManager.findIntersectionsByName(identifier);

            if (matches.isEmpty()) {
                sender.sendMessage(Component.text("No intersection found with that ID or name").color(NamedTextColor.RED));
                return;
            }

            if (matches.size() > 1) {
                sender.sendMessage(Component.text("Multiple intersections match that name. Use the ID instead:").color(NamedTextColor.YELLOW));
                for (Intersection match : matches) {
                    sender.sendMessage(Component.text("- " + match.getName() + " (" + match.getId() + ")").color(NamedTextColor.GRAY));
                }
                return;
            }

            Intersection intersection = matches.getFirst();
            intersectionManager.removeIntersection(intersection.getId());
            this.intersectionPersistence.deleteIntersection(intersection.getId());
            sender.sendMessage(Component.text("Removed intersection: " + intersection.getName()).color(NamedTextColor.GREEN));
            return;
        }

        sender.sendMessage(Component.text("Intersection not found").color(NamedTextColor.RED));
        return;
    }

    /**
     * Shows information about an intersection
     * If no argument given and sender is a player, finds the nearest intersection
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ctos.use")) {
            sender.sendMessage(Component.text("You don't have permission to view intersection info").color(NamedTextColor.RED));
            return;
        }

        Intersection intersection = null;

        if (args.length < 2) {
            // No argument - try to find nearest intersection if player
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("Usage: /ctos info <id|name>").color(NamedTextColor.RED));
                return;
            }

            Player player = (Player) sender;
            intersection = findNearestIntersection(player, 50); // 50 blocks max distance

            if (intersection == null) {
                sender.sendMessage(Component.text("No intersection found nearby. Usage: /ctos info <id|name>").color(NamedTextColor.RED));
                return;
            }

            sender.sendMessage(Component.text("Found nearby intersection: " + intersection.getName()).color(NamedTextColor.GRAY));
        } else {
            // Argument given - find by ID or name
            String identifier = args[1];

            // Try to parse as UUID
            try {
                UUID id = UUID.fromString(identifier);
                intersection = intersectionManager.getIntersection(id).orElse(null);
            } catch (IllegalArgumentException e) {
                // Not a UUID, try by name
                List<Intersection> matches = intersectionManager.findIntersectionsByName(identifier);
                if (matches.size() == 1) {
                    intersection = matches.get(0);
                } else if (matches.size() > 1) {
                    sender.sendMessage(Component.text("Multiple intersections match that name:").color(NamedTextColor.YELLOW));
                    for (Intersection match : matches) {
                        sender.sendMessage(Component.text("- " + match.getName() + " (" + match.getId() + ")").color(NamedTextColor.GRAY));
                    }
                    return;
                }
            }

            if (intersection == null) {
                sender.sendMessage(Component.text("Intersection not found").color(NamedTextColor.RED));
                return;
            }
        }

        // Display intersection info
        displayIntersectionInfo(sender, intersection);
        return;
    }

    /**
     * Finds the nearest intersection to a player within maxDistance blocks
     */
    private Intersection findNearestIntersection(Player player, double maxDistance) {
        Location playerLoc = player.getLocation();
        Intersection nearest = null;
        double nearestDistance = maxDistance;

        for (Intersection intersection : intersectionManager.getAllIntersections()) {
            Set<BlockPosition> blocks = intersection.getAllBlocks();

            for (BlockPosition blockPos : blocks) {
                // Check if same world
                if (!blockPos.getWorldName().equals(playerLoc.getWorld().getName())) {
                    continue;
                }

                // Calculate distance
                double dx = blockPos.getX() - playerLoc.getX();
                double dy = blockPos.getY() - playerLoc.getY();
                double dz = blockPos.getZ() - playerLoc.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = intersection;
                }
            }
        }

        return nearest;
    }

    /**
     * Displays detailed information about an intersection
     */
    private void displayIntersectionInfo(CommandSender sender, Intersection intersection) {
        sender.sendMessage(Component.text("=== Intersection: " + intersection.getName() + " ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: ").color(NamedTextColor.GRAY)
                .append(Component.text(intersection.getId().toString()).color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Status: ").color(NamedTextColor.GRAY)
                .append(Component.text(intersection.isComplete() ? "Complete" : "Incomplete")
                        .color(intersection.isComplete() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("Sides: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(intersection.getSides().size())).color(NamedTextColor.WHITE)));

        // Display each side
        int sideIndex = 0;
        for (TrafficLightSide side : intersection.getSides()) {
            sideIndex++;
            int redBlocks = side.getLightBlocks(LightPhase.RED).size();
            int orangeBlocks = side.getLightBlocks(LightPhase.ORANGE).size();
            int greenBlocks = side.getLightBlocks(LightPhase.GREEN).size();

            sender.sendMessage(Component.text("  Side " + sideIndex + " (" + side.getDirection() + "): ").color(NamedTextColor.YELLOW)
                    .append(Component.text(redBlocks + "R ").color(NamedTextColor.RED))
                    .append(Component.text(orangeBlocks + "O ").color(NamedTextColor.GOLD))
                    .append(Component.text(greenBlocks + "G").color(NamedTextColor.GREEN)));
        }

        // Display neutral state
        if (intersection.getNeutralState() != null) {
            sender.sendMessage(Component.text("Neutral block: ").color(NamedTextColor.GRAY)
                    .append(Component.text(intersection.getNeutralState().getMaterial().toString()).color(NamedTextColor.WHITE)));
        } else {
            sender.sendMessage(Component.text("Neutral block: ").color(NamedTextColor.GRAY)
                    .append(Component.text("Not set").color(NamedTextColor.RED)));
        }

        // Display timing info
        if (intersection.getTiming() != null) {
            sender.sendMessage(Component.text("Timing: ").color(NamedTextColor.GRAY)
                    .append(Component.text("Green=" + intersection.getTiming().getGreenDurationTicks() + "t, ")
                            .color(NamedTextColor.GREEN))
                    .append(Component.text("Orange=" + intersection.getTiming().getOrangeDurationTicks() + "t, ")
                            .color(NamedTextColor.GOLD))
                    .append(Component.text("Gap=" + intersection.getTiming().getAllRedGapTicks() + "t")
                            .color(NamedTextColor.RED)));
        }
    }

    /**
     * Starts editing an existing intersection
     * If no argument given and sender is a player, finds the nearest intersection
     */
    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command").color(NamedTextColor.RED));
            return;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("ctos.admin")) {
            player.sendMessage(Component.text("You don't have permission to edit intersections").color(NamedTextColor.RED));
            return;
        }

        Intersection intersection = null;

        if (args.length < 2) {
            // No argument - try to find nearest intersection
            intersection = findNearestIntersection(player, 50);

            if (intersection == null) {
                sender.sendMessage(Component.text("No intersection found nearby. Usage: /ctos edit <id|name>").color(NamedTextColor.RED));
                return;
            }
        } else {
            // Argument given - find by ID or name
            String identifier = args[1];

            try {
                UUID id = UUID.fromString(identifier);
                intersection = intersectionManager.getIntersection(id).orElse(null);
            } catch (IllegalArgumentException e) {
                List<Intersection> matches = intersectionManager.findIntersectionsByName(identifier);
                if (matches.size() == 1) {
                    intersection = matches.get(0);
                } else if (matches.size() > 1) {
                    sender.sendMessage(Component.text("Multiple intersections match that name:").color(NamedTextColor.YELLOW));
                    for (Intersection match : matches) {
                        sender.sendMessage(Component.text("- " + match.getName() + " (" + match.getId() + ")").color(NamedTextColor.GRAY));
                    }
                    return;
                }
            }

            if (intersection == null) {
                sender.sendMessage(Component.text("Intersection not found").color(NamedTextColor.RED));
                return;
            }
        }

        // Give wand if player doesn't have one
        if (!hasWandInInventory(player)) {
            ItemStack wand = WandState.createWand();
            player.getInventory().addItem(wand);
            player.sendMessage(Component.text("You received the ctOS Wand!").color(NamedTextColor.GREEN));
        }

        // Start edit session
        SetupSession session = wandStateManager.startEditSession(player, intersection);
        player.sendMessage(Component.text("Editing intersection: " + intersection.getName()).color(NamedTextColor.GOLD));
        displayEditMenu(player, intersection);
        session.sendPrompt(player);

        return;
    }

    /**
     * Displays the edit menu with current intersection state
     */
    private void displayEditMenu(Player player, Intersection intersection) {
        player.sendMessage(Component.text("=== Edit Mode ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Current sides:").color(NamedTextColor.GRAY));

        int index = 1;
        for (TrafficLightSide side : intersection.getSides()) {
            int r = side.getLightBlocks(LightPhase.RED).size();
            int o = side.getLightBlocks(LightPhase.ORANGE).size();
            int g = side.getLightBlocks(LightPhase.GREEN).size();
            player.sendMessage(Component.text("  " + index + ". " + side.getDirection() + " ")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text("(" + r + "R/" + o + "O/" + g + "G)").color(NamedTextColor.GRAY)));
            index++;
        }

        player.sendMessage(Component.text("Commands:").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  'add' - Add a new side").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("  'edit <n>' - Re-configure side n").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("  'remove <n>' - Remove side n").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("  'neutral' - Change neutral block").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("  'timing' - Change timing").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("  'done' - Save and exit").color(NamedTextColor.WHITE));
    }

    /**
     * Checks if the player has a wand in their inventory
     */
    private boolean hasWandInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && WandState.isWand(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancels the player's current setup session
     */
    private void handleCancel(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command").color(NamedTextColor.RED));
            return;
        }

        Player player = (Player) sender;

        if (!wandStateManager.hasSession(player)) {
            player.sendMessage(Component.text("You don't have an active setup session").color(NamedTextColor.RED));
            return;
        }

        wandStateManager.cancelSession(player);
        return;
    }

    /**
     * Reloads the plugin configuration
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("ctos.admin")) {
            sender.sendMessage(Component.text("You don't have permission to reload the plugin").color(NamedTextColor.RED));
            return;
        }

        plugin.reloadConfig();
        sender.sendMessage(Component.text("Configuration reloaded").color(NamedTextColor.GREEN));
        return;
    }

    /**
     * Sends help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== ctOS Traffic Lights ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/ctos wand").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Get the setup wand").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ctos list").color(NamedTextColor.YELLOW)
                .append(Component.text(" - List all intersections").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ctos remove <id>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Remove an intersection").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ctos info [id]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Show intersection info (auto-detect if nearby)").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ctos edit [id]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Edit an intersection (auto-detect if nearby)").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ctos cancel").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Cancel current setup").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ctos reload").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration").color(NamedTextColor.GRAY)));
    }


    private CompletableFuture<Suggestions> intersectionSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        this.intersectionManager.getAllIntersections().forEach(intersection -> {
            builder.suggest(intersection.getId().toString());
            builder.suggest(intersection.getName());
        });
        return builder.buildFuture();
    }
}
