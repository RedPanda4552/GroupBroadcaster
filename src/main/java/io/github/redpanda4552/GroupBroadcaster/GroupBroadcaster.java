package io.github.redpanda4552.GroupBroadcaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import com.google.inject.Inject;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;

@Plugin(id = "groupbroadcaster", name = "Group Broadcaster", version = "0.0.1")
public class GroupBroadcaster {
    
    @Inject
    public Logger log;
    private Scheduler scheduler;
    private Task.Builder taskBuilder;
    
    /**
     * The config root directory.
     */
    @Inject
    @ConfigDir(sharedRoot = true)
    private Path configDir;
    
    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configLoader;
    
    private CommentedConfigurationNode rootNode;

    public Logger getLogger() {
        return log;
    }
    
    public Task.Builder getTaskBuilder() {
        return taskBuilder;
    }
    
    public static PluginContainer plugin;
    public static GroupBroadcaster pluginInstance;
    private HashMap<String, Group> groupList = null;
    private boolean easyMode;
    
    /**
     * Bringing back the Bukkit standard of either returning a {@link Player Player} or null.
     */
    public Player getPlayer(UUID playerId) {
        Optional<Player> op = Sponge.getServer().getPlayer(playerId);
        
        return op.orElse(null);
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        plugin = Sponge.getPluginManager().getPlugin("groupbroadcaster").get();
        pluginInstance = this;
        
        scheduler = Sponge.getScheduler();
        taskBuilder = scheduler.createTaskBuilder();
        
        Asset asset = plugin.getAsset("groupbroadcaster.conf").orElse(null);
        Path configPath = configDir.resolve("groupbroadcaster.conf");
        
        if (asset != null) {
            if (Files.notExists(configPath)) {
                try {
                    asset.copyToFile(configPath);
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Could not unpack the default config from the jar! Maybe your Minecraft server doesn't have write permissions?");
                    onServerStop(null);
                    return;
                }
            }
        } else {
            log.error("Could not find the default config file in the jar! Did you open the jar and delete it?");
            onServerStop(null);
            return;
        }
        
        try {
            rootNode = configLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("An IOException occured while trying to load the config; aborting plugin startup.");
            onServerStop(null);
            return;
        }
        
        
        switch (rootNode.getNode("config", "settings", "mode").getString()) {
        case "group":
            easyMode = false;
            break;
        case "easy":
            easyMode = true;
            break;
        default:
            easyMode = true;
        }
        
        if (easyMode) {
            
        } else {
            // TODO Explicitly look out for duplicate groups
            String groupId, messageOrdering, superGroup, frequency;
            Sponge.getEventManager().registerListeners(this, new PlayerJoinLeaveListener());
            groupList = new HashMap<String, Group>();
            
            log.info("");
            log.info("===================");
            log.info("== Group Loading ==");
            log.info("===================");
            for (ConfigurationNode group : rootNode.getNode("config", "groups").getChildrenMap().values()) {
                groupId = group.getKey().toString();
                log.info("Evaluating ConfigurationNode " + groupId);
                
                if (groupList.containsKey(groupId)) {
                    log.warn("Duplicate group '" + groupId + "' found! Ignoring the second occurence of it.");
                    continue;
                }
                
                messageOrdering = group.getNode("message-ordering") != null ? group.getNode("message-ordering").getString() : null;
                superGroup = group.getNode("super-group") != null ? group.getNode("super-group").getString() : null;
                frequency = group.getNode("frequency") != null ? group.getNode("frequency").getString() : null;
                
                LinkedHashSet<String> messages = new LinkedHashSet<String>();
                
                for (ConfigurationNode node : group.getNode("messages").getChildrenMap().values()) {
                    messages.add(node.getString());
                }
                
                groupList.put(groupId, new Group(groupId, superGroup, messageOrdering, frequency, messages));
            }
            
            log.info("========================");
            log.info("== Group Loading Done ==");
            log.info("========================");
            log.info("");
        }
    }
    
    @Listener
    public void onServerStop(GameStoppingServerEvent event) {
        if (event == null) {
            // Plugin is shutting itself down
        } else {
            // Server is stopping
        }
    }
    
