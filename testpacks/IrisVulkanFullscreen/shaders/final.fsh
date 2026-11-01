#version 120

// Full-screen validation: every output pixel is predictable on the CPU (see IrisVulkanPixelModel).
//   red, green: the texture coordinate composite stored in colortex1 at this pixel
//   blue:       luminance of the scene (colortex0) at this pixel

uniform sampler2D colortex0;
uniform sampler2D colortex1;

varying vec2 texcoord;

void main() {
    vec2 uv = texture2D(colortex1, texcoord).rg;
    float luminance = dot(texture2D(colortex0, texcoord).rgb, vec3(0.299, 0.587, 0.114));
    gl_FragColor = vec4(uv, luminance, 1.0);
}
