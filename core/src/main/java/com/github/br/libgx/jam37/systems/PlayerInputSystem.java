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
import com.github.br.libgx.jam37.components.PlayerComponent;
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
        if (physicsComponent.crawlJoint != null) {
            if (inputDirection.len2() > 0) {
                float angle = player.currentSegmentBody.getAngle();
                Vector2 jointAxis = new Vector2((float) Math.cos(angle), (float) Math.sin(angle)).nor();
                float dotProduct = inputDirection.dot(jointAxis);

                if (dotProduct > 0.1f) {
                    physicsComponent.crawlJoint.setMotorSpeed(physicsComponent.crawlSpeed);
                } else if (dotProduct < -0.1f) {
                    physicsComponent.crawlJoint.setMotorSpeed(-physicsComponent.crawlSpeed);
                } else {
                    physicsComponent.crawlJoint.setMotorSpeed(0f);
                }
            } else {
                physicsComponent.crawlJoint.setMotorSpeed(0f);
            }
        }

        // ====================================================
        // 2. СМЕНА РЕЛЬС НА ПЕРЕКРЕСТКЕ
        // ====================================================
        if (player.activeContacts.size > 1 && inputDirection.len2() > 0) {
            Body bestNextBody = null;
            float bestMatch = -1f;

            Vector2 normInput = new Vector2(inputDirection).nor();
            Vector2 toTargetDir = new Vector2();

            for (Body webBody : player.activeContacts) {
                if (webBody == player.currentSegmentBody) continue;

                toTargetDir.set(webBody.getPosition()).sub(playerWorldPos).nor();
                float dot = normInput.dot(toTargetDir);

                if (dot > 0.8f && dot > bestMatch) {
                    bestMatch = dot;
                    bestNextBody = webBody;
                }
            }

            // Если нашли тело — рассчитываем всю его геометрию прямо тут и отдаем физике
            if (bestNextBody != null) {
                PrismaticRebindIntent rebind = mIntent.create(entityId);
                rebind.bodyToBind = playerBody;
                rebind.targetSegmentBody = bestNextBody;

                // 1. Считаем мировую ось на основе честного угла Box2D-тела
                float targetAngle = bestNextBody.getAngle();
                rebind.calculatedWorldAxis.set((float) Math.cos(targetAngle), (float) Math.sin(targetAngle)).nor();

                // 2. Считаем полудлину из фикстуры
                float halfLength = 1.0f; // Дефолт на крайний случай
                if (bestNextBody.getFixtureList().size > 0 && bestNextBody.getFixtureList().first().getShape() instanceof PolygonShape) {
                    PolygonShape poly = (PolygonShape) bestNextBody.getFixtureList().first().getShape();
                    Vector2 vertex = new Vector2();
                    poly.getVertex(0, vertex);
                    halfLength = Math.abs(vertex.x);
                }
                rebind.calculatedHalfLength = halfLength;

                if (physicsComponent.crawlJoint != null) {
                    physicsComponent.crawlJoint.setMotorSpeed(0f);
                }
            }
        }
    }
}
