package com.github.br.libgx.jam37.systems;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.github.br.libgx.jam37.SpiderWeb;
import com.github.br.libgx.jam37.SpiderWebRope;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.PlayerComponent;

@All({PlayerComponent.class, PhysicsComponent.class})
public class PlayerInputSystem extends IteratingSystem {

    protected ComponentMapper<PlayerComponent> mPlayer;
    protected ComponentMapper<PhysicsComponent> mPhysics;

    private final SpiderWeb spiderWeb;
    private final Vector2 targetPosition = new Vector2();
    private final Vector2 velocity = new Vector2();

    private final Vector2 pA = new Vector2();
    private final Vector2 pB = new Vector2();
    private final Vector2 snapPoint = new Vector2();
    private final Vector2 inputDirection = new Vector2();
    private final Vector2 segmentDir = new Vector2();

    private static final int RAY_SEGMENTS = 8;

    public Vector2 getTargetPosition() {
        return targetPosition;
    }

    public PlayerInputSystem(com.badlogic.gdx.physics.box2d.World world, SpiderWeb spiderWeb) {
        this.spiderWeb = spiderWeb;
    }

    @Override
    protected void process(int entityId) {
        PlayerComponent player = mPlayer.get(entityId);
        Body playerBody = mPhysics.get(entityId).body;

        float delta = Gdx.graphics.getDeltaTime();

        // 1. СЧИТЫВАЕМ ЧИСТЫЙ ВЕКТОР НАПРАВЛЕНИЯ ВВОДА ПО ЭКРАНУ
        inputDirection.set(0, 0);
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) inputDirection.x = 1;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) inputDirection.x = -1;
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) inputDirection.y = 1;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) inputDirection.y = -1;

        // Свободное смещение маркера цели строго по кнопкам
        targetPosition.set(playerBody.getPosition());
        float step = player.crawlSpeed * delta;
        if (inputDirection.len2() > 0) {
            targetPosition.add(inputDirection.x * step, inputDirection.y * step);
        }

        // Переменные для поиска лучшего рельса
        float bestScore = -Float.MAX_VALUE;
        Body bestSegmentBody = null;
        Vector2 bestSnapPoint = new Vector2(targetPosition);

        // Получаем живые координаты центра паутины прямо в этот кадр
        Vector2 centerPos = spiderWeb.getRadialRopes().first().getSegments().first().getJointList().first().joint.getBodyA().getPosition();
        // Но еще надежнее — взять позицию центрального тела, если бы у нас была прямая ссылка.
        // В нашем SpiderWeb мы дорисовываем хвосты от webCenterBody. Позицию центра можно взять от первого сегмента любого луча:
        if (spiderWeb.getRadialRopes().size > 0) {
            // Центр паутины всегда привязан к RevoluteJoint первого сегмента луча
            centerPos = spiderWeb.getRadialRopes().first().getSegments().first().getJointList().first().joint.getAnchorA();
        }

        int numRays = spiderWeb.getRadialRopes().size;

        // ----------------------------------------------------
        // СКАНИРОВАНИЕ ПРОДОЛЬНЫХ ЛУЧЕЙ (Включая Хвосты Центра и Краев!)
        // ----------------------------------------------------
        for (int r = 0; r < numRays; r++) {
            SpiderWebRope ray = spiderWeb.getRadialRopes().get(r);
            if (ray.getSegments().size == 0) continue;

            // Линия 1: От плавающего центра паутины до середины самого первого сегмента луча
            pA.set(centerPos);
            pB.set(ray.getSegments().first().getPosition());
            checkSegment(ray.getSegments().first(), pA, pB, bestSnapPoint, bestScore, (body, snap, score) -> {
                // Внутренний лямбда-апдейтер для сокращения дублирования кода
                // Мы запишем результаты, если этот участок оказался лучшим
            });
            float scoreCenter = calculateSegmentScore(pA, pB, snapPoint, playerBody.getPosition());
            if (scoreCenter > bestScore) {
                bestScore = scoreCenter;
                bestSnapPoint.set(snapPoint);
                bestSegmentBody = ray.getSegments().first();
            }

            // Линии 2: Внутренние стыки между всеми сегментами луча
            for (int i = 0; i < ray.getSegments().size - 1; i++) {
                pA.set(ray.getSegments().get(i).getPosition());
                pB.set(ray.getSegments().get(i + 1).getPosition());

                Intersector.nearestSegmentPoint(pA, pB, targetPosition, snapPoint);
                float score = calculateSegmentScore(pA, pB, snapPoint, playerBody.getPosition());
                if (score > bestScore) {
                    bestScore = score;
                    bestSnapPoint.set(snapPoint);
                    bestSegmentBody = ray.getSegments().get(i);
                }
            }

            // Линия 3: От последнего сегмента луча до жесткого статичного якоря на дереве (КРАЙ ЭКРАНА)
            // Позицию якоря дерева берем из RevoluteJoint на конце луча
            Vector2 anchorPos = ray.getSegments().peek().getJointList().first().joint.getAnchorA();
            if (ray.getSegments().peek().getJointList().size > 1) {
                anchorPos = ray.getSegments().peek().getJointList().get(1).joint.getAnchorA();
            }
            pA.set(ray.getSegments().peek().getPosition());
            pB.set(anchorPos);
            float scoreEdge = calculateSegmentScore(pA, pB, snapPoint, playerBody.getPosition());
            if (scoreEdge > bestScore) {
                bestScore = scoreEdge;
                bestSnapPoint.set(snapPoint);
                bestSegmentBody = ray.getSegments().peek();
            }
        }

        // ----------------------------------------------------
        // СКАНИРОВАНИЕ ПОПЕРЕЧНЫХ КОЛЕЦ (СПЛОШНАЯ КРУГОВАЯ СЕТКА)
        // ----------------------------------------------------
        // Мы сканируем кольца не как обрывки массивов, а последовательно от луча к лучу по кругу!
        Array<SpiderWebRope> spiralRopes = spiderWeb.getSpiralRopes();
        int numRings = spiralRopes.size / numRays;

        int spiralIdx = 0;
        for (int ring = 1; ring <= numRings; ring++) {
            int raySegmentIdx = MathUtils.clamp((int) ((float) ring / numRings * RAY_SEGMENTS) - 1, 0, RAY_SEGMENTS - 1);

            for (int rayIdx = 0; rayIdx < numRays; rayIdx++) {
                SpiderWebRope ringSegment = spiderWeb.getSpiralRopes().get(spiralIdx++);
                if (ringSegment.getSegments().size == 0) continue;

                // Берем физические тела двух соседних лучей, между которыми натянуто это кольцо
                Body rayBody1 = spiderWeb.getRadialRopes().get(rayIdx).getSegments().get(raySegmentIdx);
                Body rayBody2 = spiderWeb.getRadialRopes().get((rayIdx + 1) % numRays).getSegments().get(raySegmentIdx);

                // Отрезок 1: От текущего луча до центра первой половины кольца
                pA.set(rayBody1.getPosition());
                pB.set(ringSegment.getSegments().first().getPosition());
                Intersector.nearestSegmentPoint(pA, pB, targetPosition, snapPoint);
                float s1 = calculateSegmentScore(pA, pB, snapPoint, playerBody.getPosition());
                if (s1 > bestScore) {
                    bestScore = s1;
                    bestSnapPoint.set(snapPoint);
                    bestSegmentBody = ringSegment.getSegments().first();
                }

                // Отрезок 2: Между двумя половинками перемычки кольца
                if (ringSegment.getSegments().size > 1) {
                    pA.set(ringSegment.getSegments().get(0).getPosition());
                    pB.set(ringSegment.getSegments().get(1).getPosition());
                    Intersector.nearestSegmentPoint(pA, pB, targetPosition, snapPoint);
                    float s2 = calculateSegmentScore(pA, pB, snapPoint, playerBody.getPosition());
                    if (s2 > bestScore) {
                        bestScore = s2;
                        bestSnapPoint.set(snapPoint);
                        bestSegmentBody = ringSegment.getSegments().get(0);
                    }
                }

                // Отрезок 3: От центра второй половины кольца до следующего луча (ЗАМЫКАЕМ ЦЕПЬ!)
                pA.set(ringSegment.getSegments().peek().getPosition());
                pB.set(rayBody2.getPosition());
                Intersector.nearestSegmentPoint(pA, pB, targetPosition, snapPoint);
                float s3 = calculateSegmentScore(pA, pB, snapPoint, playerBody.getPosition());
                if (s3 > bestScore) {
                    bestScore = s3;
                    bestSnapPoint.set(snapPoint);
                    bestSegmentBody = ringSegment.getSegments().peek();
                }
            }
        }

        // 3. МАГНИТНЫЙ ПОДТЯГ К ЛУЧШЕЙ ТОЧКЕ НА СЕТИ
        if (bestSegmentBody != null) {
            player.currentSegmentBody = bestSegmentBody;
            targetPosition.set(bestSnapPoint);
        }

        // Подтягиваем физическое тело жука через честную скорость
        velocity.set(targetPosition).sub(playerBody.getPosition());
        velocity.scl(60f);
        playerBody.setLinearVelocity(velocity);

        if (player.currentSegmentBody != null) {
            playerBody.setAngularVelocity((player.currentSegmentBody.getAngle() - playerBody.getAngle()) * 60f);
        }
    }

    private float calculateSegmentScore(Vector2 start, Vector2 end, Vector2 snap, Vector2 playerPos) {
        // ====================================================
        // ЖЕСТКИЙ ЛОГИЧЕСКИЙ ФИЛЬТР (ЗАПЛАТКА)
        // ====================================================
        // Если точка snap на проверяемой нити находится дальше чем на 0.6 метра от лапок жука,
        // мы принудительно возвращаем минимально возможный рейтинг.
        // Это на 100% блокирует прыжки сквозь центр и срез углов по воздуху (с 2 до 10 часов),
        // так как противоположные нити просто отсекаются логически!
        if (playerPos.dst2(snap) > 0.36f) { // 0.6 * 0.6 = 0.36 метра
            return -Float.MAX_VALUE;
        }

        // Вся остальная твоя оригинальная рабочая логика остается НЕИЗМЕННОЙ!
        // Дистанция по-прежнему считается от targetPosition, поэтому маркер отлично тянет жука вперед.
        float distance = targetPosition.dst(snap);

        float inputMatch = 0f;
        if (inputDirection.len2() > 0) {
            segmentDir.set(end).sub(start).nor();
            float matchForward = segmentDir.dot(inputDirection.nor());
            inputMatch = Math.max(matchForward, -matchForward);
        }

        return -(distance * 15f) + (inputMatch * 5f);
    }

    // Интерфейс-заглушка для лямбды, если понадобится расширение
    private interface SegmentCallback {
        void onCheck(Body body, Vector2 snap, float score);
    }

    private void checkSegment(Body b, Vector2 start, Vector2 end, Vector2 bestSnap, float bestScore, SegmentCallback cb) {
        Intersector.nearestSegmentPoint(start, end, targetPosition, snapPoint);
    }
}
