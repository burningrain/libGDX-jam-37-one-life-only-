package com.github.br.libgx.jam37.components.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.github.br.libgx.jam37.systems.render.Renderable;

public class FlyRenderer implements Renderable {

    private final FlyComponent flyComp;
    private final Color coreColor = new Color(0.9f, 1.0f, 0.4f, 0.15f); // Ярко-желтый светляк
    private final Color auraColor = new Color(0.9f, 1.0f, 0.4f, 1f); // Прозрачная аура

    public FlyRenderer(FlyComponent flyComp) {
        this.flyComp = flyComp;
    }

    @Override
    public void draw(ShapeRenderer shapeRenderer) {
        Vector2 pos = flyComp.flyBody.getPosition();

        // Рассчитываем динамическую пульсацию ауры на основе таймера компонента
        float pulse = (float) Math.sin(flyComp.pulseTimer * 5f) * 0.08f;
        float baseRadius = 0.12f;
        float auraRadius = 0.35f + pulse;

        // 1. Рисуем мягкое размытое свечение (Filled круг)
        shapeRenderer.set(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(auraColor);
        shapeRenderer.circle(pos.x, pos.y, auraRadius, 10);

        // 2. Рисуем твердое пиксельное ядро мухи
        shapeRenderer.setColor(coreColor);
        shapeRenderer.circle(pos.x, pos.y, baseRadius, 6);
    }

}
