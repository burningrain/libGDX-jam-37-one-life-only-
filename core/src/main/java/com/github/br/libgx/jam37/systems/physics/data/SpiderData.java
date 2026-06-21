package com.github.br.libgx.jam37.systems.physics.data;

public class SpiderData implements ContactData {

    public int entityId;

    public SpiderData(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public int getEntityId() {
        return entityId;
    }

}
