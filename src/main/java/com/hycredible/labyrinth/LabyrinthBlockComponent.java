package com.hycredible.labyrinth;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.Nullable;

public class LabyrinthBlockComponent implements Component<ChunkStore> {

    private static ComponentType<ChunkStore, LabyrinthBlockComponent> type;

    public static ComponentType<ChunkStore, LabyrinthBlockComponent> getComponentType() {
        return type;
    }

    public static void setComponentType(ComponentType<ChunkStore, LabyrinthBlockComponent> type) {
        LabyrinthBlockComponent.type = type;
    }

    public static final BuilderCodec<LabyrinthBlockComponent> CODEC = BuilderCodec.builder(LabyrinthBlockComponent.class, LabyrinthBlockComponent::new).build();

    public LabyrinthBlockComponent() {}

    public Component<ChunkStore> clone() {
        return new LabyrinthBlockComponent();
    }
}
