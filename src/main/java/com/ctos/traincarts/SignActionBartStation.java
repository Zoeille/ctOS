package com.ctos.traincarts;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.ctos.traincarts.model.BartStationConfig;
import com.ctos.traincarts.service.BartRedstoneController;
import com.ctos.traincarts.service.BartStationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * TrainCarts sign action for SF BART station functionality.
 * Second line of the sign: "sf-bart-station"
 */
public class SignActionBartStation extends SignAction {

    private final BartRedstoneController redstoneController;
    private final BartStationManager stationManager;

    public SignActionBartStation(BartRedstoneController redstoneController, BartStationManager stationManager) {
        this.redstoneController = redstoneController;
        this.stationManager = stationManager;
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("sf-bart-station");
    }

    @Override
    public void execute(SignActionEvent info) {
        // Handle train sign (affects entire train)
        if (info.isTrainSign() && info.isPowered() && info.hasGroup()) {
            if (info.isAction(SignActionType.GROUP_ENTER)) {
                handleTrainEnter(info);
            } else if (info.isAction(SignActionType.GROUP_LEAVE)) {
                handleTrainLeave(info);
            } else if (info.isAction(SignActionType.REDSTONE_ON)) {
                handleRedstoneOn(info);
            }
            return;
        }

        // Handle cart sign (affects individual carts)
        if (info.isCartSign() && info.isPowered() && info.hasMember()) {
            if (info.isAction(SignActionType.MEMBER_ENTER)) {
                handleCartEnter(info);
            } else if (info.isAction(SignActionType.MEMBER_LEAVE)) {
                handleCartLeave(info);
            }
        }
    }

    /**
     * Called when a train enters the station
     */
    private void handleTrainEnter(SignActionEvent info) {
        String stationName = getStationName(info);
        // Log or handle train arrival
        info.getGroup().getProperties().setDestination("");

        // Trigger redstone controller with station name
        if (redstoneController != null && !stationName.isEmpty()) {
            redstoneController.onTrainEnter(info.getGroup(), stationName);
        }
    }

    /**
     * Called when a train leaves the station
     */
    private void handleTrainLeave(SignActionEvent info) {
        // Handle train departure - cleanup from redstone controller
        if (redstoneController != null && info.hasGroup()) {
            redstoneController.onTrainLeave(info.getGroup());
        }
    }

    /**
     * Called when redstone powers the sign
     */
    private void handleRedstoneOn(SignActionEvent info) {
        // Handle redstone activation - can be used to dispatch trains
        if (info.hasGroup()) {
            info.getGroup().getActions().clear();
        }
    }

    /**
     * Called when an individual cart enters the station
     */
    private void handleCartEnter(SignActionEvent info) {
        // Handle individual cart
    }

    /**
     * Called when an individual cart leaves the station
     */
    private void handleCartLeave(SignActionEvent info) {
        // Handle individual cart leaving
    }

    /**
     * Gets the station name from the third line of the sign.
     * Returns empty string if no station name is configured.
     */
    private String getStationName(SignActionEvent info) {
        return info.getLine(2).trim();
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        boolean success = SignBuildOptions.create()
                .setName("SF BART Station")
                .setDescription("defines a BART station stop for trains")
                .handle(event.getPlayer());

        if (success && event.getPlayer() != null) {
            String stationName = event.getLine(2).trim();

            if (stationName.isEmpty()) {
                event.getPlayer().sendMessage(Component.text("[ctOS BART] ")
                        .color(NamedTextColor.GOLD)
                        .append(Component.text("No station name on line 3")
                                .color(NamedTextColor.YELLOW)));
            } else if (stationManager != null) {
                BartStationConfig config = stationManager.getByStationName(stationName);
                if (config != null) {
                    event.getPlayer().sendMessage(Component.text("[ctOS BART] ")
                            .color(NamedTextColor.GOLD)
                            .append(Component.text("Station '")
                                    .color(NamedTextColor.GREEN))
                            .append(Component.text(stationName)
                                    .color(NamedTextColor.YELLOW))
                            .append(Component.text("' - Redstone configured!")
                                    .color(NamedTextColor.GREEN)));
                } else {
                    event.getPlayer().sendMessage(Component.text("[ctOS BART] ")
                            .color(NamedTextColor.GOLD)
                            .append(Component.text("Station '")
                                    .color(NamedTextColor.YELLOW))
                            .append(Component.text(stationName)
                                    .color(NamedTextColor.WHITE))
                            .append(Component.text("' - No redstone configured")
                                    .color(NamedTextColor.GRAY)));
                }
            }
        }

        return success;
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }

    @Override
    public boolean canSupportFakeSign(SignActionEvent info) {
        return true;
    }
}
