package com.github.br.libgx.jam37.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.managers.TagManager;
import com.badlogic.gdx.Gdx;
import com.github.br.libgx.jam37.SpiderWeb;
import com.github.br.libgx.jam37.Tags;
import com.github.br.libgx.jam37.components.RenderComponent;

public class WindSystem extends BaseSystem {

    private SpiderWeb spiderWeb;
    private float time = 0;

    private ComponentMapper<RenderComponent> renderMapper;

    @Override
    protected void processSystem() {
        if (spiderWeb == null) {
            int webEntityId = world.getSystem(TagManager.class).getEntityId(Tags.WEB);
            RenderComponent renderComponent = renderMapper.get(webEntityId);
            spiderWeb = (SpiderWeb) renderComponent.renderer;
        }

        // Симуляция ветра (применяем силы до шага физики)
        float windForce = (float) Math.sin(time * 2.5f) * 1.4f; // float windForce = (float) Math.sin(time * 4.5f) * 3.4f;
        spiderWeb.applyWind(windForce, 0);

        time += Gdx.graphics.getDeltaTime();
    }

}
