package com.github.br.libgx.jam37;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class Main implements ApplicationListener {

    // Ширина физического мира Box2D в метрах
    private static final float WORLD_WIDTH = 32f;

    // Фиксированные пропорции виртуального пиксельного экрана (16:9)
    private static final int VIRTUAL_WIDTH = 640;
    private static final int VIRTUAL_HEIGHT = 360;

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

    @Override
    public void create() {
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();
        shapeRenderer = new ShapeRenderer();
        spriteBatch = new SpriteBatch();

        // 1. Настраиваем единые пропорции для всего приложения (16:9)
        float aspectRatio = (float) VIRTUAL_HEIGHT / (float) VIRTUAL_WIDTH;
        float worldHeight = WORLD_WIDTH * aspectRatio;

        // 2. Основная камера и вьюпорт для вывода на экран монитора
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, worldHeight, camera);

        // Центрируем камеру
        // Сдвигаем основную камеру так, чтобы точка (0,0) была в левом нижнем углу
        camera.position.set(WORLD_WIDTH / 2f, worldHeight / 2f, 0);
        camera.update();

        // 3. Камера для FBO — она должна смотреть ТОЧНО ТУДА ЖЕ, куда и основная
        fboCamera = new OrthographicCamera();
        // Камеру FBO настраиваем ТОЧНО ТАК ЖЕ, чтобы картинки совпадали
        fboCamera.setToOrtho(false, WORLD_WIDTH, worldHeight);
        fboCamera.position.set(WORLD_WIDTH / 2f, worldHeight / 2f, 0);
        fboCamera.update();

        // 4. Создаем паутину ровно по центру рассчитанного мира
        Vector2 webCenter = new Vector2(WORLD_WIDTH / 2f, worldHeight / 2f);
        spiderWeb = new SpiderWeb(world, webCenter, 8f, 12, 8);

        // 5. Инициализируем FrameBuffer с отключенным сглаживанием
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, false);
        fbo.getColorBufferTexture().setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        time += delta;

        // Шаг физики
        world.step(1 / 60f, 6, 2);

        // Симуляция ветра
        float windForce = (float) Math.sin(time * 2.5f) * 2.0f;
        spiderWeb.applyWind(windForce, 0);

        // ====================================================
        // ЭТАП 1: Рисуем паутину внутри пиксельного FrameBuffer
        // ====================================================
        fbo.begin();

        // Чистый и надежный вызов OpenGL: сужаем экран до размеров текстуры FBO
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        // Очищаем буфер темно-серым цветом
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        fboCamera.update();
        shapeRenderer.setProjectionMatrix(fboCamera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 1f);
        spiderWeb.render(shapeRenderer);
        shapeRenderer.end();

        fbo.end();

        // ====================================================
        // ЭТАП 2: Растягиваем готовую пиксельную текстуру на экран
        // ====================================================

        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Принудительно возвращаем OpenGL в реальное разрешение окна монитора
        // Gdx.graphics.getBackBufferWidth() и Height берут честные пиксели вашего экрана
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

        // Очищаем реальный монитор черным цветом
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Применяем вьюпорт, чтобы он обновил матрицы камеры под новое окно
        viewport.apply();
        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);

        spriteBatch.begin();
        // Отрисовываем текстуру FBO, растягивая её на всю ширину и высоту вьюпорта в метрах
        spriteBatch.draw(
            fbo.getColorBufferTexture(),
            0, 0,
            viewport.getWorldWidth(), viewport.getWorldHeight(),
            0, 0,
            fbo.getWidth(), fbo.getHeight(),
            false, true // Флипаем по вертикали OpenGL текстуру
        );
        spriteBatch.end();

        // Теперь матрицы настроены верно, и дебаггер нарисует зеленые линии строго поверх пикселей!
        debugRenderer.render(world, camera.combined);
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
