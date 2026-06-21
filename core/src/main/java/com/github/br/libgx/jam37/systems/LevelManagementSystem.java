package com.github.br.libgx.jam37.systems;

import com.artemis.BaseSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.github.br.libgx.jam37.EntityFactory;
import com.github.br.libgx.jam37.SpiderWeb;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;
import com.github.br.libgx.jam37.systems.physics.PhysicsSystem;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.github.br.libgx.jam37.systems.physics.data.ContactData;
import com.github.br.libgx.jam37.systems.physics.data.WebSegmentData;

public class LevelManagementSystem extends BaseSystem {

    private EntityFactory entityFactory;
    private PhysicsSystem physicsSystem;

    private GameParamsComponent gameParams;
    private float worldHeight;

    private boolean needRestart = false;
    // Флаг, сигнализирующий, что на этом кадре мир чист и пора спавнить объекты
    private boolean readyToSpawn = false;

    private SpiderWeb spiderWeb;

    public LevelManagementSystem(float worldHeight) {
        this.worldHeight = worldHeight;
    }

    @Override
    protected void initialize() {
        // НАСТРОЙКА ДЛЯ САМОГО ПЕРВОГО СТАРТА ПРИЛОЖЕНИЯ
        this.gameParams = entityFactory.createGameParams();
        gameParams.isStartScreen = true;
        gameParams.isCountingDown = false;
        gameParams.isGameOver = false;

        initFirstTime();
    }

    private void initFirstTime() {
        getWorld().getSystem(PlayerInputSystem.class).setEnabled(true);

        if (spiderWeb != null) {
            spiderWeb.dispose();
            spiderWeb = null;
        }
        spiderWeb = entityFactory.createWeb(worldHeight);
        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();
        Vector2 startPos = startSegmentBody.getPosition();

        entityFactory.createPlayer(5, startPos, spiderWeb);

        Body spawnSegmentBody = spiderWeb.getAllSegments().get(62);
        entityFactory.createSpider(spawnSegmentBody.getPosition());
    }

    @Override
    protected void processSystem() {
        // ФАЗА 2: Прошлый кадр полностью очистил Artemis и Box2D. Теперь безопасно спавним!
        if (readyToSpawn) {
            initFirstTime();
            readyToSpawn = false;
            return;
        }

        // ФАЗА 1: Игрок нажал пробел — запускаем точечную очистку
        if (needRestart) {
            executeActualClear();
            needRestart = false;
            readyToSpawn = true; // Разрешаем спавн на СЛЕДУЮЩЕМ кадре
            return;
        }

        if (gameParams == null) return;

        // ====================================================
        // 1. ЛОГИКА СТАРТОВОГО ЭКРАНА (Самый первый запуск игры)
        // ====================================================
        if (gameParams.isStartScreen) {
            // Замораживаем всё, пока игрок не нажмет Пробел на главном меню
            disableGameplaySystems();

            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                gameParams.isStartScreen = false;
                gameParams.isCountingDown = true; // Переходим к 3-2-1!
                gameParams.startTimer = 3.9f;
            }
            return; // Выходим, чтобы код ниже не мешал
        }

        // ====================================================
        // 2. ЛОГИКА ОБРАТНОГО ОТСЧЕТА (3-2-1)
        // ====================================================
        if (gameParams.isCountingDown) {
            gameParams.startTimer -= Gdx.graphics.getDeltaTime();

            if (gameParams.startTimer <= 0) {
                gameParams.isCountingDown = false;
                // Поехали! Включаем геймплей
                enableGameplaySystems();
            } else {
                disableGameplaySystems();
            }
            return;
        }

        // ====================================================
        // ШАГ: ПРОВЕРКА УСЛОВИЯ ПОБЕДЫ (Каждый кадр живой игры)
        // ====================================================
        if (!gameParams.isGameOver && !gameParams.isVictory && gameParams.currentPoints >= gameParams.victoryPoints) {
            gameParams.isVictory = true;
        }

