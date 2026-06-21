package com.github.br.libgx.jam37.systems;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.BaseEntitySystem;
import com.artemis.managers.TagManager;
import com.artemis.systems.IntervalSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.github.br.libgx.jam37.SpiderWeb;
import com.github.br.libgx.jam37.Tags;
import com.github.br.libgx.jam37.systems.physics.data.WebSegmentData;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.enemy.FlyComponent;
import com.github.br.libgx.jam37.EntityFactory;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;

public class FlySpawnerSystem extends IntervalSystem {

    private ComponentMapper<FlyComponent> mFly;
    private ComponentMapper<RenderComponent> mRender;

    protected EntityFactory entityFactory;
    private SpiderWeb spiderWeb;

    public FlySpawnerSystem() {
        super(Aspect.all(FlyComponent.class), 3);
    }

    @Override
    protected void processSystem() {
        // 1. Лениво забираем ссылку на объект паутины из ECS
        if (spiderWeb == null) {
            TagManager tagManager = world.getSystem(TagManager.class);
            if (tagManager.isRegistered(Tags.WEB)) {
                int webId = tagManager.getEntityId(Tags.WEB);
                RenderComponent webRender = mRender.get(webId);
                if (webRender != null) spiderWeb = (SpiderWeb) webRender.renderer;
            }
        }

        IntBag actives = getEntityIds();
        int currentFlyCount = actives.size();

        GameParamsComponent gameParamsComponent = entityFactory.getGameParamsComponent();
        if (currentFlyCount == gameParamsComponent.maxFliesOnWeb) {
            return;
        }

        // спавним новых светляков
        Array<Body> allSegments = spiderWeb.getAllSegments();
        // Выбираем случайный сегмент паутины для потенциального спавна
        int randomIndex = (int) (Math.random() * allSegments.size);


        Body randomSegmentBody = null;
        WebSegmentData webSegmentData = null;
        boolean isSegmentFree = false;
        int attempt = 0;
        while (attempt < allSegments.size && !isSegmentFree) {
            randomSegmentBody = allSegments.get(randomIndex);

            webSegmentData = (WebSegmentData) randomSegmentBody.getUserData();
            isSegmentFree = webSegmentData.isFreeForFly;
            attempt++;
        }

        if (isSegmentFree) {
            webSegmentData.isFreeForFly = false;
            entityFactory.createFlySegment(randomSegmentBody);
            gameParamsComponent.currentFliesAmount++;
        }

    }
}
