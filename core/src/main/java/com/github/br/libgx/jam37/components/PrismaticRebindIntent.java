package com.github.br.libgx.jam37.components;

import com.artemis.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

public class PrismaticRebindIntent extends Component {
    // Теперь здесь лежат массивы: для какого тела гусеницы какую нить паутины мы переназначаем
    public Body[] bodiesToBind;        // Длина (Голова, Тело, Хвост)
    public Body[] targetSegmentBodies; // Длина (Соответствующие нити паутины)

    public Vector2[] calculatedWorldAxes;
    public float[] calculatedHalfLengths;

    public void init(int segments) {
        this.calculatedWorldAxes = new Vector2[segments];
        for (int i = 0; i < segments; i++) {
            calculatedWorldAxes[i] = new Vector2();
        }
        this.calculatedHalfLengths = new float[segments];
    }
}
