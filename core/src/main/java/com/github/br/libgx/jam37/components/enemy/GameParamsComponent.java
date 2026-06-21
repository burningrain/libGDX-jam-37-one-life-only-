package com.github.br.libgx.jam37.components.enemy;

import com.artemis.Component;

public class GameParamsComponent extends Component {

    public final int maxFliesOnWeb = 4;
    public final int victoryPoints = 10;

    public boolean isStartScreen = false; // Флаг стартового экрана (true только при самом первом запуске приложения)

    public boolean isGameOver = false;
    public boolean isVictory = false;     // Флаг экрана победы
    public int currentFliesAmount = 0;
    public int currentPoints = 0;

    public boolean isCountingDown = false; // Флаг, идет ли сейчас отсчет перед стартом
    public float startTimer = 3.9f; // Таймер отсчета в секундах (3.9f, чтобы тройка горела подольше)

    public void reset() {
        isStartScreen = false; //  не сбрасывается
        isGameOver = false;
        isCountingDown = true; // в true, чтобы сразу начинать с этого состояния
        startTimer = 3.9f;
        currentPoints = 0;
        currentFliesAmount = 0;
    }

}
