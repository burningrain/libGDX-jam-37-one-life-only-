package com.github.br.libgx.jam37.systems.physics.data;

public class FlyData implements ContactData {

    public int entityId;

    public FlyData(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public int getEntityId() {
        return entityId;
    }
}
