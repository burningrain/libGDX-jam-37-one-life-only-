package com.github.br.libgx.jam37.systems.render;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.br.libgx.jam37.EntityFactory;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;

import java.util.ArrayList;
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

    // Вводим дебажный рендерер Box2D
    private Box2DDebugRenderer debugRenderer;
    private final World box2dWorld;
    private boolean isDebugBox2d = false;

    private ShaderProgram crtShader;

    private final HUD hud;

    public RenderSystem(
        World box2dWorld,
        Viewport viewport,
        OrthographicCamera camera,
        int virtualWidth,
        int virtualHeight,
        float worldWidth,
        float worldHeight
    ) {
        this.box2dWorld = box2dWorld;
        this.viewport = viewport;
        this.camera = camera;
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;
        this.shapeRenderer = new ShapeRenderer();
        this.shapeRenderer.setAutoShapeType(true);

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


        // Создаем дебажный рендерер при инициализации системы
        this.debugRenderer = new Box2DDebugRenderer();

        // Настраиваем дебажные флаги (можно отключать ненужное)
        this.debugRenderer.setDrawBodies(true);   // Контуры тел
        this.debugRenderer.setDrawJoints(true);   // Суставы (отлично покажет рельсы призматических джоинтов)
        this.debugRenderer.setDrawAABBs(false);   // Хитбоксы оптимизации (обычно не нужны)
        this.debugRenderer.setDrawInactiveBodies(false);
        this.debugRenderer.setDrawVelocities(false);

        // Инициализация шейдера
        ShaderProgram.pedantic = true; // Чтобы не падать, если какие-то uniform не используются
        crtShader = new ShaderProgram(
            Gdx.files.internal("shaders/crt.vert"),
            Gdx.files.internal("shaders/crt.frag")
        );

        if (!crtShader.isCompiled()) {
            Gdx.app.error("Shader Error", crtShader.getLog());
        }

        spriteBatch.setShader(crtShader);

        hud = new HUD(virtualWidth, virtualHeight);
    }

    @Override
    protected void processSystem() {
        if(isDebugBox2d) {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            viewport.apply();
            camera.update();
            debugRenderer.render(box2dWorld, camera.combined);
            return;
        }

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

        // 1. Отрисовка геометрии мира
        shapeRenderer.setProjectionMatrix(fboCamera.combined);
        for (int entityId : sortedEntities) {
            RenderComponent rComp = renderMapper.get(entityId);
            if (rComp.renderer != null) {
                rComp.renderer.draw(shapeRenderer);
            }
        }
        shapeRenderer.end();

        // 2. ОТРИСОВКА HUD (Внутри FBO со стандартным шейдером батча!)
        GameParamsComponent gameParamsComponent = getWorld().getSystem(EntityFactory.class).getGameParamsComponent();
        hud.render(spriteBatch, gameParamsComponent);

        fbo.end();

        // ====================================================
        // ШАГ 2: Апскейл FBO на физический экран через основную камеру
        // ====================================================
        viewport.apply();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);

        // УСТАНАВЛИВАЕМ ШЕЙДЕР CRT ТОЛЬКО ЗДЕСЬ
        spriteBatch.setShader(crtShader);
        spriteBatch.begin();

        crtShader.setUniformf("u_resolution", virtualWidth, virtualHeight);
        // Добавьте эту строку, если обновили фрагментный шейдер под анимацию мерцания/шума:
        // crtShader.setUniformf("u_time", time);

        spriteBatch.draw(
            fbo.getColorBufferTexture(),
            0, 0,
            viewport.getWorldWidth(), viewport.getWorldHeight(),
            0, 0,
            fbo.getWidth(), fbo.getHeight(),
            false, true
        );
        spriteBatch.end();

        // Сбрасываем шейдер в конце, чтобы не ломать другие системы или дебаг-рендер
        spriteBatch.setShader(null);
    }


    @Override
    protected void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        fbo.dispose();

        debugRenderer.dispose();

        if (hud != null) hud.dispose();
        if (crtShader != null) crtShader.dispose();
    }

    public boolean isDebugBox2d() {
        return isDebugBox2d;
    }

    public RenderSystem setDebugBox2d(boolean debugBox2d) {
        isDebugBox2d = debugBox2d;
        return this;
    }

}
