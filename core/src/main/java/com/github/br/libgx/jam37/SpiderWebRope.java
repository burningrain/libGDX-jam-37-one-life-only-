package com.github.br.libgx.jam37;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.physics.box2d.joints.RopeJointDef;
import com.badlogic.gdx.utils.Array;

public class SpiderWebRope {

    private final World world;
    private final Array<Body> segments = new Array<>();
    private final float segmentLength;

    public SpiderWebRope(World world, Vector2 start, Vector2 end, int numSegments, float thickness) {
        this.world = world;

        Vector2 direction = new Vector2(end).sub(start);
        float totalLength = direction.len();
        this.segmentLength = totalLength / numSegments;
        direction.nor();

        Body previousBody = null;
        Vector2 currentPos = new Vector2(start);

        for (int i = 0; i < numSegments; i++) {
            currentPos.add(direction.x * segmentLength, direction.y * segmentLength);

            float centerX = currentPos.x - (direction.x * segmentLength / 2f);
            float centerY = currentPos.y - (direction.y * segmentLength / 2f);
            Vector2 boxCenter = new Vector2(centerX, centerY);

            Body segment = createSegment(boxCenter, direction, thickness);
            segments.add(segment);

            if (previousBody != null) {
                float jointX = currentPos.x - (direction.x * segmentLength);
                float jointY = currentPos.y - (direction.y * segmentLength);
                connectBodies(previousBody, segment, new Vector2(jointX, jointY));
            }

            previousBody = segment;
        }
    }

    private Body createSegment(Vector2 pos, Vector2 direction, float thickness) {
        BodyDef bdef = new BodyDef();
        bdef.type = BodyDef.BodyType.DynamicBody;
        bdef.position.set(pos);

        bdef.linearDamping = 4.0f;
        bdef.angularDamping = 4.0f;
        bdef.gravityScale = 0.0f;

        Body body = world.createBody(bdef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(segmentLength / 2, thickness / 2, new Vector2(0, 0), direction.angleRad());

        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.density = 0.05f;
        fdef.friction = 0.2f;
        fdef.restitution = 0.0f;

        body.createFixture(fdef);
        shape.dispose();

        return body;
    }

    private void connectBodies(Body bodyA, Body bodyB, Vector2 anchorPoint) {
        RevoluteJointDef rDef = new RevoluteJointDef();
        rDef.initialize(bodyA, bodyB, anchorPoint);
        rDef.collideConnected = false;
        world.createJoint(rDef);

        RopeJointDef ropeDef = new RopeJointDef();
        ropeDef.bodyA = bodyA;
        ropeDef.bodyB = bodyB;
        ropeDef.localAnchorA.set(bodyA.getLocalPoint(anchorPoint));
        ropeDef.localAnchorB.set(bodyB.getLocalPoint(anchorPoint));

        ropeDef.maxLength = segmentLength * 1.02f;
        ropeDef.collideConnected = false;
        world.createJoint(ropeDef);
    }

    /**
     * Рисует непрерывную ломаную линию строго между центрами динамических звеньев.
     * Никаких жестких хвостов, создающих "крючки".
     */
    public void render(ShapeRenderer shapeRenderer) {
        for (Body body : segments) {
            if (body.getFixtureList().size == 0) continue;
            Fixture fixture = body.getFixtureList().first();

            if (fixture.getShape() instanceof PolygonShape) {
                PolygonShape poly = (PolygonShape) fixture.getShape();
                int vertexCount = poly.getVertexCount();

                float[] vertices = new float[vertexCount * 2];
                Vector2 tmpV = new Vector2();
                Vector2 normal = new Vector2();

                // Величина компенсации "воздушной подушки" Box2D (b2_polygonRadius).
                // Обычно в Box2D она равна 0.005 метра. Мы возьмем 0.01 метра для надежного пиксельного нахлеста.
                float b2RadiusCompensation = 0.06f;

                for (int i = 0; i < vertexCount; i++) {
                    poly.getVertex(i, tmpV);

                    // Рассчитываем нормаль (направление от центра локальной коробки к вершине)
                    normal.set(tmpV).nor();

                    // Смещаем вершину наружу по направлению нормали, возвращая ей реальный размер
                    tmpV.add(normal.x * b2RadiusCompensation, normal.y * b2RadiusCompensation);

                    // Переводим в мировые координаты
                    Vector2 worldPoint = body.getWorldPoint(tmpV);
                    vertices[i * 2] = worldPoint.x;
                    vertices[i * 2 + 1] = worldPoint.y;
                }

                // Рисуем замкнутый контур прямоугольника (звена нити)
                for (int i = 0; i < vertexCount; i++) {
                    float x1 = vertices[i * 2];
                    float y1 = vertices[i * 2 + 1];

                    int nextIdx = (i + 1) % vertexCount;
                    float x2 = vertices[nextIdx * 2];
                    float y2 = vertices[nextIdx * 2 + 1];

                    shapeRenderer.line(x1, y1, x2, y2);
                }
            }
        }
    }

    public void applyWind(float forceX, float forceY) {
        if (segments.size > 0) {
            Body centerSegment = segments.get(segments.size / 2);
            centerSegment.applyForceToCenter(forceX, forceY, true);
        }
    }

    public Array<Body> getSegments() {
        return segments;
    }
}