    public HashMap<String, Group> getGroupList() {
        return groupList;
    }
    
    /**
     * Fetch messages assigned to a {@link Group Group}, and recurse through all super {@link Group Groups}, adding their messages as well.
     * @param groupId - The {@link Group Group} to fetch messages for.
     * @return An ordered set of the messages this {@link Group Group} should receive.
     */
    public LinkedHashSet<String> getGroupMessages(String groupId) {
        LinkedHashSet<String> messages = new LinkedHashSet<String>();
        ConfigurationNode groupsRoot = rootNode.getNode("config", "groups");
        ConfigurationNode group = groupsRoot.getNode(groupId);
        
        if (group == null) {
            log.warn("A group has a super-group set to " + groupId + ", but no such group exists!");
            return messages;
        }
        
        if (group.getNode("super-group") != null && group.getNode("super-group").getString() != null) {
            messages.addAll(getGroupMessages(group.getNode("super-group").getString()));
        }
        
        for (ConfigurationNode node : group.getNode("messages").getChildrenMap().values()) {
            messages.add(node.getNode(node.getString()).getString());
        }
        
        return messages;
    }
    
    protected LinkedHashSet<Text> meshMessages(LinkedHashSet<String> groupMessages, LinkedHashSet<String> superMessages) {
        int groupSize = groupMessages.size(), superSize = superMessages.size();
        int largerFreq = 0; // How many consecutive messages to pull from the larger group before pulling one from the smaller group.
        Iterator<String> groupIterator = groupMessages.iterator();
        Iterator<String> superIterator = superMessages.iterator();
        LinkedHashSet<Text> ret = new LinkedHashSet<Text>();
        
        if (groupSize > superSize) {
            largerFreq = Math.floorDiv(groupSize, superSize);
            
            while (ret.size() != groupSize + superSize) {
                int x = 0;
                
                if (groupIterator.hasNext() && x < largerFreq) {
                    ret.add(TextSerializers.FORMATTING_CODE.deserialize(groupIterator.next()));
                    x++;
                } else if (superIterator.hasNext()) {
                    ret.add(TextSerializers.FORMATTING_CODE.deserialize(superIterator.next()));
                    x = 0;
                } else {
                    // If the counter indicates a change to the super group,
                    // but there's nothing left in there, reset the counter
                    // in case the main group still has something.
                    x = 0;
                }
            }
            
        } else if (groupSize < superSize) {
            largerFreq = Math.floorDiv(superSize, groupSize);
            
            while (ret.size() != superSize + groupSize) {
                int x = 0;
                
                if (superIterator.hasNext() && x < largerFreq) {
                    ret.add(TextSerializers.FORMATTING_CODE.deserialize(superIterator.next()));
                    x++;
                } else if (groupIterator.hasNext()) {
                    ret.add(TextSerializers.FORMATTING_CODE.deserialize(groupIterator.next()));
                    x = 0;
                } else {
                    // If the counter indicates a change to the main group,
                    // but there's nothing left in there, reset the counter
                    // in case the super group still has something.
                    x = 0;
                }
            }
        } else {
            while (groupIterator.hasNext() && superIterator.hasNext()) {
                ret.add(TextSerializers.FORMATTING_CODE.deserialize(groupIterator.next()));
                ret.add(TextSerializers.FORMATTING_CODE.deserialize(superIterator.next()));
            }
        }
        
        return ret;
    }
    
    protected LinkedHashSet<Text> appendMessages(LinkedHashSet<String> groupMessages, LinkedHashSet<String> superMessages) {
        Iterator<String> groupIterator = groupMessages.iterator();
        Iterator<String> superIterator = superMessages.iterator();
        LinkedHashSet<Text> ret = new LinkedHashSet<Text>();
        
        while (groupIterator.hasNext()) {
            ret.add(TextSerializers.FORMATTING_CODE.deserialize(groupIterator.next()));
        }
        
        while (superIterator.hasNext()) {
            ret.add(TextSerializers.FORMATTING_CODE.deserialize(superIterator.next()));
        }
        
        return ret;
    }
}
