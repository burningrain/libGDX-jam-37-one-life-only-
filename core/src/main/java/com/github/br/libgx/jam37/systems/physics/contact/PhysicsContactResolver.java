package com.github.br.libgx.jam37.systems.physics.contact;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.github.br.libgx.jam37.systems.physics.data.ContactData;

public interface PhysicsContactResolver<C extends ContactData> {

    void beginContact(Contact contact, C myContact, Body myBody, Body object, ContactData objectContact);

    void endContact(Contact contact, C myContact, Body myBody, Body object, ContactData objectContact);

    void preSolve(Contact contact, Manifold oldManifold, C myContact, Body myBody, Body object, ContactData objectContact);

    void postSolve(Contact contact, ContactImpulse impulse, C myContact, Body myBody, Body object, ContactData objectContact);

}
