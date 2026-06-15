package com.github.br.libgx.jam37;

import com.artemis.managers.TagManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.PlayerComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;
import com.github.br.libgx.jam37.systems.PhysicsSystem;
import com.github.br.libgx.jam37.systems.PlayerInputSystem;

public class Main implements ApplicationListener {

    private static final float WORLD_WIDTH = 32f;
    private static final int VIRTUAL_WIDTH = 960;
    private static final int VIRTUAL_HEIGHT = 540;

    private FrameBuffer fbo;
    private SpriteBatch spriteBatch;
    private OrthographicCamera fboCamera;

    private World world;
    private Box2DDebugRenderer debugRenderer;
    private ShapeRenderer shapeRenderer;
    private float time = 0;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpiderWeb spiderWeb;

    // Переменные плавного физического игрока
    private Body playerBody;

    private com.artemis.World artemisWorld;

    @Override
    public void create() {
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();
        shapeRenderer = new ShapeRenderer();
        spriteBatch = new SpriteBatch();

        float aspectRatio = (float) VIRTUAL_HEIGHT / (float) VIRTUAL_WIDTH;
        float worldHeight = WORLD_WIDTH * aspectRatio;

        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, worldHeight, camera);
        camera.position.set(WORLD_WIDTH / 2f, worldHeight / 2f, 0);
        camera.update();

        fboCamera = new OrthographicCamera();
        fboCamera.setToOrtho(false, WORLD_WIDTH, worldHeight);
        fboCamera.position.set(WORLD_WIDTH / 2f, worldHeight / 2f, 0);
        fboCamera.update();

        // 1. Создаем паутину
        Vector2 webCenter = new Vector2(WORLD_WIDTH / 2f, worldHeight / 2f);
        spiderWeb = new SpiderWeb(world, webCenter, 8f, 12, 8, 3);

        // 2. Инициализируем FrameBuffer
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, false);
        fbo.getColorBufferTexture().setFilter(
            com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
            com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
        );

        com.artemis.WorldConfiguration config = new com.artemis.WorldConfigurationBuilder()
            .with(new TagManager())
            .with(new PlayerInputSystem())
            .with(new PhysicsSystem(world))
            .with(new WebContactListener())
            .build();
        artemisWorld = new com.artemis.World(config);


        // ====================================================
        // 5. СОЗДАЕМ СУЩНОСТЬ ИГРОКА В ECS
        // ====================================================
        createPlayer();
    }

    private void createPlayer() {
        BodyDef pDef = new BodyDef();
        pDef.type = BodyDef.BodyType.DynamicBody; // Честное динамическое тело

        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();
        pDef.position.set(startSegmentBody.getPosition());
        playerBody = world.createBody(pDef);

        CircleShape playerShape = new CircleShape();
        playerShape.setRadius(0.25f);
        FixtureDef pFixture = new FixtureDef();
        pFixture.shape = playerShape;
        pFixture.density = 0.5f; // Легкий как пушинка
        pFixture.filter.groupIndex = -1; // Отключаем коллизии со звеньями
        pFixture.isSensor = true;
        playerBody.createFixture(pFixture);
        playerShape.dispose();

        int entityId = artemisWorld.create();
        PlayerComponent playerComp = artemisWorld.getMapper(PlayerComponent.class).create(entityId);
        PhysicsComponent physicsComp = artemisWorld.getMapper(PhysicsComponent.class).create(entityId);

        // Передаем физическое тело самого жука (Кинематическое)
        physicsComp.body = playerBody;

        // НАМЕРТВО ЗАПИСЫВАЕМ СТАРТОВОЕ ТЕЛО В КОМПОНЕНТ (Жук поймет, на каких он рельсах!)
        playerComp.currentSegmentBody = startSegmentBody;
        playerComp.entityId = entityId;

        // Создаем стартовое намерение, чтобы PhysicsSystem сама собрала первый сустав
        PrismaticRebindIntent startIntent = artemisWorld.getMapper(PrismaticRebindIntent.class).create(entityId);
        startIntent.bodyToBind = playerBody;
        startIntent.targetSegmentBody = startSegmentBody;

        artemisWorld.getSystem(TagManager.class).register("PLAYER", entityId);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        time += delta;

        // ====================================================
        // ШАГ 1: ВСЕ РАСЧЕТЫ И СИМУЛЯЦИЯ (До какой-либо отрисовки)
        // ====================================================

        // Симуляция ветра (применяем силы до шага физики)
        float windForce = (float) Math.sin(time * 2.5f) * 1.4f;
        spiderWeb.applyWind(windForce, 0);

        // Запускаем Artemis ECS (включая PlayerInputSystem, которая считает скорость,
        // и PhysicsSystem, которая делает world.step())
        artemisWorld.setDelta(delta);
        artemisWorld.process();

        // ====================================================
        // ШАГ 2: ЭТАП 1 — Рисуем ВСЁ внутри пиксельного FrameBuffer
        // ====================================================
        fbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Синхронизируем и обновляем пиксельную камеру по результатам прошедшего шага физики
        fboCamera.update();
        shapeRenderer.setProjectionMatrix(fboCamera.combined);

        // Рисуем паутину
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 1f);
        spiderWeb.render(shapeRenderer);

        // Рисуем ОДНОГО ЕДИНСТВЕННОГО пиксельного жука
        shapeRenderer.setColor(1.0f, 1.0f, 1.0f, 1f);
        Vector2 pPos = playerBody.getPosition();
        shapeRenderer.circle(pPos.x, pPos.y, 0.3f, 8);

        shapeRenderer.end();

        fbo.end();

        // ====================================================
        // ШАГ 3: ЭТАП 2 — Выводим готовую пиксельную картинку на экран
        // ====================================================
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        viewport.apply();
        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);

        spriteBatch.begin();
        spriteBatch.draw(
            fbo.getColorBufferTexture(),
            0, 0,
            viewport.getWorldWidth(), viewport.getWorldHeight(),
            0, 0,
            fbo.getWidth(), fbo.getHeight(),
            false, true // Переворачиваем текстуру FBO по вертикали
        );
        spriteBatch.end();

        //debugRenderer.render(world, camera.combined);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        world.dispose();
        debugRenderer.dispose();
        shapeRenderer.dispose();
        spriteBatch.dispose();
        fbo.dispose();
    }

}

