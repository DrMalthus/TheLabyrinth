package com.hycredible.labyrinth;

import org.joml.Vector3i;

/**
 * Holds the last spawned labyrinth's grid origin and dimensions.
 *
 * Grid origin is the NW corner of section [0, 0] (i.e. player position
 * after applying the north/west offset from LabyrinthSpawnCommand).
 */
public class LabyrinthState {

    private static int originX;
    private static int originY;
    private static int originZ;
    private static int sectionsX;
    private static int sectionsZ;
    private static boolean set;

    public static void set(int originX, int originY, int originZ, int sectionsX, int sectionsZ) {
        LabyrinthState.originX = originX;
        LabyrinthState.originY = originY;
        LabyrinthState.originZ = originZ;
        LabyrinthState.sectionsX = sectionsX;
        LabyrinthState.sectionsZ = sectionsZ;
        LabyrinthState.set = true;
    }

    public static boolean isSet() {
        return set;
    }

    public static int getOriginX() { return originX; }

    public static int getOriginY() { return originY; }

    public static int getOriginZ() { return originZ; }

    public static int getSectionsX() { return sectionsX; }

    public static int getSectionsZ() { return sectionsZ; }
}