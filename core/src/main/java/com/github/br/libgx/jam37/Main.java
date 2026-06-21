package com.github.br.libgx.jam37;

import com.artemis.managers.TagManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
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
            // ФАБРИКИ И СИСТЕМЫ УПРАВЛЕНИЯ
            .with(new EntityFactory())
            .with(new LevelManagementSystem(worldHeight))
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
