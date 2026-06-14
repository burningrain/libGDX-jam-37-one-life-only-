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
    private final Array<Body> edgeAnchors = new Array<>(); // Храним якоря на деревьях
    private final Body webCenterBody; // Храним центр паутины
    private final World world;

    public SpiderWeb(World world, Vector2 center, float radius, int numRays, int numRings) {
        this.world = world;

        // 1. Создаем один центральный узел (плавающий)
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

        // 2. Генерируем все радиальные лучи
        for (int i = 0; i < numRays; i++) {
            float angle = (i * 360f / numRays) * MathUtils.degreesToRadians;

            float endX = center.x + MathUtils.cos(angle) * radius;
            float endY = center.y + MathUtils.sin(angle) * radius;
            Vector2 endPoint = new Vector2(endX, endY);

            SpiderWebRope ray = new SpiderWebRope(world, center, endPoint, RAY_SEGMENTS, 0.04f);
            radialRopes.add(ray);

            // Закрепляем начало луча к центру
            RevoluteJointDef centerJoint = new RevoluteJointDef();
            centerJoint.initialize(webCenterBody, ray.getSegments().first(), center);
            centerJoint.collideConnected = false;
            world.createJoint(centerJoint);

            // Создаем статический якорь дерева на краю
            BodyDef anchorDef = new BodyDef();
            anchorDef.type = BodyDef.BodyType.StaticBody;
            anchorDef.position.set(endPoint);
            Body edgeAnchor = world.createBody(anchorDef);
            edgeAnchors.add(edgeAnchor); // Запоминаем его

            // Прибиваем конец луча к этому дереву
            RevoluteJointDef edgeJoint = new RevoluteJointDef();
            edgeJoint.initialize(edgeAnchor, ray.getSegments().peek(), endPoint);
            edgeJoint.collideConnected = false;
            world.createJoint(edgeJoint);
        }

        // 3. Создаем соединяющие спиральные кольца
        for (int ring = 1; ring <= numRings; ring++) {
            float ringRadius = radius * ((float) ring / numRings);
            int raySegmentIdx = MathUtils.clamp((int)((float)ring / numRings * RAY_SEGMENTS) - 1, 0, RAY_SEGMENTS - 1);

            for (int rayIdx = 0; rayIdx < numRays; rayIdx++) {
                float angle1 = (rayIdx * 360f / numRays) * MathUtils.degreesToRadians;
                float angle2 = (((rayIdx + 1) % numRays) * 360f / numRays) * MathUtils.degreesToRadians;

                Vector2 p1 = new Vector2(center.x + MathUtils.cos(angle1) * ringRadius, center.y + MathUtils.sin(angle1) * ringRadius);
                Vector2 p2 = new Vector2(center.x + MathUtils.cos(angle2) * ringRadius, center.y + MathUtils.sin(angle2) * ringRadius);

                SpiderWebRope ringSegment = new SpiderWebRope(world, p1, p2, 2, 0.03f);
                spiralRopes.add(ringSegment);

                // Крепление начала кольца к лучу
                // --- КРЕПЛЕНИЕ ЛЕВОГО КОНЦА (к текущему лучу в точке p1) ---
                SpiderWebRope currentRay = radialRopes.get(rayIdx);
                Body rayBody1 = currentRay.getSegments().get(raySegmentIdx);
                Body ringStartBody = ringSegment.getSegments().first();

                // Заменяем на RevoluteJointDef для жесткой фиксации координат точки контакта
                RevoluteJointDef jointDef1 = new RevoluteJointDef();
                jointDef1.initialize(rayBody1, ringStartBody, p1);
                jointDef1.collideConnected = false;
                world.createJoint(jointDef1);

                // --- КРЕПЛЕНИЕ ПРАВОГО КОНЦА (к следующему лучу в точке p2) ---
                SpiderWebRope nextRay = radialRopes.get((rayIdx + 1) % numRays);
                Body rayBody2 = nextRay.getSegments().get(raySegmentIdx);
                Body ringEndBody = ringSegment.getSegments().peek();

                // Заменяем на RevoluteJointDef для правого края перемычки
                RevoluteJointDef jointDef2 = new RevoluteJointDef();
                jointDef2.initialize(rayBody2, ringEndBody, p2);
                jointDef2.collideConnected = false;
                world.createJoint(jointDef2);
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
}
