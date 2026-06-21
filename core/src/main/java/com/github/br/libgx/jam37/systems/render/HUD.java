package com.github.br.libgx.jam37.systems.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.github.br.libgx.jam37.components.enemy.GameParamsComponent;

public class HUD {

    public static final String GAME_OVER_TEXT = "GAME OVER";
    private float virtualWidth;
    private float virtualHeight;

    private BitmapFont pixelFont;
    private StringBuilder score = new StringBuilder("SCORE: ");
    private final int prefixLength = score.length();

    // Поля для мигающего текста и центрирования
    private float stateTime = 0f;
    private final GlyphLayout layout = new GlyphLayout();
    private final String restartText = "PRESS SPACE";

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
        // Накапливаем время для эффекта мигания
        stateTime += Gdx.graphics.getDeltaTime();

        // ОТРИСОВКА HUD (Внутри FBO со стандартным шейдером батча!)
        spriteBatch.setShader(null); // Явно сбрасываем на дефолтный шейдер для отрисовки текста
        // переключаем матрицу батча на пиксельную сетку FBO (от 0 до VIRTUAL_WIDTH/HEIGHT)
        spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, virtualWidth, virtualHeight);

        spriteBatch.begin();

        // Теперь координаты задаются в ПИКСЕЛЯХ внутри вашего FBO (960 x 540)
        float hudX = 170f;                       // 20 пикселей от левого края
        float hudY = virtualHeight - 50f;       // 20 пикселей от верхнего края

        score.setLength(prefixLength); // Это мгновенно "стирает" старые цифры без выделения новой памяти (Garbage Collector отдыхает)
        int currentPoints = gameParamsComponent.currentPoints;
        score.append(currentPoints);

        pixelFont.draw(spriteBatch, score, hudX, hudY);

        if (gameParamsComponent.isGameOver) {
            pixelFont.getData().setScale(8f);

            layout.setText(pixelFont, GAME_OVER_TEXT);
            float x = (virtualWidth - layout.width) / 2f;
            pixelFont.draw(spriteBatch, GAME_OVER_TEXT, x, virtualHeight / 1.7f);
            pixelFont.getData().setScale(1f);

            // ВАЖНО: Делим stateTime на периоды.
            // Остаток от деления % 1.0f дает значения от 0 до 1.
            // Если значение меньше 0.5 — текст виден, если больше — скрыт. Получаем мигание 0.5 сек.
            if (stateTime % 1.0f < 0.5f) {
                pixelFont.getData().setScale(2f); // Масштаб для надписи перезапуска

                // Чтобы текст "PRESS SPACE..." был идеально по центру экрана:
                // Сначала рассчитываем его ширину в пикселях с текущим масштабом шрифта
                layout.setText(pixelFont, restartText);
                float restartX = (virtualWidth - layout.width) / 2f;
                float restartY = (virtualHeight / 1.7f) - 100f; // Смещаем на 100 пикселей ниже GAME OVER

                pixelFont.draw(spriteBatch, restartText, restartX, restartY);
                pixelFont.getData().setScale(1f);
            }
        }

        spriteBatch.end();
    }

    public void dispose() {
        pixelFont.dispose();
    }

}
