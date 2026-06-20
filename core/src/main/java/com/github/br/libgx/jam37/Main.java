package com.github.br.libgx.jam37;

import com.artemis.managers.TagManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.player.CaterpillarRenderer;
import com.github.br.libgx.jam37.components.player.PlayerComponent;
import com.github.br.libgx.jam37.systems.PlayerInputSystem;
import com.github.br.libgx.jam37.systems.WindSystem;
import com.github.br.libgx.jam37.systems.physics.PhysicsSystem;
import com.github.br.libgx.jam37.systems.physics.WebContactListener;
import com.github.br.libgx.jam37.systems.render.RenderSystem;

import static com.github.br.libgx.jam37.Constants.WORLD_WIDTH;

public class Main implements ApplicationListener {

    private OrthographicCamera camera;
    private Viewport viewport;

    private com.artemis.World artemisWorld;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        float aspectRatio = (float) Constants.VIRTUAL_HEIGHT / (float) Constants.VIRTUAL_WIDTH;
        float worldHeight = Constants.WORLD_WIDTH * aspectRatio;
        viewport = new FitViewport(Constants.WORLD_WIDTH, worldHeight, camera);
        camera.position.set(Constants.WORLD_WIDTH / 2f, worldHeight / 2f, 0);
        camera.update();

        com.artemis.WorldConfiguration config = new com.artemis.WorldConfigurationBuilder()
            .with(new TagManager())
            .with(new PlayerInputSystem())
            .with(new WindSystem())
            .with(new PhysicsSystem())
            .with(new WebContactListener())
            .with(new RenderSystem(
                viewport,
                camera,
                Constants.VIRTUAL_WIDTH,
                Constants.VIRTUAL_HEIGHT,
                Constants.WORLD_WIDTH,
                worldHeight
            ))
            .build();
        artemisWorld = new com.artemis.World(config);

