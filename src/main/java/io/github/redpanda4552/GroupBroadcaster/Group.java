package io.github.redpanda4552.GroupBroadcaster;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.UUID;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

public class Group {
    
    private final GroupBroadcaster groupBroadcaster = GroupBroadcaster.pluginInstance;

    /**
     * {@link Group Group} members who are currently on the server.
     */
    private ArrayList<UUID> onlineMembers;
    
    /**
     * Set of messages that this {@link Group Group} gets.
     */
    private final LinkedHashSet<Text> messageSet;
    
    /**
     * Thread Safety - This is used in an async runnable. DO NOT ACCESS THIS OUTSIDE OF THAT RUNNABLE!
     */
    private Iterator<Text> messageIterator;
    
    private final String groupId;
    private final String superGroup;
    private final String messageOrdering;
    private final String permissionNode;
    private long frequencyTicks;
    
    /**
     * Create an empty {@link Group Group} that will take in members who have the specified permission.
     * @param groupId - The name of the {@link Group Group}. Used to define permissions and as an identifier.
     * @param messageOrdering - The style that messages should be ordered in when a superGroup is present.
     * @param superGroup - The {@link Group Group} that this {@link Group Group} should inherit messages from. Can be null, meaning none.
     * @param frequency - The frequency of messages. Expressed in the format [number][unit]. Examples: 15s = 15 seconds, 1h = 1 hour, 2m = 2 minutes.
     * @param messages - The messages this {@link Group Group} is assigned.
     */
    public Group(String groupId, String superGroup, String messageOrdering, String frequency, LinkedHashSet<String> messages) {
        this.groupId = groupId;
        this.superGroup = superGroup;
        this.messageOrdering = messageOrdering;
        this.permissionNode = "GroupBroadcaster.memberOf." + groupId;
        
        if (frequency.contains("s")) {
            try {
                frequencyTicks = Long.parseLong(frequency.split("s")[0]) * 20;
            } catch (NumberFormatException e) {
                frequencyTicks = 6000;
                this.groupBroadcaster.log.warn("Bad frequency for group '" + groupId + "'; it must be a whole number. Defaulting to 5 minutes.");
            }
        } else if (frequency.contains("m")) {
            try {
                frequencyTicks = Long.parseLong(frequency.split("m")[0]) * 20 * 60;
            } catch (NumberFormatException e) {
                frequencyTicks = 6000;
                this.groupBroadcaster.log.warn("Bad frequency for group '" + groupId + "'; it must be a whole number. Defaulting to 5 minutes.");
            }
        } else if (frequency.contains("h")) {
            try {
                frequencyTicks = Long.parseLong(frequency.split("h")[0]) * 20 * 60 * 60;
            } catch (NumberFormatException e) {
                frequencyTicks = 6000;
                this.groupBroadcaster.log.warn("Bad frequency for group '" + groupId + "'; it must be a whole number. Defaulting to 5 minutes.");
            }
        } else {
            frequencyTicks = 6000;
            this.groupBroadcaster.log.warn("Bad frequency for group '" + groupId + "'; a unit of time, s for seconds, m for minutes, or h for hours, must be specified. Defaulting to 5 minutes.");
        }
        
        LinkedHashSet<Text> messageSetBuilder = new LinkedHashSet<Text>();
        
        if (this.superGroup != null) {
            switch (this.messageOrdering) {
            case "mesh":
                messageSetBuilder = groupBroadcaster.meshMessages(messages, groupBroadcaster.getGroupMessages(superGroup));
                break;
            case "append":
                messageSetBuilder = groupBroadcaster.appendMessages(messages, groupBroadcaster.getGroupMessages(superGroup));
                break;
            default:
                this.groupBroadcaster.log.warn("Bad message order for group '" + groupId + "'; valid options are 'mesh' or 'append'. Defaulting to mesh.");
                messageSetBuilder = groupBroadcaster.meshMessages(messages, groupBroadcaster.getGroupMessages(superGroup));
                break;
            }
        } else {
            for (String str : messages) {
                messageSetBuilder.add(TextSerializers.FORMATTING_CODE.deserialize(str));
            }
        }
        
        messageSet = messageSetBuilder;
        onlineMembers = new ArrayList<UUID>();
        messageIterator = messageSet.iterator();
        startBroadcastCycle();
        groupBroadcaster.log.info("Broadcast Cycle started for group " + this.groupId);
    }
    
    public String getPermissionNode() {
        return permissionNode;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public boolean isInGroup(UUID playerId) {
        return onlineMembers.contains(playerId);
    }
    
    public boolean addMember(UUID playerId) {
        return onlineMembers.add(playerId);
    }
    
    /**
     * Removes a member from this {@link Group Group} in memory.
     */
    public boolean removeMember(UUID playerId) {
        return onlineMembers.remove(playerId);
    }
    
    /**
     * Create a repeating runnable to dispatch the messages to this group.
     */
    public void startBroadcastCycle() {
        groupBroadcaster.getTaskBuilder().execute(new Runnable() {

            @Override
            public void run() {
                groupBroadcaster.log.info("Dispatching a wave of messages for group " + groupId);
                Player player = null;
                
                for (UUID playerId : onlineMembers) {
                    player = groupBroadcaster.getPlayer(playerId);
                    groupBroadcaster.log.info("Evaluating player " + player);
                    
                    if (player != null) {
                        groupBroadcaster.log.info("Exists");
                        if (!messageIterator.hasNext()) {
                            groupBroadcaster.log.info("Resetting the iterator");
                            messageIterator = messageSet.iterator();
                        }
                        
                        groupBroadcaster.log.info("Sending the message");
                        player.sendMessage(messageIterator.next());
                    } else {
                        groupBroadcaster.log.info("Doesn't exist, removing");
                        removeMember(playerId);
                    }
                }
            }
            
        }).async().name(groupId + "-broadcast-cycle").intervalTicks(frequencyTicks).submit(groupBroadcaster); // TODO Is this supposed to be the main class?
    }
}
