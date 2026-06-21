package com.github.br.libgx.jam37.systems;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.github.br.libgx.jam37.components.enemy.FlyComponent;

public class FlyAnimationSystem extends BaseEntitySystem {

    private ComponentMapper<FlyComponent> mFly;

    public FlyAnimationSystem() {
        super(Aspect.all(FlyComponent.class));
    }

    @Override
    protected void processSystem() {
        float dt = world.getDelta();
        IntBag actives = getEntityIds();
        int currentFlyCount = actives.size();
        int[] ids = actives.getData();

        // применяем эффекты мерцания
        for (int i = 0; i < currentFlyCount; i++) {
            int id = ids[i];
            FlyComponent flyComponent = mFly.get(id);
            flyComponent.pulseTimer += dt;
        }
    }

}
