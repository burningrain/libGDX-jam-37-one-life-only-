package com.github.br.libgx.jam37.systems.physics.contact;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.physics.box2d.*;
import com.github.br.libgx.jam37.EntityFactory;
import com.github.br.libgx.jam37.components.DeleteIntentComponent;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.enemy.FlyComponent;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;
import com.github.br.libgx.jam37.components.player.PlayerComponent;
import com.github.br.libgx.jam37.systems.PlayerInputSystem;
import com.github.br.libgx.jam37.systems.physics.data.*;

public class WebContactListener extends BaseSystem implements ContactListener {

    private ComponentMapper<DeleteIntentComponent> mDelete;

    private ComponentMapper<PlayerComponent> mPlayer;
    private ComponentMapper<FlyComponent> flyMapper;
    private ComponentMapper<PhysicsComponent> mPhysics;

    // ресолверы контактов
    private EmptyContactResolver emptyContactResolver = new EmptyContactResolver();

    private WebSegmentContactResolver webSegmentContactResolver = new WebSegmentContactResolver();

    private PlayerContactResolver playerContactResolver = new PlayerContactResolver();
    private PlayerHeadContactResolver playerHeadContactResolver = new PlayerHeadContactResolver();

    private FlyContactResolver flyContactResolver = new FlyContactResolver();

    private SpiderContactResolver spiderContactResolver = new SpiderContactResolver();
    private SpiderCheliceraeContactResolver spiderCheliceraeContactResolver = new SpiderCheliceraeContactResolver();
    // // ресолверы контактов

    @Override
    protected void processSystem() {
    }

    @Override
    protected void initialize() {
        setEnabled(false);
    }

    public PhysicsContactResolver<? extends ContactData> getPhysicsContact(ContactData data) {
        if (data != null && data.isDeleted()) {
            return emptyContactResolver;
        }

        if (data instanceof WebSegmentData) {
            return webSegmentContactResolver;
        }
        if (data instanceof FlyData) {
            return flyContactResolver;
        }
        if (data instanceof PlayerData) {
            return playerContactResolver;
        }
        if (data instanceof PlayerHeadData) {
            return playerHeadContactResolver;
        }
        if (data instanceof SpiderData) {
            return spiderContactResolver;
        }
        if (data instanceof SpiderCheliceraeData) {
            return spiderCheliceraeContactResolver;
        }

        return emptyContactResolver;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Body bodyA = fixtureA.getBody();
        Body bodyB = fixtureB.getBody();

        ContactData contactDataA = (ContactData) bodyA.getUserData();
        ContactData contactDataB = (ContactData) bodyB.getUserData();

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactA = getPhysicsContact(contactDataA);
        resolverBodyContactA.beginContact(contact, contactDataA, bodyA, bodyB, contactDataB);

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactB = getPhysicsContact(contactDataB);
        resolverBodyContactB.beginContact(contact, contactDataB, bodyB, bodyA, contactDataA);
    }

    @Override
    public void endContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Body bodyA = fixtureA.getBody();
        Body bodyB = fixtureB.getBody();

        ContactData contactDataA = (ContactData) bodyA.getUserData();
        ContactData contactDataB = (ContactData) bodyB.getUserData();

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactA = getPhysicsContact(contactDataA);
        resolverBodyContactA.endContact(contact, contactDataA, bodyA, bodyB, contactDataB);

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactB = getPhysicsContact(contactDataB);
        resolverBodyContactB.endContact(contact, contactDataB, bodyB, bodyA, contactDataA);
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Body bodyA = fixtureA.getBody();
        Body bodyB = fixtureB.getBody();

        ContactData contactDataA = (ContactData) bodyA.getUserData();
        ContactData contactDataB = (ContactData) bodyB.getUserData();

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactA = getPhysicsContact(contactDataA);
        resolverBodyContactA.preSolve(contact, oldManifold, contactDataA, bodyA, bodyB, contactDataB);

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactB = getPhysicsContact(contactDataB);
        resolverBodyContactB.preSolve(contact, oldManifold, contactDataB, bodyB, bodyA, contactDataA);
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        Body bodyA = fixtureA.getBody();
        Body bodyB = fixtureB.getBody();

        ContactData contactDataA = (ContactData) bodyA.getUserData();
        ContactData contactDataB = (ContactData) bodyB.getUserData();

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactA = getPhysicsContact(contactDataA);
        resolverBodyContactA.postSolve(contact, impulse, contactDataA, bodyA, bodyB, contactDataB);

