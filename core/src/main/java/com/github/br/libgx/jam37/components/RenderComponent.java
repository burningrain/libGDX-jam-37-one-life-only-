package com.github.br.libgx.jam37.components;

import com.artemis.Component;
import com.github.br.libgx.jam37.systems.render.Renderable;

public class RenderComponent extends Component {

    public int layer;           // Порядок: 0 — паутина (снизу), 1 — жук (сверху)
    public Renderable renderer; // Ссылка на то, что умеет себя рисовать

}
