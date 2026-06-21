package com.github.br.libgx.jam37;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.managers.TagManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.enemy.*;
import com.github.br.libgx.jam37.components.player.CaterpillarRenderer;
import com.github.br.libgx.jam37.components.player.PlayerComponent;
import com.github.br.libgx.jam37.systems.physics.CollisionFilters;
import com.github.br.libgx.jam37.systems.physics.PhysicsSystem;
import com.github.br.libgx.jam37.systems.physics.data.*;

import static com.github.br.libgx.jam37.Constants.WORLD_WIDTH;

public class EntityFactory extends BaseSystem {

    private int gameParamsEntityId;

    protected ComponentMapper<GameParamsComponent> gameParamsMapper;

    @Override
    protected void processSystem() {
    }

    @Override
    protected void initialize() {
        setEnabled(false);
    }

    public GameParamsComponent getGameParamsComponent() {
        return gameParamsMapper.get(gameParamsEntityId);
    }

    public GameParamsComponent createGameParams() {
        com.artemis.World artemisWorld = getWorld();
        int entityId = artemisWorld.create();
        gameParamsEntityId = entityId;

        GameParamsComponent gameParamsComponent = artemisWorld.getMapper(GameParamsComponent.class).create(entityId);
        artemisWorld.getSystem(TagManager.class).register(Tags.GAME_PARAMS, entityId);
        return gameParamsComponent;
    }

    public void createFly(Body webSegment) {
        com.artemis.World artemisWorld = getWorld();
        int entityId = artemisWorld.create();

        FlyComponent flyComp = artemisWorld.getMapper(FlyComponent.class).create(entityId);
        flyComp.attachedWebSegment = webSegment;

        // Изначально ставим позицию в центр нити
        flyComp.pulseTimer = (float) Math.random() * 10f; // Рандомизируем фазу мерцания

        // физический компонент
        PhysicsComponent physicsComp = artemisWorld.getMapper(PhysicsComponent.class).create(entityId);
        World box2dWorld = artemisWorld.getSystem(PhysicsSystem.class).getBox2dWorld();
        // Берём текущую мировую позицию нити паутины
        Vector2 spawnPos = webSegment.getPosition();
        flyComp.attachedWebSegment = webSegment;
        flyComp.pulseTimer = (float) Math.random() * 10f;

        // 2. СОЗДАЕМ ФИЗИЧЕСКОЕ ТЕЛО МУХИ В BOX2D
        BodyDef bDef = new BodyDef();
        bDef.type = BodyDef.BodyType.DynamicBody;
        bDef.position.set(spawnPos); // Спавним точно в мировой точке нити
        bDef.linearDamping = 2.0f;
        Body flyBody = box2dWorld.createBody(bDef);

        // Намертво привязываем ID сущности для систем коллизий
        flyBody.setUserData(new FlyData(entityId, 1));

        CircleShape shape = new CircleShape();
        shape.setRadius(0.12f);

        FixtureDef fDef = new FixtureDef();
        fDef.filter.categoryBits = CollisionFilters.CATEGORY_COLLECTABLES;
        fDef.filter.maskBits = (short) (CollisionFilters.CATEGORY_PLAYER);
        fDef.shape = shape;
        fDef.density = 0.01f;
        fDef.isSensor = true;   // Датчик-сенсор
        flyBody.createFixture(fDef);
        shape.dispose();
        flyBody.resetMassData();
        flyComp.flyBody = flyBody;

        // 3. ПРИВЯЗЫВАЕМ МУХУ К НИТИ ПАУТИНЫ ПРИЗМАТИЧЕСКИМ ДЖОИНТОМ
        float webAngle = webSegment.getAngle();
        Vector2 axis = new Vector2((float) Math.cos(webAngle), (float) Math.sin(webAngle)).nor();

        // Извлекаем полудлину нити паутины для лимитов рельс
        float halfLength = 0.5f; // Дефолт
        if (webSegment.getFixtureList().size > 0 && webSegment.getFixtureList().first().getShape() instanceof PolygonShape) {
            PolygonShape poly = (PolygonShape) webSegment.getFixtureList().first().getShape();
            Vector2 vertex = new Vector2();
            poly.getVertex(0, vertex);
            halfLength = Math.abs(vertex.x);
        }

        // Строим сустав (настройки один в один как у гусеницы)
        com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef jointDef = new com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef();
        jointDef.initialize(webSegment, flyBody, spawnPos, axis);
        jointDef.enableMotor = false; // Мотор мухе не нужен, она сидит на месте
        jointDef.collideConnected = false;

        // Запираем муху строго в границах этой палочки паутины, чтобы она не уползала
        jointDef.enableLimit = true;
        jointDef.lowerTranslation = -halfLength;
        jointDef.upperTranslation = halfLength;

        // Сохраняем сустав в массив размера 1 внутри PhysicsComponent
        physicsComp.body = flyBody;
        physicsComp.crawlJoints = new PrismaticJoint[1];
        physicsComp.crawlJoints[0] = (PrismaticJoint) box2dWorld.createJoint(jointDef);


        // Подключаем рендеринг (Слой 1 — на уровне жука)
        RenderComponent renderComp = artemisWorld.getMapper(RenderComponent.class).create(entityId);
        renderComp.layer = 1;
        renderComp.renderer = new FlyRenderer(flyComp);
    }

