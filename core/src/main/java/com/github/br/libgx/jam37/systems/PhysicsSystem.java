package com.github.br.libgx.jam37.systems;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.box2d.World;

public class PhysicsSystem extends BaseSystem {

    private final World world;

    // Фиксированный шаг физики (60 кадров в секунду)
    private static final float TIME_STEP = 1 / 60f;
    private static final int VELOCITY_ITERATIONS = 6;
    private static final int POSITION_ITERATIONS = 2;

    // Накопитель времени для стабильной симуляции (фикс для лагов дельты)
    private float accumulator = 0;

    public PhysicsSystem(World world) {
        this.world = world;
    }

    @Override
    protected void processSystem() {
        float delta = Gdx.graphics.getDeltaTime();
        // Классический алгоритм фиксированного шага Box2D с накоплением времени.
        // Он гарантирует, что даже если игра лагнет, физика не "взорвется" и не пройдет сквозь стены.
        accumulator += delta;
        while (accumulator >= TIME_STEP) {
            world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            accumulator -= TIME_STEP;
        }
    }

    @Override
    protected void dispose() {
        // Чистим память за собой при уничтожении мира Artemis
        world.dispose();
    }
}
