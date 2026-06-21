package com.github.br.libgx.jam37;

import com.artemis.managers.TagManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;
import com.github.br.libgx.jam37.systems.*;
import com.github.br.libgx.jam37.systems.physics.PhysicsSystem;
import com.github.br.libgx.jam37.systems.physics.contact.WebContactListener;
import com.github.br.libgx.jam37.systems.render.RenderSystem;

public class Main implements ApplicationListener {

    private OrthographicCamera camera;
    private Viewport viewport;

    private com.artemis.World artemisWorld;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        float aspectRatio = (float) Constants.VIRTUAL_HEIGHT / (float) Constants.VIRTUAL_WIDTH;
        float worldHeight = Constants.WORLD_WIDTH * aspectRatio;
        viewport = new FitViewport(Constants.WORLD_WIDTH, worldHeight, camera);
        camera.position.set(Constants.WORLD_WIDTH / 2f, worldHeight / 2f, 0);
        camera.update();

        PhysicsSystem physicsSystem = new PhysicsSystem();

        com.artemis.WorldConfiguration config = new com.artemis.WorldConfigurationBuilder()
            .with(new TagManager())

            // ПЕРВЫЙ ЭТАП: Сбор ввода игрока и логика ИИ (Определяем намерения)
            .with(new PlayerInputSystem()) // Считывает кнопки, ставит скорость мотора головы
            .with(new SpiderUpdateSystem()) // Считает ИИ паука, ставит скорость prosoma и двигает лапки

            // ВТОРОЙ ЭТАП: Внешние физические силы (Прикладываем до шага Box2D)
            .with(new WindSystem()) // Колышет нити, вызывая applyForce

            // ТРЕТИЙ ЭТАП: Симуляция физического мира
            .with(new WebContactListener())
            .with(physicsSystem) // Делает world.step()
            .with(new FlySpawnerSystem())
            // фабрики
            .with(new EntityFactory())
            // ЧЕТВЕРТЫЙ ЭТАП: Отрисовка (Забирает уже посчитанные на этом кадре координаты Box2D)
            .with(new FlyAnimationSystem())
            .with(new RenderSystem(
                physicsSystem.getBox2dWorld(),
                viewport,
                camera,
                Constants.VIRTUAL_WIDTH,
                Constants.VIRTUAL_HEIGHT,
                Constants.WORLD_WIDTH,
                worldHeight
            ).setDebugBox2d(false))
            .build();
        artemisWorld = new com.artemis.World(config);

        EntityFactory entityFactory = artemisWorld.getSystem(EntityFactory.class);

        GameParamsComponent gameParams = entityFactory.createGameParams();

        SpiderWeb spiderWeb = entityFactory.createWeb(worldHeight);
        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();
        Vector2 startPos = startSegmentBody.getPosition();

        entityFactory.createPlayer(5, startPos, spiderWeb);

        Body spawnSegmentBody = spiderWeb.getAllSegments().get(12);
        entityFactory.createSpider(spawnSegmentBody.getPosition());
    }



    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        artemisWorld.setDelta(delta);
        artemisWorld.process();
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
        artemisWorld.dispose();
    }

}

