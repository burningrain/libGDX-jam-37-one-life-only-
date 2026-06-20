package com.github.br.libgx.jam37;

import com.artemis.managers.TagManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.enemy.SpiderComponent;
import com.github.br.libgx.jam37.components.enemy.SpiderRenderer;
import com.github.br.libgx.jam37.components.player.CaterpillarRenderer;
import com.github.br.libgx.jam37.components.player.PlayerComponent;
import com.github.br.libgx.jam37.systems.PlayerInputSystem;
import com.github.br.libgx.jam37.systems.SpiderUpdateSystem;
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

        PhysicsSystem physicsSystem = new PhysicsSystem();

        com.artemis.WorldConfiguration config = new com.artemis.WorldConfigurationBuilder()
            .with(new TagManager())

            // ПЕРВЫЙ ЭТАП: Сбор ввода игрока и логика ИИ (Определяем намерения)
            .with(new PlayerInputSystem()) // Считывает кнопки, ставит скорость мотора головы
            .with(new SpiderUpdateSystem()) // Считает ИИ паука, ставит скорость prosoma и двигает лапки

            // ВТОРОЙ ЭТАП: Внешние физические силы (Прикладываем до шага Box2D)
            .with(new WindSystem()) // Колышет нити, вызывая applyForce

            // ТРЕТИЙ ЭТАП: Симуляция физического мира
            .with(physicsSystem) // Делает world.step()
            .with(new WebContactListener())

            // ЧЕТВЕРТЫЙ ЭТАП: Отрисовка (Забирает уже посчитанные на этом кадре координаты Box2D)
            .with(new RenderSystem(
                physicsSystem.getBox2dWorld(),
                viewport,
                camera,
                Constants.VIRTUAL_WIDTH,
                Constants.VIRTUAL_HEIGHT,
                Constants.WORLD_WIDTH,
                worldHeight
            ).setDebugBox2d(false))
            .build();
        artemisWorld = new com.artemis.World(config);

        SpiderWeb spiderWeb = createWeb(worldHeight);
        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();
        Vector2 startPos = startSegmentBody.getPosition();

        //createPlayer(5, startPos, spiderWeb);

        Body spawnSegmentBody = spiderWeb.getAllSegments().get(12);
        createSpider(spawnSegmentBody.getPosition());
    }

    private SpiderWeb createWeb(float worldHeight) {
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

    private void createPlayer(int segmentCount, Vector2 startPos, SpiderWeb spiderWeb) {
        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();

        Body[] segments = new Body[segmentCount];

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

    private void createSpider(Vector2 spawnPos) {
        int entityId = artemisWorld.create();

        SpiderComponent spiderComp = artemisWorld.getMapper(SpiderComponent.class).create(entityId);
        PhysicsComponent physicsComp = artemisWorld.getMapper(PhysicsComponent.class).create(entityId);
        World box2dWorld = artemisWorld.getSystem(PhysicsSystem.class).getBox2dWorld();

        // 1. СОЗДАЕМ ГОЛОВОГРУДЬ (Центральный узел)
        BodyDef prosomaDef = new BodyDef();
        prosomaDef.type = BodyDef.BodyType.DynamicBody;
        prosomaDef.position.set(spawnPos);
        prosomaDef.linearDamping = 15.0f;
        prosomaDef.angularDamping = 3.0f;
        spiderComp.prosoma = box2dWorld.createBody(prosomaDef);
        spiderComp.prosoma.setUserData(entityId);

        CircleShape prosomaShape = new CircleShape();
        prosomaShape.setRadius(0.35f); // Головогрудь чуть меньше брюшка
        FixtureDef prosomaFix = new FixtureDef();
        prosomaFix.shape = prosomaShape;
        prosomaFix.density = 0.1f;
        prosomaFix.filter.groupIndex = -1; // Не сталкивается со своими частями и игроком
        spiderComp.prosoma.createFixture(prosomaFix);
        prosomaShape.dispose();

        // Ведущим телом для базовых ECS систем оставляем головогрудь
        physicsComp.body = spiderComp.prosoma;

        // 2. СОЗДАЕМ БРЮШКО (Сзади головогруди)
        BodyDef opisthoDef = new BodyDef();
        opisthoDef.type = BodyDef.BodyType.DynamicBody;
        opisthoDef.position.set(spawnPos.x - 0.90f, spawnPos.y); // Было -0.7f
        opisthoDef.linearDamping = 4.0f;
        opisthoDef.angularDamping = 4.0f;
        spiderComp.opisthosoma = box2dWorld.createBody(opisthoDef);
        spiderComp.opisthosoma.setUserData(entityId);

        CircleShape opisthoShape = new CircleShape();
        opisthoShape.setRadius(0.55f); // Брюшко крупное, каплевидное
        FixtureDef opisthoFix = new FixtureDef();
        opisthoFix.shape = opisthoShape;
        opisthoFix.density = 0.1f; // Брюшко тяжелое, дает инерцию
        opisthoFix.filter.groupIndex = -1;
        spiderComp.opisthosoma.createFixture(opisthoFix);
        opisthoShape.dispose();

        // Соединяем Головогрудь и Брюшко (Стебельчатый сустав)
        RevoluteJointDef waistJoint = new RevoluteJointDef();
        Vector2 waistAnchor = new Vector2(spawnPos.x - 0.35f, spawnPos.y); // Теперь это идеальная точка касания!
        waistJoint.initialize(spiderComp.prosoma, spiderComp.opisthosoma, waistAnchor);
        waistJoint.enableLimit = true;
        waistJoint.lowerAngle = -0.3f;
        waistJoint.upperAngle = 0.3f;
        box2dWorld.createJoint(waistJoint);

        // 3. СОЗДАЕМ ХЕЛИЦЕРЫ (Две маленькие челюсти спереди)
        float[] sideMultipliers = {-1f, 1f};
        Body[] chelicerae = new Body[2];

        for (int i = 0; i < 2; i++) {
            BodyDef chelDef = new BodyDef();
            chelDef.type = BodyDef.BodyType.DynamicBody;
            chelDef.position.set(spawnPos.x + 0.4f, spawnPos.y + (0.15f * sideMultipliers[i]));
            chelDef.linearDamping = 1.0f;
            chelDef.angularDamping = 5.0f;

            Body chelBody = box2dWorld.createBody(chelDef);
            chelBody.setUserData(entityId);

            PolygonShape chelShape = new PolygonShape();
            // Маленькие вытянутые клинья-клешни
            chelShape.setAsBox(0.15f, 0.08f, new Vector2(0, 0), 0);
            FixtureDef chelFix = new FixtureDef();
            chelFix.shape = chelShape;
            chelFix.density = 0.5f;
            chelFix.filter.groupIndex = -1;
            chelBody.createFixture(chelFix);
            chelShape.dispose();
            chelicerae[i] = chelBody;

            // Пришиваем челюсть к передней части головы
            RevoluteJointDef chelJoint = new RevoluteJointDef();
            Vector2 chelAnchor = new Vector2(spawnPos.x + 0.35f, spawnPos.y + (0.15f * sideMultipliers[i]));
            chelJoint.initialize(spiderComp.prosoma, chelBody, chelAnchor);
            chelJoint.enableLimit = true;
            // Челюсти могут немного двигаться внутрь/наружу, создавая эффект жевания
            chelJoint.lowerAngle = -0.2f;
            chelJoint.upperAngle = 0.2f;
            box2dWorld.createJoint(chelJoint);
        }
        spiderComp.cheliceraLeft = chelicerae[0];
        spiderComp.cheliceraRight = chelicerae[1];

        // Устанавливаем челюсти в компонент
        spiderComp.cheliceraLeft = chelicerae[0];
        spiderComp.cheliceraRight = chelicerae[1];

        // =========================================================================
        // ИСПРАВЛЕНО: ЧИСТЫЙ СКЕЛЕТНЫЙ РАСЧЕТ ДЛЯ СТАРТА ИГРЫ (БЕЗ ВЫЗОВА СИСТЕМ)
        // =========================================================================
        float bodyAngleDeg = (float) Math.toDegrees(spiderComp.prosoma.getAngle());
        float prosomaRadius = 0.35f;

        for (int i = 0; i < 8; i++) {
            int configIdx = i % 4;
            boolean isLeft = (i < 4);

            // Читаем длины костей из конфига компонента
            float fLen = spiderComp.femurLengthConfig[configIdx];
            float tLen = spiderComp.tibiaLengthConfig[configIdx];
            float tarsLen = spiderComp.tarsusLengthConfig[configIdx];

            // Читаем углы члеников
            float fAng = spiderComp.femurAngleConfig[configIdx];
            float tAng = spiderComp.tibiaAngleConfig[configIdx];
            float tarsAng = spiderComp.tarsusAngleConfig[configIdx];

            // Зеркалируем левую сторону
            if (isLeft) {
                fAng = -fAng;
                tAng = -tAng;
                tarsAng = -tarsAng;
            }

            // Вычисляем абсолютные мировые углы суставов для стартовой позиции
            float worldFemurAngle = bodyAngleDeg + fAng;
            float worldTibiaAngle = worldFemurAngle + tAng;
            float worldTarsusAngle = worldTibiaAngle + tarsAng;

            // Переводим в радианы для тригонометрии Math.cos / Math.sin
            float fRad = (float) Math.toRadians(worldFemurAngle);
            float tRad = (float) Math.toRadians(worldTibiaAngle);
            float tarsRad = (float) Math.toRadians(worldTarsusAngle);

            // ИСПРАВЛЕНО: Честно считаем финальную точку ступни от края головогруди (prosomaRadius) по цепочке костей
            float footX = spawnPos.x + (float)Math.cos(fRad) * (prosomaRadius + fLen) + (float)Math.cos(tRad) * tLen + (float)Math.cos(tarsRad) * tarsLen;
            float footY = spawnPos.y + (float)Math.sin(fRad) * (prosomaRadius + fLen) + (float)Math.sin(tRad) * tLen + (float)Math.sin(tarsRad) * tarsLen;

            // Намертво записываем координаты старта в массивы (лапы не будут лететь из нуля!)
            spiderComp.targetFootPos[i].set(footX, footY);
            spiderComp.currentFootPos[i].set(footX, footY);
        }

        // Подключаем рендеринг (Слой 2 — без изменений)
        RenderComponent renderComp = artemisWorld.getMapper(RenderComponent.class).create(entityId);
        renderComp.layer = 2;
        renderComp.renderer = new SpiderRenderer(spiderComp);
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

