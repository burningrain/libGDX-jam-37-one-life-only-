package com.github.br.libgx.jam37.systems.physics.data;

public class PlayerHeadData implements ContactData {

    public int entityId;

    public PlayerHeadData(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public int getEntityId() {
        return entityId;
    }
}
