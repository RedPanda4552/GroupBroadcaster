package io.github.redpanda4552.GroupBroadcaster;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;

public class EasyMode {
    
    private final GroupBroadcaster groupBroadcaster = GroupBroadcaster.pluginClass;

    private final LinkedHashSet<Text> messageSet;
    
    /**
     * Thread Safety - This is used in an async runnable. DO NOT ACCESS THIS OUTSIDE OF THAT RUNNABLE!
     */
    private Iterator<Text> messageIterator;
    
    private long frequencyTicks;
    
    public EasyMode(String frequency, LinkedHashSet<String> messages) {
        if (frequency.contains("s")) {
            try {
                frequencyTicks = Long.parseLong(frequency.split("s")[0]) * 20;
            } catch (NumberFormatException e) {
                frequencyTicks = 6000;
                this.groupBroadcaster.log.warn("Bad frequency for Easy Mode; it must be a whole number. Defaulting to 5 minutes.");
            }
        } else if (frequency.contains("m")) {
            try {
                frequencyTicks = Long.parseLong(frequency.split("m")[0]) * 20 * 60;
            } catch (NumberFormatException e) {
                frequencyTicks = 6000;
                this.groupBroadcaster.log.warn("Bad frequency for Easy Mode; it must be a whole number. Defaulting to 5 minutes.");
            }
        } else if (frequency.contains("h")) {
            try {
                frequencyTicks = Long.parseLong(frequency.split("h")[0]) * 20 * 60 * 60;
            } catch (NumberFormatException e) {
                frequencyTicks = 6000;
                this.groupBroadcaster.log.warn("Bad frequency for Easy Mode; it must be a whole number. Defaulting to 5 minutes.");
            }
        } else {
            frequencyTicks = 6000;
            this.groupBroadcaster.log.warn("Bad frequency for group Easy Mode; a unit of time, s for seconds, m for minutes, or h for hours, must be specified. Defaulting to 5 minutes.");
        }
        
        messageSet = groupBroadcaster.deserialize(messages);
    }
    
    /**
     * Create a repeating runnable to dispatch the messages to all players.
     */
    public void startBroadcastCycle() {
        groupBroadcaster.getTaskBuilder().execute(new Runnable() {

            @Override
            public void run() {
                groupBroadcaster.log.info("run(): Dispatching a wave of messages for easy mode");
                
                final Collection<Player> onlinePlayers = Sponge.getServer().getOnlinePlayers();
                
                for (Player player : onlinePlayers) {
                    groupBroadcaster.log.info("run(): Evaluating player " + player);
                    
                    if (player != null) {
                        groupBroadcaster.log.info("run(): Exists");
                        if (!messageIterator.hasNext()) {
                            groupBroadcaster.log.info("run(): Resetting the iterator");
                            messageIterator = messageSet.iterator();
                        }
                        
                        groupBroadcaster.log.info("run(): Sending the message");
                        player.sendMessage(messageIterator.next());
                    }
                }
            }
            
        }).async().name("easymode-broadcast-cycle").intervalTicks(frequencyTicks).submit(groupBroadcaster);
    }
}
