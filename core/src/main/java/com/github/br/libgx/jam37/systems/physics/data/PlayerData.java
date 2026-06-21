package com.github.br.libgx.jam37.systems.physics.data;

public class PlayerData implements ContactData {

    public int entityId;
    public boolean isDeleted;

    public PlayerData(int entityId) {
        this.entityId = entityId;
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
