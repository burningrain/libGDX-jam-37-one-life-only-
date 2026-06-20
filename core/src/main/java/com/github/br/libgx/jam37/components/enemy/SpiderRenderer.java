package com.github.br.libgx.jam37.components.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.github.br.libgx.jam37.systems.render.Renderable;

public class SpiderRenderer implements Renderable {

    private final SpiderComponent spider;
    private final Color chitinColor = Color.YELLOW;
    private final Color jointColor = Color.YELLOW;

    private final Vector2 legRoot = new Vector2();
    private final Vector2 joint1 = new Vector2();
    private final Vector2 joint2 = new Vector2();

    public SpiderRenderer(SpiderComponent spider) {
        this.spider = spider;
    }

    @Override
    public void draw(ShapeRenderer shapeRenderer) {
        if (spider.prosoma == null || spider.opisthosoma == null) return;

        Vector2 prosomaPos = spider.prosoma.getPosition();
        Vector2 opisthoPos = spider.opisthosoma.getPosition();
        float bodyAngleDeg = (float) Math.toDegrees(spider.prosoma.getAngle());
        float prosomaRadius = 0.35f;

        // ====================================================
        // 1. ОТРИСОВКА 8 СКЕЛЕТНЫХ ЛАП (ПРЯМОЙ РАСЧЕТ ИЗЛОМОВ)
        // ====================================================
        shapeRenderer.set(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(jointColor);
        com.badlogic.gdx.Gdx.gl.glLineWidth(2.5f);

        for (int i = 0; i < 8; i++) {
            int configIdx = i % 4;

            // Читаем длины из конфига
            float fLen = spider.femurLengthConfig[configIdx];
            float tLen = spider.tibiaLengthConfig[configIdx];

            // ИСПРАВЛЕНО: Читаем ЖИВЫЕ, ЛЕРПЯЩИЕСЯ углы из системы анимации!
            float fAng = spider.currentFemurAngles[i];
            float tAng = spider.currentTibiaAngles[i];

            // Накапливаем мировые углы относительно поворота тела паука
            float currentWorldFemurAngle = bodyAngleDeg + fAng;
            float currentWorldTibiaAngle = currentWorldFemurAngle + tAng;

            float fRad = (float) Math.toRadians(currentWorldFemurAngle);
            float tRad = (float) Math.toRadians(currentWorldTibiaAngle);

            // Корень лапы на краю диска головогруди
            legRoot.set((float)Math.cos(fRad), (float)Math.sin(fRad)).scl(prosomaRadius).add(prosomaPos);

            // СУСТАВ 1: Колено бедра (Femur -> Tibia) — теперь оно плавно колышется вверх-вниз!
            joint1.set(legRoot).add((float)Math.cos(fRad) * fLen, (float)Math.sin(fRad) * fLen);

            // СУСТАВ 2: Колено голени (Tibia -> Tarsus)
            joint2.set(joint1).add((float)Math.cos(tRad) * tLen, (float)Math.sin(tRad) * tLen);

            // Точка касания паутины
            Vector2 currentFoot = spider.currentFootPos[i];

            // Рисуем 3 живых сочленения
            shapeRenderer.line(legRoot.x, legRoot.y, joint1.x, joint1.y);
            shapeRenderer.line(joint1.x, joint1.y, joint2.x, joint2.y);
            shapeRenderer.line(joint2.x, joint2.y, currentFoot.x, currentFoot.y);
        }
        com.badlogic.gdx.Gdx.gl.glLineWidth(1f);

        // ====================================================
        // 2. ОТРИСОВКА ТЕЛА
        // ====================================================
        shapeRenderer.set(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(chitinColor);
        shapeRenderer.circle(opisthoPos.x, opisthoPos.y, 0.55f, 14);
        shapeRenderer.circle(prosomaPos.x, prosomaPos.y, prosomaRadius, 12);

        if (spider.cheliceraLeft != null) shapeRenderer.circle(spider.cheliceraLeft.getPosition().x, spider.cheliceraLeft.getPosition().y, 0.11f, 6);
        if (spider.cheliceraRight != null) shapeRenderer.circle(spider.cheliceraRight.getPosition().x, spider.cheliceraRight.getPosition().y, 0.11f, 6);

        shapeRenderer.setColor(Color.RED);
        float radAngle = (float) Math.toRadians(bodyAngleDeg);
        Vector2 eyeL = new Vector2(0.22f, 0.1f).setAngleRad(radAngle + 0.25f).add(prosomaPos);
        Vector2 eyeR = new Vector2(0.22f, -0.1f).setAngleRad(radAngle - 0.25f).add(prosomaPos);
        shapeRenderer.circle(eyeL.x, eyeL.y, 0.04f, 6);
        shapeRenderer.circle(eyeR.x, eyeR.y, 0.04f, 6);
    }
}
