package com.github.br.libgx.jam37;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.utils.Array;

public class SpiderWeb {

    private static final int RAY_SEGMENTS = 8;

    private final Array<SpiderWebRope> radialRopes = new Array<>();
    private final Array<SpiderWebRope> spiralRopes = new Array<>();
    private final Array<Body> edgeAnchors = new Array<>();
    private final Body webCenterBody;
    private final World world;

    public SpiderWeb(World world, Vector2 center, float radius, int numRays, int numRings) {
        this.world = world;

        // 1. Создаем один центральный узел паутины (плавающий)
        BodyDef centerDef = new BodyDef();
        centerDef.type = BodyDef.BodyType.DynamicBody;
        centerDef.position.set(center);
        centerDef.linearDamping = 8.0f;
        centerDef.angularDamping = 8.0f;
        this.webCenterBody = world.createBody(centerDef);

        CircleShape centerShape = new CircleShape();
        centerShape.setRadius(0.1f);
        FixtureDef centerFixture = new FixtureDef();
        centerFixture.shape = centerShape;
        centerFixture.density = 5.0f;
        webCenterBody.createFixture(centerFixture);
        centerShape.dispose();

        // 2. Генерируем все продольные нити (радиальные лучи)
        for (int i = 0; i < numRays; i++) {
            float angle = (i * 360f / numRays) * MathUtils.degreesToRadians;

            float endX = center.x + MathUtils.cos(angle) * radius;
            float endY = center.y + MathUtils.sin(angle) * radius;
            Vector2 endPoint = new Vector2(endX, endY);

            SpiderWebRope ray = new SpiderWebRope(world, center, endPoint, RAY_SEGMENTS, 0.04f);
            radialRopes.add(ray);

            // Прописываем WebSegmentData для каждого сегмента продольного луча
            for (int j = 0; j < ray.getSegments().size; j++) {
                Body b = ray.getSegments().get(j);
                b.setUserData(new WebSegmentData(true, i, j)); // true = луч, i = индекс луча, j = индекс кольца
            }

            // Шарнир начала луча к плавающему центру паутины
            RevoluteJointDef centerJoint = new RevoluteJointDef();
            centerJoint.initialize(webCenterBody, ray.getSegments().first(), center);
            centerJoint.collideConnected = false;
            world.createJoint(centerJoint);

            // Статичный якорь дерева на внешнем краю экрана
            BodyDef anchorDef = new BodyDef();
            anchorDef.type = BodyDef.BodyType.StaticBody;
            anchorDef.position.set(endPoint);
            Body edgeAnchor = world.createBody(anchorDef);
            edgeAnchors.add(edgeAnchor);

            // Шарнир конца луча к дереву
            RevoluteJointDef edgeJoint = new RevoluteJointDef();
            edgeJoint.initialize(edgeAnchor, ray.getSegments().peek(), endPoint);
            edgeJoint.collideConnected = false;
            world.createJoint(edgeJoint);
        }

        // --- ПРОШИВКА ГРАФА ДЛЯ ПРОДОЛЬНЫХ НИТЕЙ ---
        // Шаг А: Последовательно связываем сегменты внутри каждого луча от центра к краю
        for (SpiderWebRope ray : radialRopes) {
            for (int j = 0; j < ray.getSegments().size - 1; j++) {
                link(ray.getSegments().get(j), ray.getSegments().get(j + 1));
            }
        }

        // Шаг Б: Связываем противоположные лучи через центр для сквозного прохода
        for (int i = 0; i < numRays; i++) {
            Body rayStartA = radialRopes.get(i).getSegments().first();
            Body rayStartB = radialRopes.get((i + numRays / 2) % numRays).getSegments().first();
            link(rayStartA, rayStartB);
        }

        // 3. Генерируем все поперечные нити (соединяющие спиральные кольца)
        for (int ring = 1; ring <= numRings; ring++) {
            float ringRadius = radius * ((float) ring / numRings);
            int raySegmentIdx = MathUtils.clamp((int)((float)ring / numRings * RAY_SEGMENTS) - 1, 0, RAY_SEGMENTS - 1);

            for (int rayIdx = 0; rayIdx < numRays; rayIdx++) {
                float angle1 = (rayIdx * 360f / numRays) * MathUtils.degreesToRadians;
                float angle2 = (((rayIdx + 1) % numRays) * 360f / numRays) * MathUtils.degreesToRadians;

                Vector2 p1 = new Vector2(center.x + MathUtils.cos(angle1) * ringRadius, center.y + MathUtils.sin(angle1) * ringRadius);
                Vector2 p2 = new Vector2(center.x + MathUtils.cos(angle2) * ringRadius, center.y + MathUtils.sin(angle2) * ringRadius);

                // Создаем сектор кольца из 2 звеньев
                SpiderWebRope ringSegment = new SpiderWebRope(world, p1, p2, 2, 0.03f);
                spiralRopes.add(ringSegment);

                // ПРОПИСЫВАЕМ USERDATA ДЛЯ ВСЕХ КОЛЬЦЕВЫХ СЕГМЕНТОВ
                for (int j = 0; j < ringSegment.getSegments().size; j++) {
                    Body b = ringSegment.getSegments().get(j);
                    b.setUserData(new WebSegmentData(false, rayIdx, ring)); // false = кольцо, rayIdx = между какими лучами висит, ring = номер кольца
                }

                // Связывание физическим суставом левого края кольца с текущим лучом (в точке p1)
                SpiderWebRope currentRay = radialRopes.get(rayIdx);
                Body rayBody1 = currentRay.getSegments().get(raySegmentIdx);
                Body ringStartBody = ringSegment.getSegments().first();

                DistanceJointDef jointDef1 = new DistanceJointDef();
                jointDef1.initialize(rayBody1, ringStartBody, p1, p1);
                jointDef1.collideConnected = false;
                jointDef1.frequencyHz = 3.0f;
                jointDef1.dampingRatio = 0.7f;
                world.createJoint(jointDef1);

                // Связывание физическим суставом правого края кольца со следующим лучом (в точке p2)
                SpiderWebRope nextRay = radialRopes.get((rayIdx + 1) % numRays);
                Body rayBody2 = nextRay.getSegments().get(raySegmentIdx);
                Body ringEndBody = ringSegment.getSegments().peek();

                DistanceJointDef jointDef2 = new DistanceJointDef();
                jointDef2.initialize(rayBody2, ringEndBody, p2, p2);
                jointDef2.collideConnected = false;
                jointDef2.frequencyHz = 3.0f;
                jointDef2.dampingRatio = 0.7f;
                world.createJoint(jointDef2);

                // --- ПРОШИВКА ГРАФА ДЛЯ ПОПЕРЕЧНЫХ НИТЕЙ И ПЕРЕКРЕСТКОВ ---
                // Шаг В: Связываем левую развилку (палочку луча с началом кольца)
                link(rayBody1, ringStartBody);

                // Шаг Г: Связываем правую развилку (палочку соседнего луча с концом кольца)
                link(rayBody2, ringEndBody);

                // Шаг Д: Связываем два внутренних сегмента самого кольца между собой, чтобы жук полз по нему непрерывно
                if (ringSegment.getSegments().size > 1) {
                    link(ringSegment.getSegments().get(0), ringSegment.getSegments().get(1));
                }
            }
        }
    }

