#version 120

// Left half: grayscale scene from composite (colortex1). Right half: the scene itself (colortex0).
// Markers for IrisVulkanPixelCheck (bottom = row 0):
//   A bottom-left  32x32: constant (0.25, 0.5, 0.75)
//   B bottom-right 32x32: colortex1 marker written by composite, (0.5, 0.25, 1.0)
//   C top-right    32x32: (viewWidth / 4096, viewHeight / 4096, 1.0) from the uniform block

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform float viewWidth;
uniform float viewHeight;

varying vec2 texcoord;

void main() {
    vec3 color = texcoord.x < 0.5 ? texture2D(colortex1, texcoord).rgb : texture2D(colortex0, texcoord).rgb;

    if (gl_FragCoord.x > viewWidth - 32.0 && gl_FragCoord.y < 32.0) {
        color = texture2D(colortex1, texcoord).rgb;
    }

    if (gl_FragCoord.x < 32.0 && gl_FragCoord.y < 32.0) {
        color = vec3(0.25, 0.5, 0.75);
    }

    if (gl_FragCoord.x > viewWidth - 32.0 && gl_FragCoord.y > viewHeight - 32.0) {
        color = vec3(viewWidth / 4096.0, viewHeight / 4096.0, 1.0);
    }

    gl_FragColor = vec4(color, 1.0);
}
