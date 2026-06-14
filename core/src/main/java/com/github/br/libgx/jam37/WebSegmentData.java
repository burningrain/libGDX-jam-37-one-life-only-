package com.github.br.libgx.jam37;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;

public class WebSegmentData {
    public boolean isRay;      // true — луч, false — кольцо
    public int rayIndex;       // Номер луча
    public int ringIndex;      // Номер кольца

    // Список физических тел, которые соединены с этим телом через шарниры
    public final Array<Body> connectedNeighbors = new Array<>();

    public WebSegmentData(boolean isRay, int rayIndex, int ringIndex) {
        this.isRay = isRay;
        this.rayIndex = rayIndex;
        this.ringIndex = ringIndex;
    }
}
