package org.cloudburstmc.server.event.entity;

import org.cloudburstmc.api.entity.Entity;
import org.cloudburstmc.api.event.Cancellable;
import org.cloudburstmc.api.event.entity.EntityEvent;

public class EntityVehicleExitEvent extends EntityEvent implements Cancellable {

    private final Entity vehicle;

    public EntityVehicleExitEvent(Entity entity, Entity vehicle) {
        this.entity = entity;
        this.vehicle = vehicle;
    }

    public Entity getVehicle() {
        return vehicle;
    }

}
