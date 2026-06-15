package com.github.br.libgx.jam37;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class SpiderWeb {

    private final World world;
    private final Vector2 center;

    // Хранилище всех сегментов паутины (нитей) для рендеринга и логики
    private final Array<Body> allSegments = new Array<>();

    // Отдельный список радиальных лучей, чтобы Main мог при старте найти за что зацепить жука
    private final Array<Body> radialStartSegments = new Array<>();

    /**
     * Конструктор генератора правильной паутины
     * @param world Физический мир Box2D
     * @param center Центр паутины (мировые координаты)
     * @param maxRadius Насколько далеко раскинется паутина
     * @param raysCount Количество радиальных лучей (осей, идущих от центра)
     * @param ringsCount Количество концентрических колец (витков спирали)
     */
    public SpiderWeb(World world, Vector2 center, float maxRadius, int raysCount, int ringsCount) {
        this.world = world;
        this.center = center;

        generateWeb(maxRadius, raysCount, ringsCount);
    }

    private void generateWeb(float maxRadius, int raysCount, int ringsCount) {
        float angleStep = (float) (Math.PI * 2 / raysCount);
        float radiusStep = maxRadius / ringsCount;

        // Временные векторы для расчетов, чтобы не спамить GC в цикле
        Vector2 posA = new Vector2();
        Vector2 posB = new Vector2();

        // ====================================================
        // ШАГ 1: ГЕНЕРИРУЕМ РАДИАЛЬНЫЕ ЛУЧИ (Идут из центра наружу)
        // ====================================================
        for (int i = 0; i < raysCount; i++) {
            float angle = i * angleStep;

            // Направление луча
            float dirX = (float) Math.cos(angle);
            float dirY = (float) Math.sin(angle);

            for (int j = 0; j < ringsCount; j++) {
                float rStart = j * radiusStep;
                float rEnd = (j + 1) * radiusStep;

                posA.set(center.x + dirX * rStart, center.y + dirY * rStart);
                posB.set(center.x + dirX * rEnd,   center.y + dirY * rEnd);

                Body segment = createSegment(posA, posB);
                allSegments.add(segment);

                // Сохраняем самые первые внутренние сегменты лучей, чтобы Main.java мог привязать жука
                if (j == 0) {
                    radialStartSegments.add(segment);
                }
            }
        }

        // ====================================================
        // ШАГ 2: ГЕНЕРИРУЕМ КОНЦЕНТРИЧЕСКИЕ КОЛЬЦА (Витковые нити)
        // ====================================================
        for (int j = 1; j <= ringsCount; j++) {
            float currentRadius = j * radiusStep;

            for (int i = 0; i < raysCount; i++) {
                float angleA = i * angleStep;
                // Замыкаем кольцо: последний луч соединяем с самым первым (индекс 0)
                float angleB = ((i + 1) % raysCount) * angleStep;

                posA.set(center.x + (float) Math.cos(angleA) * currentRadius, center.y + (float) Math.sin(angleA) * currentRadius);
                posB.set(center.x + (float) Math.cos(angleB) * currentRadius, center.y + (float) Math.sin(angleB) * currentRadius);

                Body segment = createSegment(posA, posB);
                allSegments.add(segment);
            }
        }
    }

    /**
     * Создает честное физическое тело для палочки-сегмента паутины с правильным углом
     */
    private Body createSegment(Vector2 pA, Vector2 pB) {
        BodyDef bDef = new BodyDef();
        // На джеме нити делаем статическими (StaticBody), либо кинематическими (KinematicBody),
        // чтобы они не падали вниз от гравитации, но жук мог по ним ползать.
        bDef.type = BodyDef.BodyType.KinematicBody;

        // 1. Позиция тела в Box2D — это всегда строго геометрический ЦЕНТР отрезка
        bDef.position.set((pA.x + pB.x) / 2f, (pA.y + pB.y) / 2f);

        // 2. ✨ ИДЕАЛЬНЫЙ РАСЧЕТ УГЛА: Локальная ось X тела теперь сонаправлена с нитью!
        bDef.angle = (float) Math.atan2(pB.y - pA.y, pB.x - pA.x);

        Body body = world.createBody(bDef);

        // 3. Создаем форму прямоугольника.
        // Так как угол уже задан в bDef.angle, форму мы делаем ровной (горизонтальной) вокруг нуля.
        float length = pA.dst(pB);
        float thickness = 0.08f; // Толщина нити в метрах Box2D

        PolygonShape shape = new PolygonShape();
        // setAsBox принимает ПОЛУширину и ПОЛУвысоту
        shape.setAsBox(length / 2f, thickness / 2f);

        FixtureDef fDef = new FixtureDef();
        fDef.shape = shape;
        fDef.isSensor = true; // Скелет паутины — это сенсор, чтобы жук сквозь него не бился, но ловил контакты

        body.createFixture(fDef);
        shape.dispose();

        // 4. Обязательно вешаем маркер данных, чтобы WebContactListener понимал, что это нить паутины
        body.setUserData(new WebSegmentData());

        return body;
    }

    /**
     * Применение силы ветра (если нити будут KinematicBody, это заставит их качаться)
     */
    public void applyWind(float forceX, float forceY) {
        // Если вы решите сделать нити KinematicBody для симуляции качания от ветра,
        // тут можно будет задавать им линейную скорость. Для StaticBody этот метод будет спать.
        for (Body segment : allSegments) {
            // Напрямую задаем кинематическим нитям линейную скорость от ветра
            // Чтобы паутина не улетала, скорость должна циклично менять направление (как ваш синус)
            segment.setLinearVelocity(forceX, forceY);
        }
    }

    /**
     * Отрисовка паутины с помощью геометрических линий ShapeRenderer
     */
    public void render(ShapeRenderer shapeRenderer) {
        Vector2 vertex0 = new Vector2();
        Vector2 vertex1 = new Vector2();

        for (Body segment : allSegments) {
            if (segment.getFixtureList().size > 0 && segment.getFixtureList().first().getShape() instanceof PolygonShape) {
                PolygonShape poly = (PolygonShape) segment.getFixtureList().first().getShape();

                // Достаем локальные координаты вершин краев палочки и переводим в мировые
                poly.getVertex(0, vertex0);
                poly.getVertex(2, vertex1); // вершина 2 противоположна вершине 0 в стандартном боксе

                Vector2 w0 = segment.getWorldPoint(vertex0);
                Vector2 w1 = segment.getWorldPoint(vertex1);

                // Рисуем линию от края до края
                shapeRenderer.line(w0.x, w0.y, w1.x, w1.y);
            }
        }
    }

    /**
     * Возвращает список внутренних стартовых сегментов лучей для инициализации игрока в Main.java
     */
    public Array<Body> getRadialStartSegments() {
        return radialStartSegments;
    }

    public Array<Body> getAllSegments() {
        return allSegments;
    }
}
