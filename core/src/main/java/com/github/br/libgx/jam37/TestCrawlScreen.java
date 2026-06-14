package com.github.br.libgx.jam37;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.math.Vector2;

public class TestCrawlScreen extends ScreenAdapter {

    private World world;
    private Box2DDebugRenderer debugRenderer;
    private OrthographicCamera camera;

    private Body webSegment; // Звено нити паутины
    private Body playerBody; // Тело жука
    private PrismaticJoint crawlJoint; // Наш физический рельс

    private float time = 0;

    @Override
    public void show() {
        // Создаем физический мир без глобальной гравитации (вид сверху)
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 20f, 15f); // Экран 20 на 15 метров
        camera.position.set(10f, 7.5f, 0);
        camera.update();

        // 1. СОЗДАЕМ СТАТИЧНЫЙ ЯКОРЬ (Ветка дерева)
        BodyDef anchorDef = new BodyDef();
        anchorDef.type = BodyDef.BodyType.StaticBody;
        anchorDef.position.set(10f, 12f); // Вверху экрана по центру
        Body anchor = world.createBody(anchorDef);

        // 2. СОЗДАЕМ ОДНО ДИНАМИЧЕСКОЕ ЗВЕНО ПАУТИНЫ (Палочка длиной 4 метра)
        BodyDef segmentDef = new BodyDef();
        segmentDef.type = BodyDef.BodyType.DynamicBody;
        segmentDef.position.set(10f, 9f); // Висит чуть ниже якоря
        segmentDef.linearDamping = 1.0f;  // Сопротивление воздуха, чтобы нить плавно гасила качание
        webSegment = world.createBody(segmentDef);

        PolygonShape segmentShape = new PolygonShape();
        segmentShape.setAsBox(0.1f, 2.0f); // Тонкий вертикальный прямоугольник (полуширина 0.1м, полувысота 2м)

        FixtureDef sFixture = new FixtureDef();
        sFixture.shape = segmentShape;
        sFixture.density = 0.5f;
        // КРИТИЧЕСКИ ВАЖНО: Отключаем физическое столкновение хитбоксов жука и нити,
        // чтобы они не выталкивали друг друга и не взрывали симуляцию
        sFixture.filter.groupIndex = -1;

        webSegment.createFixture(sFixture);
        segmentShape.dispose();

        // 3. СОЕДИНЯЕМ НИТЬ С ДЕРЕВОМ ШАРНИРОМ (Чтобы она могла качаться как маятник)
        RevoluteJointDef revDef = new RevoluteJointDef();
        revDef.initialize(anchor, webSegment, new Vector2(10f, 12f)); // Точка вращения на ветке
        world.createJoint(revDef);

        // 4. СОЗДАЕМ ДИНАМИЧЕСКОЕ ТЕЛО ЖУКА (Круг)
        BodyDef pDef = new BodyDef();
        pDef.type = BodyDef.BodyType.DynamicBody;
        pDef.position.set(10f, 9f); // Спавним ровно в центре палочки нити
        playerBody = world.createBody(pDef);

        CircleShape playerShape = new CircleShape();
        playerShape.setRadius(0.3f);

        FixtureDef pFixture = new FixtureDef();
        pFixture.shape = playerShape;
        pFixture.density = 1.0f;
        pFixture.filter.groupIndex = -1; // Такой же индекс — игнорируем хитбокс нити!

        playerBody.createFixture(pFixture);
        playerShape.dispose();

        // 5. САМАЯ ГЛАВНАЯ МАГИЯ: ПРИШИВАЕМ ЖУКА К НИТИ ЧЕРЕЗ СКОЛЬЗЯЩИЙ ШАРНИР
        PrismaticJointDef prismDef = new PrismaticJointDef();

        // initialize принимает: (Тело А: Нить, Тело Б: Жук, Точка фиксации, Вектор направления скольжения)
        // Нам нужно, чтобы жук скользил ВДОЛЬ палочки, то есть по локальной вертикальной оси нити (0, 1)
        prismDef.initialize(webSegment, playerBody, playerBody.getPosition(), new Vector2(0, 1f));

        // Ограничиваем движение, чтобы жук не уполз дальше краев палочки
        prismDef.enableLimit = true;
        prismDef.lowerTranslation = -1.8f; // Ограничение ползания вниз в метрах от центра палочки
        prismDef.upperTranslation = 1.8f;  // Ограничение ползания вверх в метрах

        // Включаем встроенный физический моторчик для перемещения жука клавишами
        prismDef.enableMotor = true;
        prismDef.maxMotorForce = 100.0f; // Сила лапок жука (мощность мотора)

        crawlJoint = (PrismaticJoint) world.createJoint(prismDef);
    }

    @Override
    public void render(float delta) {
        time += delta;

        // ----------------------------------------------------
        // УПРАВЛЕНИЕ ЖУКОМ ЧЕРЕЗ СКОРОСТЬ МОТОРА СУСТАВА
        // ----------------------------------------------------
        // Мы больше не меняем координаты вручную! Мы просто включаем моторчик сустава.
        // Если зажата кнопка ВВЕРХ — задаем мотору положительную скорость (жук ползет вперед по рельсу).
        // Если зажата ВНИЗ — отрицательную (ползет назад). Если ничего не нажато — скорость 0 (жук держится на месте).
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            crawlJoint.setMotorSpeed(3.0f); // Скорость 3 метра в секунду вверх по нити
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            crawlJoint.setMotorSpeed(-3.0f); // 3 метра в секунду вниз по нити
        } else {
            crawlJoint.setMotorSpeed(0f); // Стоим на месте и намертво держимся за нить
        }

        // Имитируем порывы ветра, которые толкают маятник нити влево-вправо
        float wind = (float) Math.sin(time * 2.0f) * 8.0f;
        webSegment.applyForceToCenter(wind, 0, true);

        // Симуляция физики
        world.step(1/60f, 6, 2);

        // Очистка и отрисовка дебаг-линий Box2D
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        debugRenderer.render(world, camera.combined);
    }

    @Override
    public void dispose() {
        world.dispose();
        debugRenderer.dispose();
    }
}

