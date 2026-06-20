package com.github.br.libgx.jam37.systems;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.BaseEntitySystem;
import com.artemis.managers.TagManager;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.github.br.libgx.jam37.SpiderWeb;
import com.github.br.libgx.jam37.Tags;
import com.github.br.libgx.jam37.components.PhysicsComponent;
import com.github.br.libgx.jam37.components.RenderComponent;
import com.github.br.libgx.jam37.components.enemy.SpiderComponent;

public class SpiderUpdateSystem extends IteratingSystem {

    private ComponentMapper<SpiderComponent> mSpider;
    private ComponentMapper<PhysicsComponent> mPhysics;
    private ComponentMapper<RenderComponent> mRender;

    private final Vector2 targetVelocity = new Vector2();

    // Кэш для поиска ближайшей нити
    private SpiderWeb cachedSpiderWeb;
    private final Vector2 segmentStart = new Vector2();
    private final Vector2 segmentEnd = new Vector2();
    private final Vector2 closestPoint = new Vector2();

    public SpiderUpdateSystem() {
        super(Aspect.all(SpiderComponent.class, PhysicsComponent.class));
    }

    @Override
    protected void process(int entityId) {
        SpiderComponent spider = mSpider.get(entityId);
        PhysicsComponent physics = mPhysics.get(entityId);

        Body prosomaBody = spider.prosoma;
        if (prosomaBody == null) return;

        Vector2 prosomaPos = prosomaBody.getPosition();
        float bodyAngle = (float) Math.toDegrees(prosomaBody.getAngle()); // Переводим в градусы для удобства сложения
        float dt = world.getDelta();

        if (cachedSpiderWeb == null) {
            TagManager tagManager = world.getSystem(TagManager.class);
            if (tagManager.isRegistered(Tags.WEB)) {
                int webId = tagManager.getEntityId(Tags.WEB);
                RenderComponent webRender = mRender.get(webId);
                if (webRender != null) cachedSpiderWeb = (SpiderWeb) webRender.renderer;
            }
        }

        // ====================================================
        // 1. ИИ ДВИЖЕНИЯ
        // ====================================================
        TagManager tagManager = world.getSystem(TagManager.class);
        boolean isPlayerAlive = false;
        if (tagManager.isRegistered(Tags.PLAYER)) {
            int playerId = tagManager.getEntityId(Tags.PLAYER);
            if (playerId != -1) {
                PhysicsComponent playerPhys = mPhysics.get(playerId);
                if (playerPhys != null && playerPhys.body != null) {
                    Vector2 playerPos = playerPhys.body.getPosition();
                    isPlayerAlive = true;
                    targetVelocity.set(playerPos).sub(prosomaPos);
                    if (targetVelocity.len2() > 0.01f) {
                        targetVelocity.nor();
                        float targetAngle = (float) Math.atan2(targetVelocity.y, targetVelocity.x);
                        prosomaBody.setTransform(prosomaPos.x, prosomaPos.y, targetAngle);
                        prosomaBody.setLinearVelocity(targetVelocity.scl(spider.speed));
                    } else {
                        prosomaBody.setLinearVelocity(0f, 0f);
                    }
                }
            }
        }
        if (!isPlayerAlive) {
            prosomaBody.setLinearVelocity(0f, 0f);
            prosomaBody.setTransform(prosomaPos.x, prosomaPos.y, 0f);
            bodyAngle = 0f; // В покое смотрим строго в 0°
        }

        // ====================================================
        // 2. РАСЧЕТ ИДЕАЛЬНОЙ СТУПНИ ИЗ ПРЯМОЙ КИНЕМАТИКИ СУСТАВОВ
        // ====================================================
//        for (int i = 0; i < 8; i++) {
//            spider.legTimers[i] += dt;
//            int configIdx = i % 4;
//            boolean isLeft = (i < 4);
//
//            // Читаем длины костей из конфига
//            float fLen = spider.femurLengthConfig[configIdx];
//            float tLen = spider.tibiaLengthConfig[configIdx];
//            float tarsLen = spider.tarsusLengthConfig[configIdx];
//
//            // Читаем и выстраиваем углы суставов по цепочке
//            float fAng = spider.femurAngleConfig[configIdx];
//            float tAng = spider.tibiaAngleConfig[configIdx];
//            float tarsAng = spider.tarsusAngleConfig[configIdx];
//
//            // Если нога левая — зеркально инвертируем все знаки углов наружу/вовнутри
//            if (isLeft) {
//                fAng = -fAng;
//                tAng = -tAng;
//                tarsAng = -tarsAng;
//            }
//
//            // Абсолютные мировые углы каждого сустава в пространстве
//            float currentWorldFemurAngle = bodyAngle + fAng;
//            float currentWorldTibiaAngle = currentWorldFemurAngle + tAng;
//            float currentWorldTarsusAngle = currentWorldTibiaAngle + tarsAng;
//
//            // Считаем финальную точку ступни, складывая сдвиги трех члеников
//            float fRad = (float) Math.toRadians(currentWorldFemurAngle);
//            float tRad = (float) Math.toRadians(currentWorldTibiaAngle);
//            float tarsRad = (float) Math.toRadians(currentWorldTarsusAngle);
//
//            float footX = prosomaPos.x + (float)Math.cos(fRad)*fLen + (float)Math.cos(tRad)*tLen + (float)Math.cos(tarsRad)*tarsLen;
//            float footY = prosomaPos.y + (float)Math.sin(fRad)*fLen + (float)Math.sin(tRad)*tLen + (float)Math.sin(tarsRad)*tarsLen;
//
//            spider.targetFootPos[i].set(footX, footY);
//
//            // Фича магнитного прилипания к нитям паутины
//            if (cachedSpiderWeb != null) {
//                snapToNearestWebSegment(spider.targetFootPos[i], cachedSpiderWeb.getAllSegments());
//            }
//
//            // Логика Лерп-шага лап
//            float distanceToTarget = spider.currentFootPos[i].dst(spider.targetFootPos[i]);
//            boolean isLegTurnToMove = (i % 2 == 0)
//                ? (Math.sin(spider.legTimers[i] * 16f) > 0)
//                : (Math.sin(spider.legTimers[i] * 16f) < 0);
//
//            if (distanceToTarget > 0.4f && isLegTurnToMove) {
//                spider.currentFootPos[i].lerp(spider.targetFootPos[i], 0.35f);
//            } else {
//                spider.currentFootPos[i].lerp(spider.currentFootPos[i], 0.05f);
//            }
//        }
    }

    private void snapToNearestWebSegment(Vector2 targetPos, Array<Body> webSegments) {
        float minDistance = Float.MAX_VALUE;
        float bestX = targetPos.x; float bestY = targetPos.y;

        for (int i = 0; i < webSegments.size; i++) {
            Body segment = webSegments.get(i);
            float angle = segment.getAngle();
            float halfLen = 0.5f;
            float cos = (float) Math.cos(angle) * halfLen;
            float sin = (float) Math.sin(angle) * halfLen;

            segmentStart.set(segment.getPosition().x - cos, segment.getPosition().y - sin);
            segmentEnd.set(segment.getPosition().x + cos, segment.getPosition().y + sin);

            Intersector.nearestSegmentPoint(segmentStart, segmentEnd, targetPos, closestPoint);
            float dist = targetPos.dst2(closestPoint);
            if (dist < minDistance) {
                minDistance = dist; bestX = closestPoint.x; bestY = closestPoint.y;
            }
        }
        if (minDistance < 0.64f) { targetPos.set(bestX, bestY); }
    }
}
