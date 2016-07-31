package io.github.redpanda4552.GroupBroadcaster;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent;

/**
 * Handles {@link Player Player} joining and leaving and adds/removes them from their {@link Group Group}s respectively.
 * This class is only registered when the plugin is running in group mode, and not easy mode.
 */
public class PlayerJoinLeaveListener {
    
    private final GroupBroadcaster groupBroadcaster = GroupBroadcaster.pluginClass;

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        Player player = event.getTargetEntity();
        
        for (Group group : groupBroadcaster.getGroupMap().values()) {
            if (player.hasPermission("GroupBroadcaster.memberOf." + group.getGroupId())) {
                group.addMember(player.getUniqueId());
                return;
            }
        }
    }
    
    @Listener
    public void onPlayerLeave(ClientConnectionEvent.Disconnect event) {
        Player player = event.getTargetEntity();
        
        for (Group group : groupBroadcaster.getGroupMap().values()) {
            if (group.isInGroup(player.getUniqueId())) {
                group.removeMember(player.getUniqueId());
                return;
            }
        }
    }
}
