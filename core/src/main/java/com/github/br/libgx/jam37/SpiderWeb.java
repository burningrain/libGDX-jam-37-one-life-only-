package com.github.br.libgx.jam37;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.utils.Array;
import com.github.br.libgx.jam37.systems.render.Renderable;

public class SpiderWeb implements Renderable {

    private final World world;
    private final Vector2 center;

    // Хранилище всех сегментов паутины (нитей) для рендеринга и логики
    private final Array<Body> allSegments = new Array<>();
    // Длины сегментов для быстрого рендеринга по локальным осям
    private final Array<Float> segmentLengths = new Array<>();

    // Отдельный список радиальных лучей, чтобы Main мог при старте найти за что зацепить жука
    private final Array<Body> radialStartSegments = new Array<>();
    // Список статичных тел-якорей по краям экрана и в центре, удерживающих всю структуру
    private final Array<Body> edgeAnchors = new Array<>();

    // Кэш векторов для отрисовки, полностью защищающий от спама в Garbage Collector (GC Free)
    private final Vector2 localLeft = new Vector2();
    private final Vector2 localRight = new Vector2();
    private final Vector2 worldLeft = new Vector2();
    private final Vector2 worldRight = new Vector2();

    /**
     * Конструктор генератора правильной пружинящей паутины
     */
    public SpiderWeb(World world, Vector2 center, float maxRadius, int raysCount, int ringsCount, int ringSubSegmentsCount) {
        this.world = world;
        this.center = center;

        generateWeb(maxRadius, raysCount, ringsCount, ringSubSegmentsCount);
    }

