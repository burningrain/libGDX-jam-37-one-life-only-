#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

// Обязательное имя для SpriteBatch
uniform sampler2D u_texture;
uniform vec2 u_resolution;

vec2 curve(vec2 uv) {
    uv = (uv - 0.5) * 2.0;
    uv.x *= 1.0 + (uv.y * uv.y) * 0.02;
    uv.y *= 1.0 + (uv.x * uv.x) * 0.03;
    uv = (uv / 2.0) + 0.5;
    return uv;
}

void main() {
    vec2 uv = curve(v_texCoords);

    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec4 col = texture2D(u_texture, uv);

    float scanline = sin(uv.y * u_resolution.y * 3.141592) * 0.15;
    col.rgb -= scanline;

    float vignette = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
    col.rgb *= clamp(vignette * 15.0 + 0.25, 0.0, 1.0);

    gl_FragColor = v_color * col;
}