        SpiderWeb spiderWeb = createSpiderWeb(worldHeight);
        createPlayer(4, spiderWeb);
    }

    private SpiderWeb createSpiderWeb(float worldHeight) {
        World box2dWorld = artemisWorld.getSystem(PhysicsSystem.class).getBox2dWorld();

        Vector2 webCenter = new Vector2(WORLD_WIDTH / 2f, worldHeight / 2f);
        SpiderWeb spiderWeb = new SpiderWeb(box2dWorld, webCenter, 8f, 12, 8, 3);

        int webEntityId = artemisWorld.create();
        RenderComponent webRender = artemisWorld.getMapper(RenderComponent.class).create(webEntityId);
        webRender.layer = 0;            // Паутина строго под жуком
        webRender.renderer = spiderWeb; // Сам объект паутины выступает в роли отрисовщика

        artemisWorld.getSystem(TagManager.class).register(Tags.WEB, webEntityId);

        return spiderWeb;
    }

    private void createPlayer(int segmentCount, SpiderWeb spiderWeb) {
        Body[] segments = new Body[segmentCount];

        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();
        Vector2 startPos = startSegmentBody.getPosition();

        int entityId = artemisWorld.create();

        World box2dWorld = artemisWorld.getSystem(PhysicsSystem.class).getBox2dWorld();
        // 1. СОЗДАЕМ ТЕЛА И ПРИВЯЗЫВАЕМ USER DATA (Ваш код)
        for (int i = 0; i < segmentCount; i++) {
            BodyDef pDef = new BodyDef();
            pDef.type = BodyDef.BodyType.DynamicBody;
            pDef.position.set(startPos.x - (i * 0.4f), startPos.y);
            pDef.linearDamping = 1.0f;

            Body segBody = box2dWorld.createBody(pDef);
            segBody.setUserData(entityId);

            CircleShape playerShape = new CircleShape();
            playerShape.setRadius(0.20f - (i * 0.03f));

            FixtureDef pFixture = new FixtureDef();
            pFixture.shape = playerShape;
            pFixture.density = 0.1f;
            pFixture.filter.groupIndex = -1;
            pFixture.isSensor = true;

            segBody.createFixture(pFixture);
            playerShape.dispose();

            segments[i] = segBody;
        }

        // 2. СВЯЗЫВАЕМ СЕГМЕНТЫ МЯГКИМИ ПРУЖИНАМИ
        for (int i = 0; i < segmentCount - 1; i++) {
            Body bodyA = segments[i];
            Body bodyB = segments[i + 1];

            DistanceJointDef distanceJointDef = new DistanceJointDef();
            // Связываем строго центры кружков
            distanceJointDef.initialize(bodyA, bodyB, bodyA.getPosition(), bodyB.getPosition());
            distanceJointDef.collideConnected = false;

            // Настройка мягкости связи внутри гусеницы
            distanceJointDef.frequencyHz = 8.0f;  // Достаточно жестко, чтобы хвост не отставал
            distanceJointDef.dampingRatio = 0.7f; // Гасим лишнюю болтанку хвоста

            box2dWorld.createJoint(distanceJointDef);
        }

        // 3. ЗАПОЛНЯЕМ СТАНДАРТНЫЕ КОМПОНЕНТЫ
        PlayerComponent playerComp = artemisWorld.getMapper(PlayerComponent.class).create(entityId);
        PhysicsComponent physicsComp = artemisWorld.getMapper(PhysicsComponent.class).create(entityId);

        physicsComp.body = segments[0]; // Оставляем голову для обратной совместимости логики
        playerComp.bodySegments = segments;
        playerComp.currentSegmentBody = startSegmentBody;
        playerComp.entityId = entityId;

        // ====================================================
        // ШАГ 4 (NEW): ПОДКЛЮЧАЕМ СЛОЙ ОТРИСОВКИ И ИНТЕРФЕЙС ИГРОКА
        // ====================================================
        RenderComponent playerRenderComp = artemisWorld.getMapper(RenderComponent.class).create(entityId);
        playerRenderComp.layer = 1; // Рисуем ПОВЕРХ паутины (слой 1)
        playerRenderComp.renderer = new CaterpillarRenderer(segments); // Передаем кастомный отрисовщик

        // ====================================================
        // 5. ИНТЕНТЫ ПРИВЯЗКИ (ИСПРАВЛЕНО: Массивы для всей гусеницы)
        // ====================================================
        PrismaticRebindIntent startIntent = artemisWorld.getMapper(PrismaticRebindIntent.class).create(entityId);
        startIntent.init(segmentCount);

        // Инициализируем массивы под segmentCount сегментов
        startIntent.bodiesToBind = segments;
        startIntent.targetSegmentBodies = new Body[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            startIntent.targetSegmentBodies[i] = startSegmentBody;
        }

        // Рассчитываем геометрию для стартовой нити паутины
        float targetAngle = startSegmentBody.getAngle();
        Vector2 initialAxis = new Vector2((float) Math.cos(targetAngle), (float) Math.sin(targetAngle)).nor();

        float halfLength = 1.0f; // Дефолтное значение
        if (startSegmentBody.getFixtureList().size > 0 && startSegmentBody.getFixtureList().first().getShape() instanceof PolygonShape) {
            PolygonShape poly = (PolygonShape) startSegmentBody.getFixtureList().first().getShape();
            Vector2 vertex = new Vector2();
            poly.getVertex(0, vertex);
            halfLength = Math.abs(vertex.x);
        }

        // Заполняем параметры геометрии для каждого из 3-х суставов
        for (int i = 0; i < segmentCount - 1; i++) {
            startIntent.calculatedWorldAxes[i].set(initialAxis);
            startIntent.calculatedHalfLengths[i] = halfLength;
        }

        artemisWorld.getSystem(TagManager.class).register(Tags.PLAYER, entityId);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        artemisWorld.setDelta(delta);
        artemisWorld.process();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        artemisWorld.dispose();
    }

}