        // ====================================================
        // ЛОГИКА ЭКРАНА ПОБЕДЫ
        // ====================================================
        if (gameParams.isVictory) {
            disableGameplaySystems(); // Замораживаем мир, празднуем победу!

            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                needRestart = true; // Перезапуск по пробелу сразу на отсчет 3-2-1
            }
            return; // Выходим из метода
        }

        // ====================================================
        // 3. ЛОГИКА СМЕРТИ И РЕСТАРТА (Обычный Game Over)
        // ====================================================
        if (gameParams.isGameOver) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                needRestart = true;
            }
        }
    }

    // Вынесем включение/выключение систем в удобные методы-помощники
    private void disableGameplaySystems() {
        getWorld().getSystem(PlayerInputSystem.class).setEnabled(false);
        getWorld().getSystem(SpiderUpdateSystem.class).setEnabled(false);
        //getWorld().getSystem(PhysicsSystem.class).setEnabled(false);
    }

    private void enableGameplaySystems() {
        getWorld().getSystem(PlayerInputSystem.class).setEnabled(true);
        getWorld().getSystem(SpiderUpdateSystem.class).setEnabled(true);
        //getWorld().getSystem(PhysicsSystem.class).setEnabled(true);
    }

    private void executeActualClear() {
        int gameParamsEntityId = -1;
        com.artemis.utils.IntBag paramsBag = getWorld().getAspectSubscriptionManager()
            .get(com.artemis.Aspect.all(GameParamsComponent.class))
            .getEntities();
        if (paramsBag.size() > 0) {
            gameParamsEntityId = paramsBag.get(0);
        }

//        if (spiderWeb != null) {
//            spiderWeb.dispose();
//            spiderWeb = null;
//        }

        com.badlogic.gdx.physics.box2d.World box2dWorld = physicsSystem.getBox2dWorld();

        // 1. БЕЗОПАСНЫЙ СБРОС КОНТАКТОВ
        com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Contact> contacts = box2dWorld.getContactList();

        for (com.badlogic.gdx.physics.box2d.Contact c : contacts) {
            if (c != null) c.setEnabled(false);
        }

        // 2. ОТКЛЮЧАЕМ ВСЕ СУСТАВЫ
        com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Joint> joints = new com.badlogic.gdx.utils.Array<>();
        box2dWorld.getJoints(joints);
        for (com.badlogic.gdx.physics.box2d.Joint joint : joints) {
            if (joint != null) {
                joint.setUserData(null);
                box2dWorld.destroyJoint(joint); // Суставы удалять нативно абсолютно безопасно
            }
        }

        // 3. СТИРАЕМ USER DATA И ФИЛЬТРЫ (Вместо destroyBody)
        // Чтобы нативный b2World не падал от двойного удаления, мы НЕ вызываем destroyBody руками.
        // Мы просто зануляем UserData и выключаем столкновения фикстур (уводим их в категорию 0).
        // Старые тела физически исчезнут из логики игры, а Artemis-ODB сотрет их в конце кадра.
        com.badlogic.gdx.utils.Array<Body> allBodies = new com.badlogic.gdx.utils.Array<>();
        box2dWorld.getBodies(allBodies);

        for (Body body : allBodies) {
            if (body == null) continue;

            Object userData = body.getUserData();
            if (userData instanceof com.github.br.libgx.jam37.systems.physics.data.ContactData) {
                if (userData instanceof WebSegmentData) {
                    continue;
                }
                int bodyEntityId = ((com.github.br.libgx.jam37.systems.physics.data.ContactData) userData).getEntityId();

                if (bodyEntityId != gameParamsEntityId) {
                    // Полностью изолируем тело от игрового мира
                    body.setUserData(null);
                    body.setActive(false); // Выключаем тело из симуляции физики! Оно замирает и не тратит ресурсы

                    // Начисто отключаем фикстуры от любых коллизий
                    for (Fixture fixture : body.getFixtureList()) {
                        com.badlogic.gdx.physics.box2d.Filter filter = fixture.getFilterData();
                        filter.categoryBits = 0; // Никто
                        filter.maskBits = 0;     // Ни с кем
                        fixture.setFilterData(filter);
                    }
                }
            }
        }

        // 4. ОЧИЩАЕМ СУЩНОСТИ В ARTEMIS-ODB
        IntBag allEntities = getWorld().getAspectSubscriptionManager()
            .get(com.artemis.Aspect.all())
            .getEntities();

        for (int i = allEntities.size() - 1; i >= 0; i--) {
            int entityId = allEntities.get(i);
            if (entityId != gameParamsEntityId) {
                getWorld().delete(entityId);
            }
        }

        // 5. СБРАСЫВАЕМ ПАРАМЕТРЫ МАТЧА
        if (gameParams != null) {
            gameParams.isStartScreen = false; // Пропускаем титульник
            gameParams.isVictory = false;
            gameParams.isGameOver = false;
            gameParams.isCountingDown = true; // Сразу на отсчет
            gameParams.startTimer = 3.9f;
            gameParams.currentPoints = 0;
            gameParams.currentFliesAmount = 0;
        }
    }


}
