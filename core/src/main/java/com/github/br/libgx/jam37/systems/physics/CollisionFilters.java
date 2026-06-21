package com.github.br.libgx.jam37.systems.physics;

public class CollisionFilters {
    public static final short CATEGORY_PLAYER = 0x0001;        // Гусеница (игрок)
    public static final short CATEGORY_SPIDER_BODY = 0x0002;   // Основное тело паука
    public static final short CATEGORY_SPIDER_PIECE = 0x0004;  // Хелицеры / лапы паука
    public static final short CATEGORY_WEB = 0x0008;           // Паутина
    public static final short CATEGORY_COLLECTABLES = 0x0010;         // Стены, границы уровня
}
