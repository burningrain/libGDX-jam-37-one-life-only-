package com.github.br.libgx.jam37.components;

import com.artemis.Component;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;

public class PlayerComponent extends Component {

    public int entityId;
    public Body currentSegmentBody;   // Физическое тело (сегмент), на котором жук сейчас сидит

    // Хранилище живых контактов от слушателя
    public final Array<Body> activeContacts = new Array<>();
}
