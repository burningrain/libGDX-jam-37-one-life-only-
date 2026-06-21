package com.github.br.libgx.jam37.components.enemy;

import com.artemis.Component;

public class GameParamsComponent extends Component {

    public final int maxFliesOnWeb = 4;

    public boolean isGameOver = false;
    public int currentFliesAmount = 0;
    public int currentPoints = 0;

    public boolean isCountingDown = true; // Флаг, идет ли сейчас отсчет перед стартом
    public float startTimer = 3.9f; // Таймер отсчета в секундах (3.9f, чтобы тройка горела подольше)

    public void reset() {
        isGameOver = false;
        currentPoints = 0;
        currentFliesAmount = 0;
        startTimer = 3.9f;
        isCountingDown = true;
    }

}
