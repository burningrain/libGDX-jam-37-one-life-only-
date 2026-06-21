package com.github.br.libgx.jam37.systems;

import com.artemis.BaseSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.github.br.libgx.jam37.EntityFactory;
import com.github.br.libgx.jam37.SpiderWeb;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;
import com.github.br.libgx.jam37.systems.physics.PhysicsSystem;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;

public class LevelManagementSystem extends BaseSystem {

    private EntityFactory entityFactory;
    private PhysicsSystem physicsSystem;

    private GameParamsComponent gameParams;
    private float worldHeight;

    // Флаг-маркер: сигнализирует, что на СЛЕДУЮЩЕМ кадре нужно выполнить рестарт
    private boolean needRestart = false;

    public LevelManagementSystem(float worldHeight) {
        this.worldHeight = worldHeight;
    }

    @Override
    protected void initialize() {
        initFirstTime();
    }

    private void initFirstTime() {
        getWorld().getSystem(PlayerInputSystem.class).setEnabled(true);

        this.gameParams = entityFactory.createGameParams();
        SpiderWeb spiderWeb = entityFactory.createWeb(worldHeight);
        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();
        Vector2 startPos = startSegmentBody.getPosition();

        entityFactory.createPlayer(5, startPos, spiderWeb);

        Body spawnSegmentBody = spiderWeb.getAllSegments().get(12);
        entityFactory.createSpider(spawnSegmentBody.getPosition());
    }

    @Override
    protected void processSystem() {
        // 1. Отработка отложенного перезапуска (происходит в самом начале работы системы на новом кадре)
        if (needRestart) {
            executeActualRestart();
            needRestart = false;
            return; // Пропускаем остаток кадра для этой системы, чтобы мир стабилизировался
        }

        // 2. Проверяем условия конца игры и нажатия пробела
        if (gameParams != null && gameParams.isGameOver) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                // Вместо мгновенного тяжелого рестарта с world.process() просто взводим флаг
                needRestart = true;
            }
        }
    }

    private void executeActualRestart() {
        // 1. Сначала находим ID глобальных параметров игры, чтобы не стереть очки
        int gameParamsEntityId = -1;
        com.artemis.utils.IntBag paramsBag = getWorld().getAspectSubscriptionManager()
            .get(com.artemis.Aspect.all(GameParamsComponent.class))
            .getEntities();
        if (paramsBag.size() > 0) {
            gameParamsEntityId = paramsBag.get(0);
        }

        // 2. ЖЕЛЕЗОБЕТОННАЯ ОЧИСТКА BOX2D МИРА
        // Вместо отложенного слушателя, мы ПРИНУДИТЕЛЬНО уничтожаем физические тела старой паутины,
        // игрока и паука прямо сейчас, пока их компоненты PhysicsComponent еще живы в мапперах.
        com.artemis.utils.IntBag allEntities = getWorld().getAspectSubscriptionManager()
            .get(com.artemis.Aspect.all())
            .getEntities();

        com.artemis.ComponentMapper<PhysicsComponent> mPhysics = getWorld().getMapper(PhysicsComponent.class);

        for (int i = 0; i < allEntities.size(); i++) {
            int entityId = allEntities.get(i);

            if (entityId != gameParamsEntityId) {
                // Удаляем физическое тело Box2D РУКАМИ до создания новых объектов!
                if (mPhysics.has(entityId)) {
                    PhysicsComponent physics = mPhysics.get(entityId);
                    if (physics != null && physics.body != null) {
                        physics.body.setUserData(null);
                        physicsSystem.getBox2dWorld().destroyBody(physics.body);
                        physics.body = null;
                    }
                }
                // Помечаем сущность на удаление в Artemis
                getWorld().delete(entityId);
            }
        }

        // 3. Сбрасываем параметры матча
        if (gameParams != null) {
            gameParams.isGameOver = false;
            gameParams.currentPoints = 0;
            gameParams.currentFliesAmount = 0;
        }

        // 4. ТЕПЕРЬ физический мир девственно чист!
        // Спокойно создаем новую паутину, игрока и суставы — никакого наложения не будет.
        initFirstTime();
    }
}
