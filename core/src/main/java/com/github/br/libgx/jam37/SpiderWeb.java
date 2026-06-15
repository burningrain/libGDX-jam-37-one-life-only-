package com.github.br.libgx.jam37;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.utils.Array;

public class SpiderWeb {

    private final World world;
    private final Vector2 center;

    // Хранилище всех сегментов паутины (нитей) для рендеринга и логики
    private final Array<Body> allSegments = new Array<>();
    // Длины сегментов для быстрого рендеринга по локальным осям
    private final Array<Float> segmentLengths = new Array<>();

    // Отдельный список радиальных лучей, чтобы Main мог при старте найти за что зацепить жука
    private final Array<Body> radialStartSegments = new Array<>();
    // Список статичных тел-якорей по краям экрана, удерживающих всю структуру
    private final Array<Body> edgeAnchors = new Array<>();

    // Кэш векторов для отрисовки, полностью защищающий от спама в Garbage Collector
    private final Vector2 localLeft = new Vector2();
    private final Vector2 localRight = new Vector2();
    private final Vector2 worldLeft = new Vector2();
    private final Vector2 worldRight = new Vector2();

    /**
     * Конструктор генератора правильной пружинящей паутины
     */
    public SpiderWeb(World world, Vector2 center, float maxRadius, int raysCount, int ringsCount) {
        this.world = world;
        this.center = center;

        generateWeb(maxRadius, raysCount, ringsCount);
    }

    private void generateWeb(float maxRadius, int raysCount, int ringsCount) {
        float angleStep = (float) (Math.PI * 2 / raysCount);
        float radiusStep = maxRadius / ringsCount;

        // Временные векторы для расчетов, чтобы не спамить GC в цикле
        Vector2 posA = new Vector2();
        Vector2 posB = new Vector2();

        // Матрица для точечной прошивки суставов на стыках
        // Индексы: [индекс_луча][индекс_сегмента_от_центра]
        Body[][] rayMatrix = new Body[raysCount][ringsCount];

        // ====================================================
        // ШАГ 1: ГЕНЕРИРУЕМ РАДИАЛЬНЫЕ ЛУЧИ (Идут из центра наружу)
        // ====================================================
        for (int i = 0; i < raysCount; i++) {
            float angle = i * angleStep;

            // Направление луча
            float dirX = (float) Math.cos(angle);
            float dirY = (float) Math.sin(angle);

            for (int j = 0; j < ringsCount; j++) {
                float rStart = j * radiusStep;
                float rEnd = (j + 1) * radiusStep;

                posA.set(center.x + dirX * rStart, center.y + dirY * rStart);
                posB.set(center.x + dirX * rEnd, center.y + dirY * rEnd);

                Body segment = createSegment(posA, posB);
                allSegments.add(segment);
                rayMatrix[i][j] = segment;

                // Сохраняем самые первые внутренние сегменты лучей для инициализации жука
                if (j == 0) {
                    radialStartSegments.add(segment);
                }

                // ТОЧКА Б: Сшиваем сегменты одного луча последовательно «паровозиком» в точке стыка posA
                if (j > 0) {
                    DistanceJointDef jointDef = new DistanceJointDef();
                    jointDef.initialize(rayMatrix[i][j - 1], segment, posA, posA);
                    jointDef.collideConnected = false;

                    // Жесткая настройка для прочного каркаса
                    // Вместо stiffness и damping пишем:
                    jointDef.frequencyHz = 6.0f;    // Чем выше частота, тем жестче пружина
                    jointDef.dampingRatio = 0.5f;   // Значение 0.5f отлично гасит лишнюю тряску [1]

                    world.createJoint(jointDef);
                }

                // ТОЧКА А: Фиксация самого внешнего края паутины к статичному невидимому дереву-якорю
                if (j == ringsCount - 1) {
                    BodyDef anchorDef = new BodyDef();
                    anchorDef.type = BodyDef.BodyType.StaticBody;
                    anchorDef.position.set(posB);
                    Body anchor = world.createBody(anchorDef);
                    edgeAnchors.add(anchor);

                    DistanceJointDef edgeJoint = new DistanceJointDef();
                    edgeJoint.initialize(segment, anchor, posB, posB);
                    edgeJoint.collideConnected = false;

                    // Максимальное натяжение на краях, чтобы паутина не провисала под собственным весом
                    // Вместо stiffness и damping пишем:
                    edgeJoint.frequencyHz = 10.0f;  // Самая высокая частота для жесткого каркаса
                    edgeJoint.dampingRatio = 0.7f;  // Высокое гашение, чтобы края не вибрировали

                    world.createJoint(edgeJoint);
                }
            }
        }

        // ====================================================
        // ШАГ 2: ГЕНЕРИРУЕМ КОНЦЕНТРИЧЕСКИЕ КОЛЬЦА (Витковые нити)
        // ====================================================
        for (int j = 1; j <= ringsCount; j++) {
            float currentRadius = j * radiusStep;
            int raySegmentIdx = j - 1; // Берем соответствующий сегмент луча на этом уровне глубины

            for (int i = 0; i < raysCount; i++) {
                float angleA = i * angleStep;
                // Замыкаем кольцо: последний луч соединяем с самым первым (индекс 0)
                float angleB = ((i + 1) % raysCount) * angleStep;

                posA.set(center.x + (float) Math.cos(angleA) * currentRadius, center.y + (float) Math.sin(angleA) * currentRadius);
                posB.set(center.x + (float) Math.cos(angleB) * currentRadius, center.y + (float) Math.sin(angleB) * currentRadius);

                Body ringSegment = createSegment(posA, posB);
                allSegments.add(ringSegment);

                // Достаем тела левого и правого лучей, между которыми натягивается текущая дуга кольца
                Body leftRay = rayMatrix[i][raySegmentIdx];
                Body rightRay = rayMatrix[(i + 1) % raysCount][raySegmentIdx];

                // ТОЧКА В1: Подвешиваем левый край кольца к левому лучу (в точке posA)
                DistanceJointDef jointLeft = new DistanceJointDef();
                jointLeft.initialize(leftRay, ringSegment, posA, posA);
                jointLeft.collideConnected = false;

                // Делаем кольца эластичными: они мягче лучей, сочно тянутся и круто пружинят
                // Вместо stiffness и damping пишем:
                jointLeft.frequencyHz = 4.0f;   // Низкая частота сделает кольца мягкими и эластичными
                jointLeft.dampingRatio = 0.4f;  // Позволит нити сделать пару красивых пружинящих покачиваний

                world.createJoint(jointLeft);

                // ТОЧКА В2: Подвешиваем правый край кольца к правому лучу (в точке posB)
                DistanceJointDef jointRight = new DistanceJointDef();
                jointRight.initialize(rightRay, ringSegment, posB, posB);
                jointRight.collideConnected = false;

                // Вместо stiffness и damping пишем:
                jointLeft.frequencyHz = 4.0f;   // Низкая частота сделает кольца мягкими и эластичными
                jointLeft.dampingRatio = 0.4f;  // Позволит нити сделать пару красивых пружинящих покачиваний

                world.createJoint(jointRight);
            }
        }
    }

