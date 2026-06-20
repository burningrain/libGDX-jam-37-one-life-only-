package com.github.br.libgx.jam37.systems.physics;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.player.PlayerComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;
import com.github.br.libgx.jam37.systems.render.RenderSystem;

public class PhysicsSystem extends BaseSystem {

    private final World world;
    private static final float TIME_STEP = 1 / 60f;
    private static final int VELOCITY_ITERATIONS = 6;
    private static final int POSITION_ITERATIONS = 2;
    private float accumulator = 0;

    protected WebContactListener contactListener;
    protected ComponentMapper<PhysicsComponent> mPhysics;
    protected ComponentMapper<PlayerComponent> mPlayer;
    protected ComponentMapper<PrismaticRebindIntent> mIntent;

    public World getBox2dWorld() {
        return world;
    }

    public PhysicsSystem() {
        this.world = new World(new Vector2(0, 0), true);
    }

    @Override
    protected void initialize() {
        world.setContactListener(contactListener);
    }

    @Override
    protected void processSystem() {
        processRebindIntents();

        float delta = Gdx.graphics.getDeltaTime();
        accumulator += delta;
        while (accumulator >= TIME_STEP) {
            this.world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            accumulator -= TIME_STEP;
        }
    }

    private void processRebindIntents() {
        com.artemis.utils.IntBag aspectBag = getWorld().getAspectSubscriptionManager()
            .get(com.artemis.Aspect.all(PrismaticRebindIntent.class, PhysicsComponent.class, PlayerComponent.class))
            .getEntities();

        int[] ids = aspectBag.getData();
        for (int i = 0, s = aspectBag.size(); i < s; i++) {
            int entityId = ids[i];
            handleRebind(mIntent.get(entityId), mPhysics.get(entityId), mPlayer.get(entityId));
            mIntent.remove(entityId);
        }
    }

    private void handleRebind(PrismaticRebindIntent intent, PhysicsComponent physics, PlayerComponent player) {
        int length = intent.targetSegmentBodies.length;

        // 1. Уничтожаем ВСЕ старые суставы рельс гусеницы (если они существовали)
        if (physics.crawlJoints != null) {
            for (int i = 0; i < physics.crawlJoints.length; i++) {
                if (physics.crawlJoints[i] != null) {
                    world.destroyJoint(physics.crawlJoints[i]);
                    physics.crawlJoints[i] = null;
                }
            }
        } else {
            physics.crawlJoints = new PrismaticJoint[length];
        }

        // 2. Последовательно пересобираем суставы
        for (int i = 0; i < length; i++) {
            Body targetBody = intent.targetSegmentBodies[i];
            Body playerSegment = intent.bodiesToBind[i];
            Vector2 axis = intent.calculatedWorldAxes[i];
            float halfLength = intent.calculatedHalfLengths[i];

            Vector2 targetPos = targetBody.getPosition();
            Vector2 toPlayer = new Vector2(playerSegment.getPosition()).sub(targetPos);
            float distanceAlongAxis = toPlayer.dot(axis);
            Vector2 snapPosition = new Vector2(axis).scl(distanceAlongAxis).add(targetPos);

            playerSegment.setTransform(snapPosition.x, snapPosition.y, playerSegment.getAngle());

            PrismaticJointDef jointDef = new PrismaticJointDef();
            jointDef.initialize(targetBody, playerSegment, snapPosition, axis);
            jointDef.enableMotor = true;
            jointDef.maxMotorForce = 100.0f;
            jointDef.motorSpeed = 0f;
            jointDef.collideConnected = false;

            jointDef.enableLimit = true;
            if (i == 0) {
                jointDef.lowerTranslation = -halfLength - distanceAlongAxis - 0.1f;
                jointDef.upperTranslation = halfLength - distanceAlongAxis + 0.1f;
            } else {
                jointDef.lowerTranslation = -halfLength - distanceAlongAxis;
                jointDef.upperTranslation = halfLength - distanceAlongAxis;
            }

            physics.crawlJoints[i] = (PrismaticJoint) world.createJoint(jointDef);
        }

        player.currentSegmentBody = intent.targetSegmentBodies[0];
    }

    @Override
    protected void dispose() {
        this.world.dispose();
    }
}
