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

        // ====================================================
        // ОТРИСОВКА СТАРТОВОГО ЭКРАНА
        // ====================================================
        if (gameParamsComponent.isStartScreen) {
            pixelFont.getData().setScale(6f);
            String titleText = "WEB  OF  DEATH";
            layout.setText(pixelFont, titleText);
            float titleX = (virtualWidth - layout.width) / 2f;
            float titleY = virtualHeight / 1.7f - 10f;
            pixelFont.draw(spriteBatch, titleText, titleX, titleY);
            pixelFont.getData().setScale(1f);

            // 2. Рисуем мигающую надпись PRESS SPACE TO PLAY чуть ниже
            if (stateTime % 1.0f < 0.5f) {
                pixelFont.getData().setScale(2f);
                String playText = restartText;
                layout.setText(pixelFont, playText);
                float playX = (virtualWidth - layout.width) / 2f;
                float playY = titleY - 100f; // Смещаем ниже названия игры
                pixelFont.draw(spriteBatch, playText, playX, playY);
                pixelFont.getData().setScale(1f);
            }
        }

        if (gameParamsComponent.isCountingDown) {
            pixelFont.getData().setScale(10f); // Делаем цифры ОЧЕНЬ крупными и пиксельными

            String text;
            int seconds = (int) gameParamsComponent.startTimer;

            if (seconds >= 1) {
                text = String.valueOf(seconds); // На экране будет "3", "2", "1"
            } else {
                text = "GO!"; // Когда таймер меньше 1 секунды — кричим "GO!"
            }

            // Центрируем текст ровно посередине экрана FBO
            layout.setText(pixelFont, text);
            float x = (virtualWidth - layout.width) / 2f;
            float y = (virtualHeight + layout.height) / 2f; // Идеальный центр с учетом высоты шрифта

            pixelFont.draw(spriteBatch, text, x, y);
            pixelFont.getData().setScale(1f); // Сбрасываем масштаб обратно
        }

        spriteBatch.end();
    }

    public void dispose() {
        pixelFont.dispose();
    }

}
