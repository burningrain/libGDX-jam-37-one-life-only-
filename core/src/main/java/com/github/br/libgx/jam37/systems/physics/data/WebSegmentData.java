package com.github.br.libgx.jam37.systems.physics.data;

public class WebSegmentData implements ContactData {

    public boolean isFreeForFly = true;

    @Override
    public int getEntityId() {
        throw new UnsupportedOperationException("Получай паутину через тег менеджер");
    }

}
