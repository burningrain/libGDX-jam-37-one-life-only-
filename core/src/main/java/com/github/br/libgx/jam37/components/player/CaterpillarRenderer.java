package com.github.br.libgx.jam37.components.player;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.github.br.libgx.jam37.systems.render.Renderable;

public class CaterpillarRenderer implements Renderable {

    private final Body[] segments;

    public CaterpillarRenderer(Body[] segments) {
        this.segments = segments;
    }

    @Override
    public void draw(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(1.0f, 1.0f, 1.0f, 1f);

        for (int j = 0; j < segments.length; j++) {
            Vector2 pos = segments[j].getPosition();
            float radius = 0.20f - (j * 0.03f); // Сужение хвоста гусеницы
            shapeRenderer.circle(pos.x, pos.y, radius, 8);
        }
    }

}