    private void generateWeb(float maxRadius, int raysCount, int ringsCount, int ringSubSegmentsCount) {
        float angleStep = (float) (Math.PI * 2 / raysCount);
        float radiusStep = maxRadius / ringsCount;

        // Временные векторы для расчетов, чтобы не спамить GC в цикле
        Vector2 posA = new Vector2();
        Vector2 posB = new Vector2();

        // Матрица для точечной прошивки суставов на стыках
        // Индексы: [индекс_луча][индекс_сегмента_от_центра]
        Body[][] rayMatrix = new Body[raysCount][ringsCount];

        // ШАГ А: Создаем скрытый монолитный центр паутины, чтобы он не шатался и не разваливался
        BodyDef centerDef = new BodyDef();
        centerDef.type = BodyDef.BodyType.DynamicBody; // ИЗМЕНЕНО: теперь центр может двигаться!
        centerDef.position.set(center);
        centerDef.linearDamping = 1.0f; // Гасим лишнюю болтанку центра

        Body centerAnchorBody = world.createBody(centerDef);

// Обязательно даем центру физическую форму и массу, чтобы суставы работали корректно
        CircleShape centerShape = new CircleShape();
        centerShape.setRadius(0.2f); // Маленькая точка-центр
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = centerShape;
        fixtureDef.density = 0.2f;   // Вес центральной точки паутины
        centerAnchorBody.createFixture(fixtureDef);
        centerShape.dispose();
        edgeAnchors.add(centerAnchorBody); // Очистим при dispose

        // ====================================================
        // ШАГ 1: ГЕНЕРИРУЕМ РАДИАЛЬНЫЕ ЛУЧИ (С подсегментами между кольцами)
        // ====================================================
        for (int i = 0; i < raysCount; i++) {
            float angle = i * angleStep;

            // Направление луча
            float dirX = (float) Math.cos(angle);
            float dirY = (float) Math.sin(angle);

            // Храним самый последний созданный сегмент на ЭТОМ луче для связывания в цепочку
            Body previousSegmentInRay = null;

            for (int j = 0; j < ringsCount; j++) {
                float rStart = j * radiusStep;
                float rEnd = (j + 1) * radiusStep;

                // Считаем длину шага для каждого подсегмента внутри текущего пролета
                float subSegmentLength = (rEnd - rStart) / ringSubSegmentsCount;

                for (int k = 0; k < ringSubSegmentsCount; k++) {
                    // Вычисляем точные координаты начала и конца текущего подсегмента
                    float currentRStart = rStart + k * subSegmentLength;
                    float currentREnd = rStart + (k + 1) * subSegmentLength;

                    posA.set(center.x + dirX * currentRStart, center.y + dirY * currentRStart);
                    posB.set(center.x + dirX * currentREnd, center.y + dirY * currentREnd);

                    Body segment = createSegment(posA, posB);
                    allSegments.add(segment);

                    // Если вам все еще нужна матрица для спиралей (ШАГ 2),
                    // сохраняем в нее КРАЙНИЙ сегмент у кольца (где k == ringSubSegmentsCount - 1)
                    if (k == ringSubSegmentsCount - 1) {
                        rayMatrix[i][j] = segment;
                    }

                    // КРЕПИМ НАЧАЛО САМОГО ПЕРВОГО ПОДСЕГМЕНТА К ЦЕНТРУ
                    if (j == 0 && k == 0) {
                        radialStartSegments.add(segment);

                        DistanceJointDef centerJoint = new DistanceJointDef();
                        centerJoint.initialize(centerAnchorBody, segment, center, center);
                        centerJoint.collideConnected = false;
                        centerJoint.frequencyHz = 25.0f;   // Удерживаем геометрию центра жестко
                        centerJoint.dampingRatio = 1.0f;   // Без дребезга и вибраций у основания
                        world.createJoint(centerJoint);
                    }

                    // СШИВАЕМ ПОДСЕГМЕНТЫ ПОСЛЕДОВАТЕЛЬНО (внутри кольца и между кольцами)
                    if (previousSegmentInRay != null) {
                        DistanceJointDef jointDef = new DistanceJointDef();
                        // Соединяем в точке posA (начало текущего подсегмента)
                        jointDef.initialize(previousSegmentInRay, segment, posA, posA);
                        jointDef.collideConnected = false;

                        // Параметры упругости нити
                        jointDef.frequencyHz = 4.5f;
                        jointDef.dampingRatio = 0.85f;
                        world.createJoint(jointDef);
                    }

                    // ФИКСАЦИЯ КОНЦА САМОГО ПОСЛЕДНЕГО ПОДСЕГМЕНТА К ОПОРЕ НА КРАЮ
                    if (j == ringsCount - 1 && k == ringSubSegmentsCount - 1) {
                        BodyDef anchorDef = new BodyDef();
                        anchorDef.type = BodyDef.BodyType.StaticBody;
                        anchorDef.position.set(posB);
                        Body anchor = world.createBody(anchorDef);
                        edgeAnchors.add(anchor);

                        DistanceJointDef edgeJoint = new DistanceJointDef();
                        edgeJoint.initialize(segment, anchor, posB, posB);
                        edgeJoint.collideConnected = false;

                        edgeJoint.frequencyHz = 20.0f;   // Внешний каркас держим прочно
                        edgeJoint.dampingRatio = 1.0f;    // Поглощаем импульсы на границах
                        world.createJoint(edgeJoint);
                    }

                    // Запоминаем текущий сегмент, чтобы следующий привязался к нему
                    previousSegmentInRay = segment;
                }
            }
        }

        // ====================================================
        // ШАГ 2: ГЕНЕРИРУЕМ КОНЦЕНТРИЧЕСКИЕ КОЛЬЦА (Упругая дуга)
        // ====================================================
        for (int j = 1; j <= ringsCount; j++) {
            float currentRadius = j * radiusStep;

            // ИСПРАВЛЕНО: Кольцо j должно крепиться к концу пролета j - 1,
            // который как раз завершается на радиусе currentRadius!
            int raySegmentIdx = j - 1;

            for (int i = 0; i < raysCount; i++) {
                float angleA = i * angleStep;
                float angleB = ((i + 1) % raysCount) * angleStep;

                // Проверяем, чтобы индекс не вышел за пределы матрицы
                if (raySegmentIdx >= ringsCount) continue;

                Body leftRay = rayMatrix[i][raySegmentIdx];
                Body rightRay = rayMatrix[(i + 1) % raysCount][raySegmentIdx];

                // Если вдруг луч на самом краю отсутствует, страхуемся
                if (leftRay == null || rightRay == null) continue;

                float totalAngleDelta = angleB - angleA;
                if (totalAngleDelta < 0) {
                    totalAngleDelta += (float) (Math.PI * 2);
                }
                float angleSubStep = totalAngleDelta / ringSubSegmentsCount;

                Body previousRingSegment = null;

                for (int k = 0; k < ringSubSegmentsCount; k++) {
                    float subAngleA = angleA + k * angleSubStep;
                    float subAngleB = angleA + (k + 1) * angleSubStep;

                    posA.set(center.x + (float) Math.cos(subAngleA) * currentRadius, center.y + (float) Math.sin(subAngleA) * currentRadius);
                    posB.set(center.x + (float) Math.cos(subAngleB) * currentRadius, center.y + (float) Math.sin(subAngleB) * currentRadius);

                    Body currentRingSegment = createSegment(posA, posB);
                    allSegments.add(currentRingSegment);

                    // ТОЧКА К1: Пришиваем ровно стык в стык к концу подсегмента луча
                    if (k == 0) {
                        DistanceJointDef jointLeft = new DistanceJointDef();
                        // Инициализируем в реальной точке posA на луче
                        jointLeft.initialize(leftRay, currentRingSegment, posA, posA);
                        jointLeft.collideConnected = false;
                        jointLeft.frequencyHz = 12.0f;  // Поднимаем жесткость стыков на краях
                        jointLeft.dampingRatio = 0.7f;
                        world.createJoint(jointLeft);
                    }

                    // ТОЧКА К2: Сшиваем внутренние микро-сегменты кольца между собой
                    if (previousRingSegment != null) {
                        DistanceJointDef jointInternal = new DistanceJointDef();
                        jointInternal.initialize(previousRingSegment, currentRingSegment, posA, posA);
                        jointInternal.collideConnected = false;
                        jointInternal.frequencyHz = 10.0f;
                        jointInternal.dampingRatio = 0.7f;
                        world.createJoint(jointInternal);
                    }

                    // ТОЧКА К3: Конец дуги пришиваем к правому лучу
                    if (k == ringSubSegmentsCount - 1) {
                        DistanceJointDef jointRight = new DistanceJointDef();
                        jointRight.initialize(currentRingSegment, rightRay, posB, posB);
                        jointRight.collideConnected = false;
                        jointRight.frequencyHz = 12.0f;
                        jointRight.dampingRatio = 0.7f;
                        world.createJoint(jointRight);
                    }

                    previousRingSegment = currentRingSegment;
                }
            }
        }
    }

