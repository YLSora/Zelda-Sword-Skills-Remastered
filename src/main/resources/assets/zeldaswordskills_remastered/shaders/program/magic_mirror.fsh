#version 150

uniform sampler2D DiffuseSampler;
uniform float Progress;
uniform float WarpTime;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float angle = sin(WarpTime * 2.4) * 0.12 * Progress;
    mat2 rotation = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    vec2 warped = rotation * centered;
    warped.x *= 1.0 + sin(WarpTime * 3.0) * 0.14 * Progress;
    warped += sin(warped.yx * 7.0 + WarpTime * 4.0) * 0.025 * Progress;
    vec2 uv = clamp(warped * 0.5 + 0.5, vec2(0.001), vec2(0.999));
    vec3 world = texture(DiffuseSampler, uv).rgb;
    float radius = length(centered) / sqrt(2.0);
    float edge = 1.2 - Progress * 1.4;
    float white = smoothstep(edge - 0.18, edge, radius);
    fragColor = vec4(mix(world, vec3(1.0), white), 1.0);
}
