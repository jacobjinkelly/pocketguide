package com.example.cossettenavigation.map;

import com.example.cossettenavigation.Utilities;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A beacon placed in a key location and used to define zones.
 * @see Map
 */
public class AnchorBeacon extends Beacon implements Comparable, Serializable {

    private ArrayList<Zone> zones = new ArrayList<>();

    /**
     * Constructor using an absolute position.
     */
    public AnchorBeacon(String name,
                        String description,
                        Floor floor,
                        double xPosition,
                        double yPosition,
                        String uuid,
                        int major,
                        int minor) {

        super(name, description, floor, xPosition, yPosition, uuid, major, minor);
        floor.addAnchorBeacon(this);
    }

    /**
     * Constructor using a position relative to another beacon.
     */
    public AnchorBeacon(String name,
                        String description,
                        Floor floor,
                        Beacon referenceBeacon,
                        double xPositionOffset,
                        double yPositionOffset,
                        String uuid,
                        int major,
                        int minor) {

        super(name, description, floor, referenceBeacon, xPositionOffset, yPositionOffset, uuid, major, minor);
        floor.addAnchorBeacon(this);
    }


    @Override
    public String toString() {
        return String.format(
                "%s { name = \"%s\", floor = \"%s\", position = %s, uuid = %s, major = %d, minor = %d, zones = %s }",
                getClass().getSimpleName(),
                name, floor.getName(), position, uuid, major, minor,
                Utilities.getZoneNamesString(zones));
    }

    @Override
    public ArrayList<Zone> getZones() {
        return zones;
    }

    public void addZone(Zone zone) {
        if (!this.zones.contains(zone)) {
            this.zones.add(zone);
        }
    }

}
