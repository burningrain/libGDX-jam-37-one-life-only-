package com.github.br.libgx.jam37.systems.physics;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.managers.TagManager;
import com.badlogic.gdx.physics.box2d.*;
import com.github.br.libgx.jam37.Tags;
import com.github.br.libgx.jam37.WebSegmentData;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.player.PlayerComponent;

public class WebContactListener extends BaseSystem implements ContactListener {

    private ComponentMapper<PlayerComponent> mPlayer;
    private ComponentMapper<PhysicsComponent> mPhysics;

    private int playerId = -1;

    @Override
    protected void processSystem() {
    }

    @Override
    protected void initialize() {
        setEnabled(false);
    }

    @Override
    public void beginContact(Contact contact) {
        int playerId = getPlayerId();
        PhysicsComponent playerPhysicsComponent = mPhysics.get(playerId);
        Body pBody = playerPhysicsComponent.body;

        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Body bodyA = fixtureA.getBody();
        Body bodyB = fixtureB.getBody();

        // Проверяем: кто-то из них жук, а кто-то — сегмент паутины?
        Body playerBody = null;
        Body webBody = null;

        if (bodyA.getUserData() instanceof WebSegmentData) {
            webBody = bodyA;
        } else if (bodyB.getUserData() instanceof WebSegmentData)  {
            webBody = bodyB;
        }

        if (bodyA == pBody) {
            playerBody = bodyA;
        } else if (bodyB == pBody)  {
            playerBody = bodyB;
        }

        // Если нашли и жука, и палочку паутины — заносим в список живых контактов сущности
        if (playerBody != null && webBody != null) {
            PlayerComponent player = mPlayer.get(playerId);
            if (player != null && !player.activeContacts.contains(webBody, true)) {
                player.activeContacts.add(webBody);
            }
        }
    }

    private int getPlayerId() {
        if (playerId == -1) {
            playerId = world.getSystem(TagManager.class).getEntityId(Tags.PLAYER);
        }
        return playerId;
    }

    @Override
    public void endContact(Contact contact) {
        int playerId = getPlayerId();
        PhysicsComponent playerPhysicsComponent = mPhysics.get(playerId);
        Body pBody = playerPhysicsComponent.body;

        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Body bodyA = fixtureA.getBody();
        Body bodyB = fixtureB.getBody();

        // Проверяем: кто-то из них жук, а кто-то — сегмент паутины?
        Body playerBody = null;
        Body webBody = null;

        if (bodyA.getUserData() instanceof WebSegmentData) {
            webBody = bodyA;
        } else if (bodyB.getUserData() instanceof WebSegmentData)  {
            webBody = bodyB;
        }

        if (bodyA == pBody) {
            playerBody = bodyA;
        } else if (bodyB == pBody)  {
            playerBody = bodyB;
        }

        if (playerBody != null && webBody != null) {
            PlayerComponent player = mPlayer.get(playerId);
            if (player != null) {
                player.activeContacts.removeValue(webBody, true);
            }
        }
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
    }

}
