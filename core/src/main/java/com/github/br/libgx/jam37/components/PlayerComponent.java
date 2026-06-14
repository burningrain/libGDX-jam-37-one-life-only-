package com.github.br.libgx.jam37.components;

import com.artemis.Component;
import com.badlogic.gdx.physics.box2d.Body;

public class PlayerComponent extends Component {
    public Body currentSegmentBody; // Физическое тело (сегмент), на котором жук сейчас сидит
    public float crawlSpeed = 4.0f;  // Скорость ползания жука (метров в секунду)
}
