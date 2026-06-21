package com.github.br.libgx.jam37.components.player;

import com.artemis.Component;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;

public class PlayerComponent extends Component {

    public int entityId;
    public Body currentSegmentBody; // По какому куску паутины ползет голова
    public Body[] bodySegments;     // Добавляем массив для хранения всех сегментов гусеницы

    // Хранилище живых контактов от слушателя
    public final Array<Body> activeContacts = new Array<>();

}
