package com.ctos.traincarts;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

/**
 * TrainCarts sign action for SF BART station functionality.
 * Second line of the sign: "sf-bart-station"
 */
public class SignActionBartStation extends SignAction {

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
    }

    /**
     * Called when a train leaves the station
     */
    private void handleTrainLeave(SignActionEvent info) {
        // Handle train departure
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
     * Gets the station name from the third line of the sign
     */
    private String getStationName(SignActionEvent info) {
        String line3 = info.getLine(2);
        return line3.isEmpty() ? "SF BART Station" : line3;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setName("SF BART Station")
                .setDescription("defines a BART station stop for trains")
                .handle(event.getPlayer());
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
