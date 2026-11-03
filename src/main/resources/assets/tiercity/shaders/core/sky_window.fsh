#version 150

uniform sampler2D Sampler0;   // copy of the screen right after the sky was drawn

in vec4 clipPos;

out vec4 fragColor;

void main() {
    vec2 uv = clipPos.xy / clipPos.w * 0.5 + 0.5;
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
}
