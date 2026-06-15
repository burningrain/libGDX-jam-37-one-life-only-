package com.github.br.libgx.jam37.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef;
import com.github.br.libgx.jam37.WebContactListener;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.PlayerComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;

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

    public PhysicsSystem(World world) {
        this.world = world;
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
        if (physics.crawlJoint != null) {
            world.destroyJoint(physics.crawlJoint);
            physics.crawlJoint = null;
        }

        Body targetBody = intent.targetSegmentBody;
        Body playerBody = intent.bodyToBind;
        Vector2 axis = intent.calculatedWorldAxis;

        // Находим проекцию и выравниваем строго по данным из интента
        Vector2 targetPos = targetBody.getPosition();
        Vector2 toPlayer = new Vector2(playerBody.getPosition()).sub(targetPos);
        float distanceAlongAxis = toPlayer.dot(axis);
        Vector2 snapPosition = new Vector2(axis).scl(distanceAlongAxis).add(targetPos);

        playerBody.setTransform(snapPosition.x, snapPosition.y, playerBody.getAngle());

        // Чистая сборка сустава без копания в полигонах
        PrismaticJointDef jointDef = new PrismaticJointDef();
        jointDef.initialize(targetBody, playerBody, snapPosition, axis);
        jointDef.enableMotor = true;
        jointDef.maxMotorForce = 100.0f;
        jointDef.motorSpeed = 0f;
        jointDef.collideConnected = false;

        // Применяем лимиты, переданные снаружи
        jointDef.enableLimit = true;
        jointDef.lowerTranslation = -intent.calculatedHalfLength - distanceAlongAxis - 0.1f;
        jointDef.upperTranslation = intent.calculatedHalfLength - distanceAlongAxis + 0.1f;

        physics.crawlJoint = (PrismaticJoint) world.createJoint(jointDef);
        player.currentSegmentBody = targetBody;
    }

    @Override
    protected void dispose() {
        this.world.dispose();
    }
}
