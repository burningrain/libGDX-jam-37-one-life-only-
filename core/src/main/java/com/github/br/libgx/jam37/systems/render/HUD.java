package com.github.br.libgx.jam37.systems.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;

public class HUD {

    private float virtualWidth;
    private float virtualHeight;

    private BitmapFont pixelFont;
    private StringBuilder score = new StringBuilder("SCORE: ");
    private final int prefixLength = score.length();

    public HUD(float virtualWidth, float virtualHeight) {
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;

        // Инициализируем стандартный шрифт libGDX (он размыт, ниже напишу, как загрузить пиксельный)
        this.pixelFont = new BitmapFont();
        // ВАЖНО: отключаем сглаживание, чтобы пиксели были острыми
        this.pixelFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        // Масштабируем шрифт под маленькое разрешение FBO (в метрах/пикселях виртуального мира)
        this.pixelFont.getData().setScale(1f); // Подберите масштаб под вашу метрику мира
    }

    public void render(SpriteBatch spriteBatch, GameParamsComponent gameParamsComponent) {
        // 2. ОТРИСОВКА HUD (Внутри FBO со стандартным шейдером батча!)
        spriteBatch.setShader(null); // Явно сбрасываем на дефолтный шейдер для отрисовки текста
        // ИСПРАВЛЕНО: переключаем матрицу батча на пиксельную сетку FBO (от 0 до VIRTUAL_WIDTH/HEIGHT)
        spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, virtualWidth, virtualHeight);
        spriteBatch.begin();

        // Теперь координаты задаются в ПИКСЕЛЯХ внутри вашего FBO (960 x 540)
        float hudX = 170f;                       // 20 пикселей от левого края
        float hudY = virtualHeight - 50f;       // 20 пикселей от верхнего края

        score.setLength(prefixLength); // Это мгновенно "стирает" старые цифры без выделения новой памяти (Garbage Collector отдыхает)
        int currentPoints = gameParamsComponent.currentPoints;
        score.append(currentPoints);

        pixelFont.draw(spriteBatch, score, hudX, hudY);
        spriteBatch.end();
    }

    public void dispose() {
        pixelFont.dispose();
    }

}
