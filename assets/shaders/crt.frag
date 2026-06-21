#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;


// Эффект выпуклого экрана CRT
vec2 curve(vec2 uv) {
    uv = (uv - 0.5) * 2.0;
    uv.x *= 1.0 + (uv.y * uv.y) * 0.02; // Изгиб по горизонтали
    uv.y *= 1.0 + (uv.x * uv.x) * 0.03; // Изгиб по вертикали
    uv = (uv / 2.0) + 0.5;
    return uv;
}

void main() {
    // 1. Применяем геометрию кинескопа
    vec2 uv = curve(v_texCoords);

    // Срезаем углы кинескопа
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // 2. ХРОМАТИЧЕСКАЯ АБЕРРАЦИЯ (ЦВЕТОВОЙ СДВИГ)
    // Вычисляем вектор направления от центра экрана (0.5, 0.5) к текущему пикселю
    vec2 toCenter = uv - vec2(0.5);

    // Сила сдвига увеличивается к краям экрана кадра.
    // Коэффициент 0.006 управляет силой эффекта (можно менять).
    vec2 shift = toCenter * 0.006;

    // Раздельное чтение каналов с разным смещением координат:
    // Красный смещаем наружу, синий вовнутрь, зеленый оставляем по центру.
    float r = texture2D(u_texture, uv + shift).r;
    float g = texture2D(u_texture, uv).g;
    float b = texture2D(u_texture, uv - shift).b;
    float a = texture2D(u_texture, uv).a; // Прозрачность берем базовую

    vec4 col = vec4(r, g, b, a);

    // 3. Сканлайны (строки развертки)
    float scanline = sin(uv.y * u_resolution.y * 3.141592) * 0.15;
    col.rgb -= scanline;

    // Виньетирование (затемнение по углам)
    float vignette = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
    col.rgb *= clamp(vignette * 15.0 + 0.25, 0.0, 1.0);

    gl_FragColor = v_color * col;
}
