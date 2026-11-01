#version 120

// Writes the pass's own texture coordinate into colortex1, so final can prove where every pixel came from.

varying vec2 texcoord;

void main() {
/* DRAWBUFFERS:1 */
    gl_FragData[0] = vec4(texcoord, 0.0, 1.0);
}
