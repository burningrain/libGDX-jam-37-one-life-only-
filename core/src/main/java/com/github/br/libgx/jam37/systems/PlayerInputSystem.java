package com.github.br.libgx.jam37.systems;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.player.PlayerComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;

@All({PlayerComponent.class, PhysicsComponent.class})
public class PlayerInputSystem extends IteratingSystem {

    protected ComponentMapper<PlayerComponent> mPlayer;
    protected ComponentMapper<PrismaticRebindIntent> mIntent;
    protected ComponentMapper<PhysicsComponent> mPhysics;

    private final Vector2 inputDirection = new Vector2();

    @Override
    protected void process(int entityId) {
        PlayerComponent player = mPlayer.get(entityId);
        PhysicsComponent physicsComponent = mPhysics.get(entityId);
        Body playerBody = physicsComponent.body;
        Vector2 playerWorldPos = playerBody.getPosition();

        if (player.currentSegmentBody == null || mIntent.has(entityId)) return;

        // Считываем ввод игрока
        inputDirection.set(0, 0);
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) inputDirection.x = 1;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) inputDirection.x = -1;
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) inputDirection.y = 1;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) inputDirection.y = -1;

        // ====================================================
        // 1. УПРАВЛЕНИЕ МОТОРОМ
        // ====================================================
        int length = physicsComponent.crawlJoints.length;
        if (physicsComponent.crawlJoints != null) {
            float speedToApply = 0f;

            if (inputDirection.len2() > 0) {
                float angle = player.currentSegmentBody.getAngle();
                Vector2 jointAxis = new Vector2((float) Math.cos(angle), (float) Math.sin(angle)).nor();
                float dotProduct = inputDirection.dot(jointAxis);

                if (dotProduct > 0.1f) {
                    speedToApply = physicsComponent.crawlSpeed;
                } else if (dotProduct < -0.1f) {
                    speedToApply = -physicsComponent.crawlSpeed;
                }
            }

            // Включаем мотор СИНХРОННО на каждом живом суставе гусеницы
            for (int i = 0; i < physicsComponent.crawlJoints.length; i++) {
                if (physicsComponent.crawlJoints[i] != null) {
                    physicsComponent.crawlJoints[i].setMotorSpeed(speedToApply);
                }
            }
        }

        // ====================================================
        // 2. СМЕНА РЕЛЬС НА ПЕРЕКРЕСТКЕ (По цепочке для всей гусеницы)
        // ====================================================
        if (player.activeContacts.size >= 1 && inputDirection.len2() > 0) {
            Body bestNextBodyForHead = null;
            float bestMatch = -1f;

            Vector2 normInput = new Vector2(inputDirection).nor();
            Vector2 toTargetDir = new Vector2();

            for (Body webBody : player.activeContacts) {
                if (webBody == player.currentSegmentBody) continue;

                toTargetDir.set(webBody.getPosition()).sub(playerWorldPos).nor();
                float dot = normInput.dot(toTargetDir);

                if (dot > 0.8f && dot > bestMatch) {
                    bestMatch = dot;
                    bestNextBodyForHead = webBody;
                }
            }

            // Если нашли куда повернуть головой — запускаем перепривязку ВСЕЙ гусеницы
            if (bestNextBodyForHead != null) {
                PrismaticRebindIntent rebind = mIntent.create(entityId);

                // Переменная длина на основе реального тела гусеницы
                rebind.init(length); // Инициализируем массивы внутри интента под нужный размер

                // Передаем массив всех сегментов тела
                rebind.bodiesToBind = player.bodySegments;
                rebind.targetSegmentBodies = new Body[length];

                // ====================================================
                // ДИНАМИЧЕСКИЙ СДВИГ РЕЛЬС ДЛЯ ЛЮБОГО КОЛИЧЕСТВА СЕГМЕНТОВ
                // ====================================================

                // Индекс 0 — это всегда голова, она идет на новую выбранную нить
                rebind.targetSegmentBodies[0] = bestNextBodyForHead;

                // Для всех остальных сегментов хвоста (от 1 до конца):
                for (int i = 1; i < length; i++) {
                    // Если суставы уже созданы (игра идет), то каждый сегмент i
                    // забирает себе ту нить паутины, к которой СЕЙЧАС привязан предыдущий сегмент (i - 1)
                    if (physicsComponent.crawlJoints != null && physicsComponent.crawlJoints[i - 1] != null) {
                        rebind.targetSegmentBodies[i] = physicsComponent.crawlJoints[i - 1].getBodyA();
                    } else {
                        // Если суставов еще нет (самый первый шаг на старте),
                        // временно сажаем весь хвост на текущую нить головы, чтобы не было NullPointerException
                        rebind.targetSegmentBodies[i] = player.currentSegmentBody;
                    }
                }

                // ====================================================
                // ПРОСЧЕТ ГЕОМЕТРИИ ОСЕЙ ДЛЯ ВСЕХ СУСТАВОВ
                // ====================================================
                for (int i = 0; i < length; i++) {
                    Body targetWeb = rebind.targetSegmentBodies[i];

                    float targetAngle = targetWeb.getAngle();
                    rebind.calculatedWorldAxes[i].set((float) Math.cos(targetAngle), (float) Math.sin(targetAngle)).nor();

                    float halfLength = 1.0f;
                    if (targetWeb.getFixtureList().size > 0 && targetWeb.getFixtureList().first().getShape() instanceof PolygonShape) {
                        PolygonShape poly = (PolygonShape) targetWeb.getFixtureList().first().getShape();
                        Vector2 vertex = new Vector2();
                        poly.getVertex(0, vertex);
                        halfLength = Math.abs(vertex.x);
                    }
                    rebind.calculatedHalfLengths[i] = halfLength;
                }

                // Гасим моторы у всех существующих суставов перед перепривязкой
                if (physicsComponent.crawlJoints != null) {
                    for (int i = 0; i < physicsComponent.crawlJoints.length; i++) {
                        if (physicsComponent.crawlJoints[i] != null) {
                            physicsComponent.crawlJoints[i].setMotorSpeed(0f);
                        }
                    }
                }
            }
        }
    }
}
