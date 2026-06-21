package com.github.br.libgx.jam37.components.enemy;

import com.artemis.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

public class FlyComponent extends Component {

    public Body flyBody;

    public Body attachedWebSegment; // К какой именно нитке прилипла муха
    public float pulseTimer = 0f; // Для анимации мерцания светляка

}
