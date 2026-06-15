package com.github.br.libgx.jam37.components;

import com.artemis.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

public class PrismaticRebindIntent extends Component {
    public Body bodyToBind;         // Тело жука
    public Body targetSegmentBody;  // Тело сегмента-рельса

    // ✨ НОВЫЕ ПОЛЯ: Вся геометрия рассчитывается ДО физической системы
    public final Vector2 calculatedWorldAxis = new Vector2();
    public float calculatedHalfLength;
}
