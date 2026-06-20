package com.hycredible.labyrinth;

import com.hycredible.labyrinth.Commands.LabyrinthMoveRowUndoCommand;
import com.hycredible.labyrinth.Commands.LabyrinthSpawnCommand;
import com.hycredible.labyrinth.Commands.LabyrinthMoveRowCommand;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class Labyrinth extends JavaPlugin {
    protected static Labyrinth instance;

    private ComponentType labyrinthBlockComponentType;

    public Labyrinth(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;

        // Commands
        this.getCommandRegistry().registerCommand(new LabyrinthSpawnCommand());
        this.getCommandRegistry().registerCommand(new LabyrinthMoveRowCommand());
        this.getCommandRegistry().registerCommand(new LabyrinthMoveRowUndoCommand());

        // Components
        this.labyrinthBlockComponentType =
            this.getChunkStoreRegistry().registerComponent(LabyrinthBlockComponent.class, "LabyrinthBlock", LabyrinthBlockComponent.CODEC);
        LabyrinthBlockComponent.setComponentType(this.labyrinthBlockComponentType);
    }

    @Override
    protected void start() {

        this.getChunkStoreRegistry().registerSystem(new LabyrinthBlockInitializer());

    }
}
