package com.github.br.libgx.jam37;

import com.artemis.managers.TagManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.PrismaticRebindIntent;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.enemy.SpiderComponent;
import com.github.br.libgx.jam37.components.enemy.SpiderRenderer;
import com.github.br.libgx.jam37.components.player.CaterpillarRenderer;
import com.github.br.libgx.jam37.components.player.PlayerComponent;
import com.github.br.libgx.jam37.systems.PlayerInputSystem;
import com.github.br.libgx.jam37.systems.SpiderUpdateSystem;
import com.github.br.libgx.jam37.systems.WindSystem;
import com.github.br.libgx.jam37.systems.physics.PhysicsSystem;
import com.github.br.libgx.jam37.systems.physics.WebContactListener;
import com.github.br.libgx.jam37.systems.render.RenderSystem;

import static com.github.br.libgx.jam37.Constants.WORLD_WIDTH;

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
            .with(physicsSystem) // Делает world.step()
            .with(new WebContactListener())
            .with(new EntityFactory())

            // ЧЕТВЕРТЫЙ ЭТАП: Отрисовка (Забирает уже посчитанные на этом кадре координаты Box2D)
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

        SpiderWeb spiderWeb = createWeb(worldHeight);
        Body startSegmentBody = spiderWeb.getRadialStartSegments().first();
        Vector2 startPos = startSegmentBody.getPosition();

        EntityFactory entityFactory = artemisWorld.getSystem(EntityFactory.class);
        entityFactory.createPlayer(5, startPos, spiderWeb);

        Body spawnSegmentBody = spiderWeb.getAllSegments().get(12);
        entityFactory.createSpider(spawnSegmentBody.getPosition());
    }

    private SpiderWeb createWeb(float worldHeight) {
        World box2dWorld = artemisWorld.getSystem(PhysicsSystem.class).getBox2dWorld();

        Vector2 webCenter = new Vector2(WORLD_WIDTH / 2f, worldHeight / 2f);
        SpiderWeb spiderWeb = new SpiderWeb(box2dWorld, webCenter, 8f, 12, 8, 3);

        int webEntityId = artemisWorld.create();
        RenderComponent webRender = artemisWorld.getMapper(RenderComponent.class).create(webEntityId);
        webRender.layer = 0;            // Паутина строго под жуком
        webRender.renderer = spiderWeb; // Сам объект паутины выступает в роли отрисовщика

        artemisWorld.getSystem(TagManager.class).register(Tags.WEB, webEntityId);

        return spiderWeb;
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

