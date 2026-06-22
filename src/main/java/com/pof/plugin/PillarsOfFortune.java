package com.pof.plugin;

import com.pof.plugin.command.PofCommand;
import com.pof.plugin.game.ArenaManager;
import com.pof.plugin.game.ArenaService;
import com.pof.plugin.game.GameManager;
import com.pof.plugin.game.GameStateStore;
import com.pof.plugin.listener.GameListener;
import com.pof.plugin.listener.BlockListener;
import com.pof.plugin.listener.SignListener;
import com.pof.plugin.loot.LootManager;
import com.pof.plugin.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PillarsOfFortune extends JavaPlugin {

    private ArenaManager arenaManager;
    private LootManager lootManager;
    private GameStateStore gameStateStore;
    private ArenaService arenaService;
    private BlockListener blockListener;
    private MessageUtil messages;

    /** Players who died in-game and are awaiting respawn processing. */
    private final Set<UUID> pendingEliminations = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.arenaManager = new ArenaManager(getDataFolder(), getLogger());
        this.lootManager = new LootManager(this);
        this.gameStateStore = new GameStateStore(getDataFolder(), getLogger());
        this.messages = new MessageUtil(getConfig());
        this.arenaService = new ArenaService(this, arenaManager, lootManager, gameStateStore);

        getServer().getPluginManager().registerEvents(new SignListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        blockListener = new BlockListener(this);
        getServer().getPluginManager().registerEvents(blockListener, this);

        PofCommand pofCommand = new PofCommand(this);
        getCommand("pof").setExecutor(pofCommand);

        getLogger().info("Pillars of Fortune enabled with " + arenaManager.count() + " arena(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (arenaService != null) {
            for (GameManager gm : arenaService.getAllGameManagers()) {
                gm.forceStop(getServer().getConsoleSender());
            }
        }
        getLogger().info("Pillars of Fortune disabled.");
    }

    public void reloadMessages() {
        this.messages = new MessageUtil(getConfig());
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public BlockListener getBlockListener() { return blockListener; }

    public ArenaService getArenaService() {
        return arenaService;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public GameStateStore getGameStateStore() {
        return gameStateStore;
    }

    public MessageUtil getMessages() {
        return messages;
    }

    public Set<UUID> getPendingEliminations() {
        return pendingEliminations;
    }
}
