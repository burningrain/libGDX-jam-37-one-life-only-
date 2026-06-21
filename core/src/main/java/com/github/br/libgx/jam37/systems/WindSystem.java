package com.github.br.libgx.jam37.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.managers.TagManager;
import com.badlogic.gdx.Gdx;
import com.github.br.libgx.jam37.Constants;
import com.github.br.libgx.jam37.EntityFactory;
import com.github.br.libgx.jam37.SpiderWeb;
import com.github.br.libgx.jam37.Tags;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;

public class WindSystem extends BaseSystem {

    private float time = 0;

    private ComponentMapper<RenderComponent> renderMapper;

    @Override
    protected void processSystem() {
        int webEntityId = world.getSystem(TagManager.class).getEntityId(Tags.WEB);
        if (Constants.EMPTY_ID == webEntityId) {
            return;
        }

        RenderComponent renderComponent = renderMapper.get(webEntityId);
        SpiderWeb spiderWeb = (SpiderWeb) renderComponent.renderer;

        // Симуляция ветра (применяем силы до шага физики)
        GameParamsComponent gameParams = getWorld().getSystem(EntityFactory.class).getGameParamsComponent();
        int score = gameParams.currentPoints;

        // Базовая сила ветра (например, 5.0f) увеличивается на 10% за каждое очко
        float baseWindForce = 0.0f;
        float currentWindForce = baseWindForce + (score * 0.5f);

        // Накапливаем время для синусоиды покачивания ветра
        time += Gdx.graphics.getDeltaTime();

        // Пример расчета силы ветра с учетом синуса и прогрессии очков:
        float windX = (float) Math.sin(time * 2.0f) * currentWindForce;

        // Симуляция ветра (применяем силы до шага физики)
        spiderWeb.applyWind(windX, 0);


    }

}