    /**
     * Создает честное физическое тело для палочки-сегмента паутины с правильным углом
     */
    private Body createSegment(Vector2 pA, Vector2 pB) {
        BodyDef bDef = new BodyDef();
        // Включаем честную динамику, чтобы палочки смещались под весом физических объектов
        bDef.type = BodyDef.BodyType.DynamicBody;

        // Повышенное сопротивление сред, чтобы паутина возвращалась на место, а не вела себя как желе
        bDef.linearDamping = 4.0f;
        bDef.angularDamping = 4.0f;

        // 1. Позиция тела в Box2D — это всегда строго геометрический ЦЕНТР отрезка
        bDef.position.set((pA.x + pB.x) / 2f, (pA.y + pB.y) / 2f);

        // 2. ✨ Локальная ось X тела теперь сонаправлена с нитью
        bDef.angle = (float) Math.atan2(pB.y - pA.y, pB.x - pA.x);

        Body body = world.createBody(bDef);

        // 3. Создаем форму прямоугольника.
        float length = pA.dst(pB);
        segmentLengths.add(length); // Кешируем длину нити для рендера
        float thickness = 0.08f;    // Толщина нити в метрах Box2D

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(length / 2f, thickness / 2f);

        FixtureDef fDef = new FixtureDef();
        fDef.shape = shape;
        fDef.isSensor = true; // Сенсор ловит контакты (для WebContactListener), но не отталкивает жука физически
        fDef.density = 0.5f;  // Облегчаем нити, чтобы пружины джоинтов отрабатывали эффективнее

        body.createFixture(fDef);
        shape.dispose();

        // 4. Обязательно вешаем маркер данных, чтобы WebContactListener понимал, что это нить паутины
        body.setUserData(new WebSegmentData());

        return body;
    }

    /**
     * Применение силы ветра. Физически толкает упругие динамические нити.
     */
    public void applyWind(float forceX, float forceY) {
        for (Body segment : allSegments) {
            // Так как тела динамические, этот метод заставит их реалистично отклоняться в сторону силы
            segment.applyForceToCenter(forceX, forceY, true);
        }
    }

    /**
     * Отрисовка паутины строго вдоль локальных осей деформированных тел без спама в GC
     */
    public void render(ShapeRenderer shapeRenderer) {
        for (int i = 0; i < allSegments.size; i++) {
            Body segment = allSegments.get(i);
            float halfLength = segmentLengths.get(i) / 2f;

            // Локальные координаты краев ровной палочки вокруг ее центра (0,0)
            localLeft.set(-halfLength, 0);
            localRight.set(halfLength, 0);

            // Переводим локальные точки в мировые с учетом текущего смещения/поворота тела физикой
            worldLeft.set(segment.getWorldPoint(localLeft));
            worldRight.set(segment.getWorldPoint(localRight));

            // Рисуем линию от края до края
            shapeRenderer.line(worldLeft.x, worldLeft.y, worldRight.x, worldRight.y);
        }
    }

    //* Очистка физического мира при перезапуске уровня / выходе в меню*/


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

