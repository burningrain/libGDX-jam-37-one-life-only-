package com.github.br.libgx.jam37.components;

import com.artemis.Component;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;

public class PhysicsComponent extends Component {

    public Body body;
    public PrismaticJoint[] crawlJoints;  // Мотор для головы (индекс 0) и моторы для звеньев хвоста (индексы 1 и 2)
    public float crawlSpeed = 4.0f;     // Скорость ползания жука (метров в секунду)

}
