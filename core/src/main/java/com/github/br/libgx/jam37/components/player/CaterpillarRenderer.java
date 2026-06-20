package com.github.br.libgx.jam37.components.player;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.github.br.libgx.jam37.Constants;
import com.github.br.libgx.jam37.systems.render.Renderable;

public class CaterpillarRenderer implements Renderable {

    private final Body[] segments;
    private final float[] radii;

    // Кэш векторов для защиты от Garbage Collector (GC Free)
    private final Vector2 direction = new Vector2();
    private final Vector2 normal = new Vector2();

    // Массивы для точек левой и правой стороны контура
    private final Vector2[] leftPoints;
    private final Vector2[] rightPoints;


    public CaterpillarRenderer(Body[] segments) {
        this.segments = segments;
        int count = segments.length;

        this.radii = new float[count];
        this.leftPoints = new Vector2[count];
        this.rightPoints = new Vector2[count];

        for (int i = 0; i < count; i++) {
            this.radii[i] = 0.22f - (i * 0.03f); // Радиусы сегментов (голова чуть шире)
            this.leftPoints[i] = new Vector2();
            this.rightPoints[i] = new Vector2();
        }
    }

    @Override
    public void draw(ShapeRenderer shapeRenderer) {
        if (segments == null || segments.length < 2) return;
        int count = segments.length;

        // ----------------------------------------------------
        // ЭТАП 1: Рассчитываем точки контура (Лево / Право)
        // ----------------------------------------------------
        for (int i = 0; i < count; i++) {
            Vector2 currentPos = segments[i].getPosition();

            // Направление сегмента (смотрим на соседа)
            if (i < count - 1) {
                direction.set(segments[i + 1].getPosition()).sub(currentPos);
            } else {
                direction.set(currentPos).sub(segments[i - 1].getPosition());
            }

            // Считаем перпендикуляр (нормаль) к направлению движения
            normal.set(-direction.y, direction.x).nor();

            // Сдвигаем точки влево и вправо на величину радиуса текущего звена
            leftPoints[i].set(currentPos).add(normal.x * radii[i], normal.y * radii[i]);
            rightPoints[i].set(currentPos).sub(normal.x * radii[i], normal.y * radii[i]);
        }

        // Переключаем ShapeRenderer в режим сплошной заливки
        shapeRenderer.set(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Constants.PLAYER_COLOR);

        // ----------------------------------------------------
        // ЭТАП 2: Отрисовка тела (Заливка трапециями/треугольниками)
        // ----------------------------------------------------
        for (int i = 0; i < count - 1; i++) {
            // Берем 4 точки между текущим звеном и следующим
            Vector2 l1 = leftPoints[i];
            Vector2 r1 = rightPoints[i];
            Vector2 l2 = leftPoints[i + 1];
            Vector2 r2 = rightPoints[i + 1];

            // Рисуем два треугольника, которые образуют сплошной сегмент кожи (quad)
            shapeRenderer.triangle(l1.x, l1.y, r1.x, r1.y, l2.x, l2.y);
            shapeRenderer.triangle(r1.x, r1.y, r2.x, r2.y, l2.x, l2.y);
        }

        // ----------------------------------------------------
        // ЭТАП 3: Сглаживаем голову и хвост полусферами
        // ----------------------------------------------------
        Vector2 headPos = segments[0].getPosition();
        shapeRenderer.circle(headPos.x, headPos.y, radii[0], 8);

        Vector2 tailPos = segments[count - 1].getPosition();
        shapeRenderer.circle(tailPos.x, tailPos.y, radii[count - 1], 8);

        // КРИТИЧЕСКИ ВАЖНО: Возвращаем тип в Line, чтобы RenderSystem продолжила корректно рисовать паутину!
        shapeRenderer.set(ShapeRenderer.ShapeType.Line);
    }
}
