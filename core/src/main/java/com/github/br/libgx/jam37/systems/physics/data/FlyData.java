package com.github.br.libgx.jam37.systems.physics.data;

public class FlyData implements ContactData {

    public int entityId;
    public int points;
    public boolean isDeleted = false;

    public FlyData(int entityId, int points) {
        this.entityId = entityId;
        this.points = points;
    }

    @Override
    public int getEntityId() {
        return entityId;
    }

    @Override
    public boolean isDeleted() {
        return isDeleted;
    }

}
