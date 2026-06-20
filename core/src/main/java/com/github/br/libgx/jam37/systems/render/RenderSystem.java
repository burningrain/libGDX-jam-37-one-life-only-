package com.github.br.libgx.jam37.systems.render;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.br.libgx.jam37.components.RenderComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class RenderSystem extends BaseSystem {

    private final int virtualWidth, virtualHeight;
    private ComponentMapper<RenderComponent> renderMapper;

    private FrameBuffer fbo;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera, fboCamera;
    private Viewport viewport;

    private final ArrayList<Integer> sortedEntities = new ArrayList<>();
    private final Comparator<Integer> layerComparator = (e1, e2) ->
        Integer.compare(renderMapper.get(e1).layer, renderMapper.get(e2).layer);

    public RenderSystem(
        Viewport viewport,
        OrthographicCamera camera,
        int virtualWidth,
        int virtualHeight,
        float worldWidth,
        float worldHeight
    ) {
        this.viewport = viewport;
        this.camera = camera;
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();

        // 1. ИСПРАВЛЕНО: Для 2D-игры настраиваем камеру через проверенный setToOrtho(false)
        // Никаких осей Z и lookAt, которые ломают ShapeRenderer.
        this.fboCamera = new OrthographicCamera();
        this.fboCamera.setToOrtho(false, worldWidth, worldHeight);

        // 2. ИСПРАВЛЕНО: Центрируем точно так же, как основную камеру в Main
        this.fboCamera.position.set(worldWidth / 2f, worldHeight / 2f, 0);
        this.fboCamera.update();

        // Инициализация буфера
        this.fbo = new FrameBuffer(Pixmap.Format.RGBA8888, virtualWidth, virtualHeight, false);
        this.fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    @Override
    protected void processSystem() {
        // Сортировка слоев через ECS
        sortedEntities.clear();
        IntBag actives = world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(RenderComponent.class)).getEntities();
        for (int i = 0; i < actives.size(); i++) sortedEntities.add(actives.get(i));
        sortedEntities.sort(layerComparator);

        // ====================================================
        // ШАГ 1: Отрисовка во внутренний холст FBO (в метрах)
        // ====================================================
        fbo.begin();

        // Фиксируем пиксельную сетку под размер текстуры буфера
        Gdx.gl.glViewport(0, 0, virtualWidth, virtualHeight);
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        fboCamera.update();
        shapeRenderer.setProjectionMatrix(fboCamera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int entityId : sortedEntities) {
            RenderComponent rComp = renderMapper.get(entityId);
            if (rComp.renderer != null) {
                rComp.renderer.draw(shapeRenderer);
            }
        }
        shapeRenderer.end();
        fbo.end();

        // ====================================================
        // ШАГ 2: Апскейл FBO на физический экран через основную камеру
        // ====================================================

        // Настраиваем glViewport видеокарты под размеры окна с учетом черных полос FitViewport
        viewport.apply();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // ИСПРАВЛЕНО: Используем camera.combined игровой камеры, так как FitViewport
        // завязан именно на её проекционную матрицу.
        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);

        spriteBatch.begin();
        // ИСПРАВЛЕНО: Рисуем текстуру, растягивая её строго на виртуальные МЕТРЫ вьюпорта.
        // FitViewport сам отмасштабирует эти метры до физических пикселей монитора игрока.
        spriteBatch.draw(
            fbo.getColorBufferTexture(),
            0, 0,
            viewport.getWorldWidth(), viewport.getWorldHeight(),
            0, 0,
            fbo.getWidth(), fbo.getHeight(),
            false, true // Переворот по Y обязателен для текстур FBO
        );
        spriteBatch.end();
    }

    @Override
    protected void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        fbo.dispose();
    }
}
