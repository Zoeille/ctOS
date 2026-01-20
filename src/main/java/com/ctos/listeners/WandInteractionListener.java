package com.ctos.listeners;

import com.ctos.CtOSPlugin;
import com.ctos.trafficlight.model.BlockPosition;
import com.ctos.trafficlight.model.BlockStateData;
import com.ctos.trafficlight.model.Intersection;
import com.ctos.trafficlight.model.TimingConfiguration;
import com.ctos.trafficlight.service.IntersectionManager;
import com.ctos.trafficlight.service.IntersectionPersistence;
import com.ctos.trafficlight.state.SetupSession;
import com.ctos.trafficlight.state.SetupStep;
import com.ctos.trafficlight.state.WandState;
import com.ctos.trafficlight.state.WandStateManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles player interactions with the wand
 */
public class WandInteractionListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger("ctOS");

    private final CtOSPlugin plugin;
    private final WandStateManager wandStateManager;
    private final IntersectionManager intersectionManager;
    private final IntersectionPersistence persistence;

    public WandInteractionListener(CtOSPlugin plugin, WandStateManager wandStateManager,
                                    IntersectionManager intersectionManager, IntersectionPersistence persistence) {
        this.plugin = plugin;
        this.wandStateManager = wandStateManager;
        this.intersectionManager = intersectionManager;
        this.persistence = persistence;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if player is holding the wand
        if (!WandState.isWand(item)) {
            return;
        }

        Optional<SetupSession> sessionOpt = wandStateManager.getSession(player);
        if (!sessionOpt.isPresent()) {
            return;
        }

        SetupSession session = sessionOpt.get();
        session.touch(); // Update last interaction time

        // Handle right-click (block selection)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleBlockSelection(player, session, event.getClickedBlock());
        }
        // Handle left-click (confirmation)
        else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleConfirmation(player, session);
        }
    }

    /**
     * Handles block selection (right-click)
     */
    private void handleBlockSelection(Player player, SetupSession session, Block block) {
        SetupStep step = session.getCurrentStep();

        switch (step) {
            case SELECT_NEUTRAL_BLOCK:
                BlockStateData neutralState = BlockStateData.capture(block);
                session.getIntersectionInProgress().setNeutralState(neutralState);
                player.sendMessage(Component.text("[ctOS] Neutral block set to: " + block.getType().name())
                        .color(NamedTextColor.GREEN));

                // Check if we're in edit mode (intersection already has sides)
                if (session.getIntersectionInProgress().getSides().size() > 0) {
                    // Return to edit menu
                    session.setCurrentStep(SetupStep.EDIT_MENU);
                    session.sendPrompt(player);
                } else {
                    // Normal setup: Detect direction for first side
                    String direction = SetupSession.detectDirection(player.getLocation().getYaw());
                    player.sendMessage(Component.text("[ctOS] Starting first side: " + direction + " (based on where you're looking)")
                            .color(NamedTextColor.AQUA));

                    session.advanceToNextStep(); // Goes to START_SIDE
                    session.startNewSide(direction); // Creates first side with detected direction
                    session.advanceToNextStep(); // Goes to SELECT_RED_BLOCKS
                    session.sendPrompt(player);
                }
                break;

            case SELECT_RED_BLOCKS:
            case SELECT_ORANGE_BLOCKS:
            case SELECT_GREEN_BLOCKS:
            case SELECT_PEDESTRIAN_GREEN:
            case SELECT_PEDESTRIAN_RED:
                // Add to buffer
                BlockPosition pos = BlockPosition.fromLocation(block.getLocation());
                BlockStateData state = BlockStateData.capture(block);
                session.addToBuffer(pos, state);

                int count = session.getBufferSize();
                player.sendMessage(Component.text("[ctOS] Block selected (" + count + " total). Left-click to confirm.")
                        .color(NamedTextColor.YELLOW));
                break;

            default:
                player.sendMessage(Component.text("[ctOS] Cannot select blocks at this step.")
                        .color(NamedTextColor.RED));
                break;
        }
    }

    /**
     * Handles confirmation (left-click)
     */
    private void handleConfirmation(Player player, SetupSession session) {
        SetupStep step = session.getCurrentStep();

        switch (step) {
            case SELECT_RED_BLOCKS:
            case SELECT_ORANGE_BLOCKS:
            case SELECT_GREEN_BLOCKS:
                if (session.getBufferSize() == 0) {
                    player.sendMessage(Component.text("[ctOS] No blocks selected! Right-click blocks first.")
                            .color(NamedTextColor.RED));
                    return;
                }

                session.confirmCurrentSelection();
                player.sendMessage(Component.text("[ctOS] Blocks confirmed for " + step.name())
                        .color(NamedTextColor.GREEN));
                session.advanceToNextStep();
                session.sendPrompt(player);
                break;

            case SELECT_PEDESTRIAN_GREEN:
                // Pedestrian green lights are optional
                // Check buffer size BEFORE confirming (which clears the buffer)
                int greenBlockCount = session.getBufferSize();
                session.confirmCurrentSelection();

                if (greenBlockCount == 0) {
                    player.sendMessage(Component.text("[ctOS] No pedestrian green lights added (skipped).")
                            .color(NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("[ctOS] Pedestrian green blocks confirmed! (" + greenBlockCount + " blocks)")
                            .color(NamedTextColor.GREEN));
                }
                session.advanceToNextStep();
                session.sendPrompt(player);
                break;

            case SELECT_PEDESTRIAN_RED:
                // Pedestrian red lights are optional
                // Check buffer size BEFORE confirming (which clears the buffer)
                int redBlockCount = session.getBufferSize();
                session.confirmCurrentSelection();

                if (redBlockCount == 0) {
                    player.sendMessage(Component.text("[ctOS] No pedestrian red lights added (skipped).")
                            .color(NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("[ctOS] Pedestrian red blocks confirmed! (" + redBlockCount + " blocks)")
                            .color(NamedTextColor.GREEN));
                }
                session.advanceToNextStep();
                session.sendPrompt(player);
                break;

            default:
                player.sendMessage(Component.text("[ctOS] Nothing to confirm at this step.")
                        .color(NamedTextColor.RED));
                break;
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        Optional<SetupSession> sessionOpt = wandStateManager.getSession(player);
        if (!sessionOpt.isPresent()) {
            return;
        }

        SetupSession session = sessionOpt.get();
        SetupStep step = session.getCurrentStep();

        // Only handle chat if the current step requires text input
        if (!step.requiresTextInput()) {
            return;
        }

        event.setCancelled(true);
        session.touch();

        // Extract text from the chat message
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());

        handleChatInput(player, session, input);
    }

    /**
     * Handles chat input during setup
     */
    private void handleChatInput(Player player, SetupSession session, String input) {
        SetupStep step = session.getCurrentStep();

        switch (step) {
            case NAME_INTERSECTION:
                session.getIntersectionInProgress().setName(input);
                player.sendMessage(Component.text("[ctOS] Intersection named: " + input)
                        .color(NamedTextColor.GREEN));
                session.advanceToNextStep();
                session.sendPrompt(player);
                break;

            case EDIT_MENU:
                handleEditMenuInput(player, session, input);
                break;

            case CONFIRM_SIDE:
                handleSideConfirmation(player, session, input);
                break;

            case CONFIGURE_TIMING:
                handleTimingInput(player, session, input);
                break;

            case CONFIRM_COMPLETE:
                handleFinalConfirmation(player, session, input);
                break;

            default:
                player.sendMessage(Component.text("[ctOS] Unexpected text input at this step.")
                        .color(NamedTextColor.RED));
                break;
        }
    }

    /**
     * Handles input from the edit menu
     */
    private void handleEditMenuInput(Player player, SetupSession session, String input) {
        String[] parts = input.trim().toLowerCase().split("\\s+");
        String command = parts[0];
        Intersection intersection = session.getIntersectionInProgress();

        switch (command) {
            case "add":
                // Start adding a new side
                String direction = SetupSession.detectDirection(player.getLocation().getYaw());
                player.sendMessage(Component.text("[ctOS] Adding new side: " + direction + " (based on where you're looking)")
                        .color(NamedTextColor.AQUA));
                session.startNewSide(direction);
                session.advanceToNextStep();
                session.sendPrompt(player);
                break;

            case "remove":
                if (parts.length < 2) {
                    player.sendMessage(Component.text("[ctOS] Usage: remove <side number>")
                            .color(NamedTextColor.RED));
                    return;
                }
                try {
                    int sideNum = Integer.parseInt(parts[1]);
                    if (session.removeSide(sideNum - 1)) { // Convert to 0-indexed
                        player.sendMessage(Component.text("[ctOS] Side " + sideNum + " removed!")
                                .color(NamedTextColor.GREEN));
                        displayEditMenuSides(player, intersection);
                    } else {
                        player.sendMessage(Component.text("[ctOS] Invalid side number!")
                                .color(NamedTextColor.RED));
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("[ctOS] Invalid number! Usage: remove <side number>")
                            .color(NamedTextColor.RED));
                }
                break;

            case "edit":
                if (parts.length < 2) {
                    player.sendMessage(Component.text("[ctOS] Usage: edit <side number>")
                            .color(NamedTextColor.RED));
                    return;
                }
                try {
                    int sideNum = Integer.parseInt(parts[1]);
                    int sideIndex = sideNum - 1; // Convert to 0-indexed
                    var sides = intersection.getSides();
                    if (sideIndex < 0 || sideIndex >= sides.size()) {
                        player.sendMessage(Component.text("[ctOS] Invalid side number!")
                                .color(NamedTextColor.RED));
                        return;
                    }
                    // Get the existing side's direction, then remove it and start editing
                    String existingDirection = sides.get(sideIndex).getDirection();
                    session.removeSide(sideIndex);
                    player.sendMessage(Component.text("[ctOS] Editing side " + sideNum + " (" + existingDirection + ")...")
                            .color(NamedTextColor.AQUA));
                    session.startNewSide(existingDirection);
                    session.advanceToNextStep();
                    session.sendPrompt(player);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("[ctOS] Invalid number! Usage: edit <side number>")
                            .color(NamedTextColor.RED));
                }
                break;

            case "neutral":
                player.sendMessage(Component.text("[ctOS] Right-click a block to set as the new neutral state")
                        .color(NamedTextColor.YELLOW));
                session.setCurrentStep(SetupStep.SELECT_NEUTRAL_BLOCK);
                break;

            case "timing":
                session.setCurrentStep(SetupStep.CONFIGURE_TIMING);
                session.sendPrompt(player);
                break;

            case "done":
                // Check if intersection is valid
                if (intersection.getSides().size() < 2) {
                    player.sendMessage(Component.text("[ctOS] You need at least 2 sides!")
                            .color(NamedTextColor.RED));
                    return;
                }
                saveEditedIntersection(player, session);
                break;

            default:
                player.sendMessage(Component.text("[ctOS] Unknown command. Use: add, edit <n>, remove <n>, neutral, timing, done")
                        .color(NamedTextColor.RED));
                break;
        }
    }

    /**
     * Displays the current sides in edit mode
     */
    private void displayEditMenuSides(Player player, Intersection intersection) {
        player.sendMessage(Component.text("Current sides:").color(NamedTextColor.GRAY));
        int index = 1;
        for (var side : intersection.getSides()) {
            player.sendMessage(Component.text("  " + index + ". " + side.getDirection()).color(NamedTextColor.YELLOW));
            index++;
        }
    }

    /**
     * Saves an edited intersection
     */
    private void saveEditedIntersection(Player player, SetupSession session) {
        Intersection intersection = session.getIntersectionInProgress();

        if (!intersection.isComplete()) {
            player.sendMessage(Component.text("[ctOS] Intersection is not complete!")
                    .color(NamedTextColor.RED));
            return;
        }

        try {
            // Re-register with animator to apply changes
            plugin.getAnimator().unregisterIntersection(intersection);

            persistence.saveIntersection(intersection);
            if (!this.intersectionManager.hasIntersection(intersection.getId())) {
                this.intersectionManager.registerIntersection(intersection);
            }

            plugin.getAnimator().registerIntersection(intersection);

            player.sendMessage(Component.text("[ctOS] Intersection '" + intersection.getName() + "' saved!")
                    .color(NamedTextColor.GREEN));

            wandStateManager.removeSession(player);
            WandState.removeWandFromInventory(player);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save intersection", e);
            player.sendMessage(Component.text("[ctOS] Error saving intersection: " + e.getMessage())
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Handles side confirmation (add another side or continue)
     */
    private void handleSideConfirmation(Player player, SetupSession session, String input) {
        Intersection intersection = session.getIntersectionInProgress();
        boolean isEditMode = intersection.getSides().size() > 0 && intersection.getNeutralState() != null;

        if (input.equalsIgnoreCase("next")) {
            // Complete current side
            session.completeSide();

            // Detect direction based on where player is looking
            String direction = SetupSession.detectDirection(player.getLocation().getYaw());

            player.sendMessage(Component.text("[ctOS] Side completed! Starting next side...")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("[ctOS] Detected direction: " + direction + " (based on where you're looking)")
                    .color(NamedTextColor.AQUA));

            session.startNewSide(direction);
            session.advanceToNextStep();
            session.sendPrompt(player);
        } else if (input.equalsIgnoreCase("done")) {
            // Complete current side
            session.completeSide();

            // Check if we have at least 2 sides
            if (session.getSideCount() < 2) {
                player.sendMessage(Component.text("[ctOS] You need at least 2 sides! Type 'next' to add another side.")
                        .color(NamedTextColor.RED));
                return;
            }

            player.sendMessage(Component.text("[ctOS] Side completed!")
                    .color(NamedTextColor.GREEN));

            // If editing, return to edit menu. Otherwise continue to timing.
            if (isEditMode) {
                player.sendMessage(Component.text("[ctOS] Returning to edit menu...")
                        .color(NamedTextColor.AQUA));
                session.setCurrentStep(SetupStep.EDIT_MENU);
                session.sendPrompt(player);
            } else {
                player.sendMessage(Component.text("[ctOS] All sides configured!")
                        .color(NamedTextColor.GREEN));
                session.advanceToNextStep();
                session.sendPrompt(player);
            }
        } else {
            player.sendMessage(Component.text("[ctOS] Type 'next' to add another side, or 'done' to finish sides.")
                    .color(NamedTextColor.YELLOW));
        }
    }

    /**
     * Handles timing configuration input
     * Format: "green,orange,pedestrian,gap" in seconds
     */
    private void handleTimingInput(Player player, SetupSession session, String input) {
        Intersection intersection = session.getIntersectionInProgress();
        boolean isEditMode = intersection.getSides().size() >= 2 && intersection.getNeutralState() != null;

        try {
            String[] parts = input.split(",");
            if (parts.length != 4) {
                player.sendMessage(Component.text("[ctOS] Invalid format! Use: green,orange,pedestrian,gap (e.g., '10,3,7,1')")
                        .color(NamedTextColor.RED));
                return;
            }

            int greenSeconds = Integer.parseInt(parts[0].trim());
            int orangeSeconds = Integer.parseInt(parts[1].trim());
            int pedestrianSeconds = Integer.parseInt(parts[2].trim());
            int gapSeconds = Integer.parseInt(parts[3].trim());

            // Convert to ticks (20 ticks = 1 second)
            TimingConfiguration timing = new TimingConfiguration(
                    greenSeconds * 20,
                    orangeSeconds * 20,
                    pedestrianSeconds * 20,
                    gapSeconds * 20
            );

            intersection.setTiming(timing);
            player.sendMessage(Component.text("[ctOS] Timing configured: Green=" + greenSeconds + "s, Orange=" +
                    orangeSeconds + "s, Pedestrian=" + pedestrianSeconds + "s, Gap=" + gapSeconds + "s")
                    .color(NamedTextColor.GREEN));

            // If editing, return to edit menu. Otherwise continue to confirmation.
            if (isEditMode) {
                player.sendMessage(Component.text("[ctOS] Returning to edit menu...")
                        .color(NamedTextColor.AQUA));
                session.setCurrentStep(SetupStep.EDIT_MENU);
                session.sendPrompt(player);
            } else {
                session.advanceToNextStep();
                session.sendPrompt(player);
            }

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[ctOS] Invalid numbers! Use: green,orange,pedestrian,gap (e.g., '10,3,7,1')")
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Handles final confirmation
     */
    private void handleFinalConfirmation(Player player, SetupSession session, String input) {
        if (input.equalsIgnoreCase("confirm")) {
            Intersection intersection = session.getIntersectionInProgress();

            // Validate intersection
            if (!intersection.isComplete()) {
                player.sendMessage(Component.text("[ctOS] Intersection is not complete! Please check all configuration.")
                        .color(NamedTextColor.RED));
                return;
            }

            // Save and register intersection
            try {
                persistence.saveIntersection(intersection);
                intersectionManager.registerIntersection(intersection);
                plugin.getAnimator().registerIntersection(intersection);

                player.sendMessage(Component.text("[ctOS] Intersection '" + intersection.getName() + "' created successfully!")
                        .color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("[ctOS] ID: " + intersection.getId())
                        .color(NamedTextColor.GRAY));

                wandStateManager.removeSession(player);
                WandState.removeWandFromInventory(player);

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to save intersection", e);
                player.sendMessage(Component.text("[ctOS] Error saving intersection: " + e.getMessage())
                        .color(NamedTextColor.RED));
            }

        } else if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("[ctOS] Setup cancelled.")
                    .color(NamedTextColor.YELLOW));
            wandStateManager.removeSession(player);
            WandState.removeWandFromInventory(player);
        } else {
            player.sendMessage(Component.text("[ctOS] Type 'confirm' to save, or 'cancel' to discard.")
                    .color(NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove setup session when player quits
        wandStateManager.removeSession(player);
    }
}
