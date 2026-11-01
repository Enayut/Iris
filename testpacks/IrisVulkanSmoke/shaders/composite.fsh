#version 120

// Grayscale copy of the scene into colortex1, plus marker B in the bottom-right 32x32 pixels.

uniform sampler2D colortex0;
uniform float viewWidth;

varying vec2 texcoord;

void main() {
    vec3 scene = texture2D(colortex0, texcoord).rgb;
    vec4 color = vec4(vec3(dot(scene, vec3(0.299, 0.587, 0.114))), 1.0);

    if (gl_FragCoord.x > viewWidth - 32.0 && gl_FragCoord.y < 32.0) {
        color = vec4(0.5, 0.25, 1.0, 1.0);
    }

/* DRAWBUFFERS:1 */
    gl_FragData[0] = color;
}