    public SpiderWeb createWeb(float worldHeight) {
        com.artemis.World artemisWorld = getWorld();
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

    public void createPlayer(int segmentCount, Vector2 startPos, SpiderWeb spiderWeb) {
        com.artemis.World artemisWorld = getWorld();

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
            if (i == 0 || i == 1) {
                segBody.setUserData(new PlayerHeadData(entityId));
            } else {
                segBody.setUserData(new PlayerData(entityId));
            }


            CircleShape playerShape = new CircleShape();
            playerShape.setRadius(0.20f - (i * 0.03f));

            FixtureDef pFixture = new FixtureDef();
            pFixture.filter.categoryBits = CollisionFilters.CATEGORY_PLAYER; // Я — игрок
            pFixture.filter.maskBits = (short) (
                CollisionFilters.CATEGORY_SPIDER_PIECE |
                CollisionFilters.CATEGORY_COLLECTABLES |
                    CollisionFilters.CATEGORY_WEB
            );
            // Я сталкиваюсь с телом паука, его челюстями и миром

            pFixture.shape = playerShape;
            pFixture.density = 0.1f;
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

    public void createSpider(Vector2 spawnPos) {
        com.artemis.World artemisWorld = getWorld();

        int entityId = artemisWorld.create();

        SpiderComponent spiderComp = artemisWorld.getMapper(SpiderComponent.class).create(entityId);
        PhysicsComponent physicsComp = artemisWorld.getMapper(PhysicsComponent.class).create(entityId);
        World box2dWorld = artemisWorld.getSystem(PhysicsSystem.class).getBox2dWorld();

        SpiderData spiderData = new SpiderData(entityId);
        // 1. СОЗДАЕМ ГОЛОВОГРУДЬ (Центральный узел)
        BodyDef prosomaDef = new BodyDef();
        prosomaDef.type = BodyDef.BodyType.DynamicBody;
        prosomaDef.position.set(spawnPos);
        prosomaDef.linearDamping = 15.0f;
        prosomaDef.angularDamping = 3.0f;
        spiderComp.prosoma = box2dWorld.createBody(prosomaDef);
        spiderComp.prosoma.setUserData(spiderData);

        CircleShape prosomaShape = new CircleShape();
        prosomaShape.setRadius(0.35f); // Головогрудь чуть меньше брюшка
        FixtureDef prosomaFix = new FixtureDef();
        prosomaFix.shape = prosomaShape;
        prosomaFix.density = 0.1f;
        prosomaFix.filter.categoryBits = CollisionFilters.CATEGORY_SPIDER_BODY;
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
        spiderComp.opisthosoma.setUserData(spiderData);

        CircleShape opisthoShape = new CircleShape();
        opisthoShape.setRadius(0.55f); // Брюшко крупное, каплевидное
        FixtureDef opisthoFix = new FixtureDef();
        opisthoFix.filter.categoryBits = CollisionFilters.CATEGORY_SPIDER_BODY;
        opisthoFix.shape = opisthoShape;
        opisthoFix.density = 0.1f; // Брюшко тяжелое, дает инерцию
        spiderComp.opisthosoma.createFixture(opisthoFix);
        opisthoShape.dispose();

        // Соединяем Головогрудь и Брюшко (Стебельчатый сустав)
        // ====================================================
        // ИСПРАВЛЕНО: СВЯЗЫВАЕМ ГОЛОВОГРУДЬ И БРЮШКО СВАРНЫМ СУСТАВОМ (WeldJoint)
        // ====================================================
        WeldJointDef waistJoint = new WeldJointDef();
        Vector2 waistAnchor = new Vector2(spawnPos.x - 0.35f, spawnPos.y);
        waistJoint.initialize(spiderComp.prosoma, spiderComp.opisthosoma, waistAnchor);
        waistJoint.collideConnected = false;

        // Настраиваем мягкость «сварки»:
        // Сустав будет жестко удерживать брюшко соосно голове, но позволит ему
        // упруго вилять на резких поворотах, амортизируя заносы, как живой хитин!
        waistJoint.frequencyHz = 5.0f;   // Чем выше, тем монолитнее тело паука
        waistJoint.dampingRatio = 0.7f;  // Быстро возвращает брюшко в ровное положение

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
            chelBody.setUserData(new SpiderCheliceraeData(entityId));

            PolygonShape chelShape = new PolygonShape();
            // Маленькие вытянутые клинья-клешни
            chelShape.setAsBox(0.15f, 0.08f, new Vector2(0, 0), 0);
            FixtureDef chelFix = new FixtureDef();
            chelFix.filter.categoryBits = CollisionFilters.CATEGORY_SPIDER_PIECE; // Я кусок паука
            chelFix.filter.maskBits = CollisionFilters.CATEGORY_PLAYER;
            chelFix.shape = chelShape;
            chelFix.density = 0.5f;
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
            spiderComp.currentFemurAngles[i] = isLeft ? -fAng : fAng;
            spiderComp.currentTibiaAngles[i] = isLeft ? -tAng : tAng;

            // Намертво записываем координаты старта в массивы (лапы не будут лететь из нуля!)
            spiderComp.targetFootPos[i].set(footX, footY);
            spiderComp.currentFootPos[i].set(footX, footY);
        }

        // Подключаем рендеринг (Слой 2 — без изменений)
        RenderComponent renderComp = artemisWorld.getMapper(RenderComponent.class).create(entityId);
        renderComp.layer = 2;
        renderComp.renderer = new SpiderRenderer(spiderComp);

        artemisWorld.getSystem(TagManager.class).register(Tags.SPIDER, entityId);
    }

}