    /**
     * Создает честное физическое тело для палочки-сегмента паутины с правильным углом
     */
    private Body createSegment(Vector2 pA, Vector2 pB) {
        BodyDef bDef = new BodyDef();
        bDef.type = BodyDef.BodyType.DynamicBody;

        // Высокое сопротивление среды заставляет нити извиваться плавно и благородно, как в воде
        bDef.linearDamping = 6.0f;
        bDef.angularDamping = 6.0f;

        // Позиция тела в Box2D — это всегда строго геометрический ЦЕНТР отрезка
        bDef.position.set((pA.x + pB.x) / 2f, (pA.y + pB.y) / 2f);

        // Локальная ось X тела теперь сонаправлена с нитью!
        bDef.angle = (float) Math.atan2(pB.y - pA.y, pB.x - pA.x);

        Body body = world.createBody(bDef);

        // Создаем форму прямоугольника
        float length = pA.dst(pB);
        segmentLengths.add(length); // Кешируем длину для быстрого рендера
        float thickness = 0.08f;    // Толщина нити в метрах Box2D

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(length / 2f, thickness / 2f);

        FixtureDef fDef = new FixtureDef();
        fDef.shape = shape;
        fDef.isSensor = true; // Сенсор собирает контакты лапок, не ломая физику движения
        fDef.density = 0.4f;  // Облегченные палочки позволяют суставам эффективнее убирать тряску

        body.createFixture(fDef);
        shape.dispose();

        // Вешаем маркер данных
        body.setUserData(new WebSegmentData());

        return body;
    }

    /**
     * Применение ослабленной силы ветра с затуханием к центру
     */
    public void applyWind(float forceX, float forceY) {
        // 1. Ослабляем входящую силу (например, в 4 раза), чтобы не было резких рывков
        float reducedX = forceX * 0.025f; // Было 0.25f
        float reducedY = forceY * 0.05f;

        for (Body segment : allSegments) {
            // 2. Считаем, как далеко этот сегмент находится от центра паутины
            float distanceToCenter = segment.getPosition().dst(center);

            // 3. Создаем множитель силы: у центра (distance = 0) он равен 0, на краях он выше.
            // Чем дальше нить от центра, тем сильнее её колышет ветер.
            float windMultiplier = distanceToCenter * 0.4f;

            // Прикладываем индивидуальную силу к каждому сегменту
            segment.applyForceToCenter(reducedX * windMultiplier, reducedY * windMultiplier, true);
        }
    }

    /*** Отрисовка паутины строго вдоль локальных осей деформированных тел без спама в GC*/
    @Override
    public void draw(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(Constants.WEB_COLOR);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < allSegments.size; i++) {
            Body segment = allSegments.get(i);
            float halfLength = segmentLengths.get(i) / 2f;
            // Локальные координаты краев ровной палочки вокруг ее центра (0,0)
            localLeft.set(-halfLength, 0);
            localRight.set(halfLength, 0);
            // Переводим локальные точки в мировые с учетом текущего смещения/поворота тела физикой
            worldLeft.set(segment.getWorldPoint(localLeft));
            worldRight.set(segment.getWorldPoint(localRight));
            // Рисуем чистую линию от края до края
            shapeRenderer.line(worldLeft.x, worldLeft.y, worldRight.x, worldRight.y);
        }
    }

    /*** Очистка физического мира при смене экрана джема*/
    public void dispose() {
        for (Body body : allSegments) {
            world.destroyBody(body);
        }
        for (Body anchor : edgeAnchors) {
            world.destroyBody(anchor);
        }
        allSegments.clear();
        segmentLengths.clear();
        radialStartSegments.clear();
        edgeAnchors.clear();
    }

    public Array<Body> getRadialStartSegments() {
        return radialStartSegments;
    }

    public Array<Body> getAllSegments() {
        return allSegments;
    }

}