    /**
     * Вспомогательный метод для взаимной прошивки соседей в массивы connectedNeighbors.
     * Берет два тела, достает их WebSegmentData и безопасно добавляет ссылки друг на друга.
     */
    private void link(Body a, Body b) {
        if (a == null || b == null) return;

        WebSegmentData dataA = (WebSegmentData) a.getUserData();
        WebSegmentData dataB = (WebSegmentData) b.getUserData();

        if (dataA != null && dataB != null) {
            // Добавляем тело B в список соседей тела A, если его там еще нет
            if (!dataA.connectedNeighbors.contains(b, true)) {
                dataA.connectedNeighbors.add(b);
            }
            // Добавляем тело A в список соседей тела B
            if (!dataB.connectedNeighbors.contains(a, true)) {
                dataB.connectedNeighbors.add(a);
            }
        }
    }

    public void render(ShapeRenderer shapeRenderer) {
        // Просто просим каждую нить честно нарисовать свои физические звенья-коробки
        for (SpiderWebRope rope : radialRopes) {
            rope.render(shapeRenderer);
        }
        for (SpiderWebRope rope : spiralRopes) {
            rope.render(shapeRenderer);
        }

        // Центральный узел паутины (опционально, если хотите видеть саму серединку)
        Vector2 centerPos = webCenterBody.getPosition();
        shapeRenderer.circle(centerPos.x, centerPos.y, 0.1f, 8);
    }

    public void applyWind(float forceX, float forceY) {
        for (SpiderWebRope rope : radialRopes) {
            rope.applyWind(forceX, forceY);
        }
    }

    public Array<SpiderWebRope> getRadialRopes() { return radialRopes; }
    public Array<SpiderWebRope> getSpiralRopes() { return spiralRopes; }
}