        @SuppressWarnings("unchecked")
        PhysicsContactResolver resolverBodyContactB = getPhysicsContact(contactDataB);
        resolverBodyContactB.postSolve(contact, impulse, contactDataB, bodyB, bodyA, contactDataA);
    }


    public class WebSegmentContactResolver implements PhysicsContactResolver<WebSegmentData> {
        @Override
        public void beginContact(Contact contact, WebSegmentData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void endContact(Contact contact, WebSegmentData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold, WebSegmentData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse, WebSegmentData myContact, Body myBody, Body object, ContactData objectContact) {

        }
    }

    public class FlyContactResolver implements PhysicsContactResolver<FlyData> {

        @Override
        public void beginContact(Contact contact, FlyData myContact, Body myBody, Body object, ContactData objectContact) {
            if (objectContact instanceof PlayerHeadData) {
                EntityFactory entityFactory = getWorld().getSystem(EntityFactory.class);
                GameParamsComponent gameParamsComponent = entityFactory.getGameParamsComponent();
                gameParamsComponent.currentPoints += myContact.points;

                // удаляем сущность
                int flyEntityId = myContact.getEntityId();
                FlyComponent flyComponent = flyMapper.get(flyEntityId);
                WebSegmentData userData = (WebSegmentData) flyComponent.attachedWebSegment.getUserData(); // flyComponent == null
                userData.isFreeForFly = true;

                mDelete.create(flyEntityId);
                myContact.isDeleted = true;
            }
        }

        @Override
        public void endContact(Contact contact, FlyData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold, FlyData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse, FlyData myContact, Body myBody, Body object, ContactData objectContact) {

        }

    }

    public class PlayerContactResolver implements PhysicsContactResolver<PlayerData> {
        @Override
        public void beginContact(Contact contact, PlayerData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void endContact(Contact contact, PlayerData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold, PlayerData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse, PlayerData myContact, Body myBody, Body object, ContactData objectContact) {

        }

    }

    public class PlayerHeadContactResolver implements PhysicsContactResolver<PlayerHeadData> {
        @Override
        public void beginContact(Contact contact, PlayerHeadData myContact, Body playerBody, Body object, ContactData objectContact) {

            if (objectContact instanceof WebSegmentData) {
                int playerId = myContact.getEntityId();

                // Если нашли и жука, и палочку паутины — заносим в список живых контактов сущности
                if (playerBody != null && object != null) {
                    PlayerComponent player = mPlayer.get(playerId);
                    if (player != null && !player.activeContacts.contains(object, true)) {
                        player.activeContacts.add(object);
                    }
                }
            }
        }

        @Override
        public void endContact(Contact contact, PlayerHeadData myContact, Body playerBody, Body object, ContactData objectContact) {
            if (objectContact instanceof WebSegmentData) {
                if (playerBody != null && object != null) {
                    PlayerComponent player = mPlayer.get(myContact.getEntityId());
                    if (player != null) {
                        player.activeContacts.removeValue(object, true);
                    }
                }
            }
        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold, PlayerHeadData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse, PlayerHeadData myContact, Body myBody, Body object, ContactData objectContact) {

        }
    }


    public class SpiderCheliceraeContactResolver implements PhysicsContactResolver<SpiderCheliceraeData> {
        @Override
        public void beginContact(Contact contact, SpiderCheliceraeData myContact, Body myBody, Body object, ContactData objectContact) {
            if (objectContact instanceof PlayerHeadData) {
                getWorld().getSystem(PlayerInputSystem.class).setEnabled(false);
            }
        }

        @Override
        public void endContact(Contact contact, SpiderCheliceraeData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold, SpiderCheliceraeData myContact, Body myBody, Body object, ContactData objectContact) {
            if (objectContact instanceof PlayerHeadData) {
                contact.setEnabled(false);
            }
        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse, SpiderCheliceraeData myContact, Body myBody, Body object, ContactData objectContact) {
        }
    }

    public class SpiderContactResolver implements PhysicsContactResolver<SpiderData> {

        @Override
        public void beginContact(Contact contact, SpiderData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void endContact(Contact contact, SpiderData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold, SpiderData myContact, Body myBody, Body object, ContactData objectContact) {

        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse, SpiderData myContact, Body myBody, Body object, ContactData objectContact) {

        }

    }

}
