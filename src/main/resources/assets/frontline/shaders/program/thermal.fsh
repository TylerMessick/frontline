#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// two overlapping tubes, soft edged
float binocular(vec2 uv, vec2 size) {
    vec2 p = (uv - 0.5) * vec2(size.x / size.y, 1.0);
    float d = min(length(p - vec2(-0.27, 0.0)), length(p - vec2(0.27, 0.0)));
    return 1.0 - smoothstep(0.47, 0.53, d);
}

// ironbow: black, indigo, magenta, orange, yellow, white
vec3 ironbow(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 c0 = vec3(0.00, 0.00, 0.04);
    vec3 c1 = vec3(0.16, 0.02, 0.42);
    vec3 c2 = vec3(0.70, 0.05, 0.48);
    vec3 c3 = vec3(0.96, 0.38, 0.04);
    vec3 c4 = vec3(1.00, 0.84, 0.12);
    vec3 c5 = vec3(1.00, 1.00, 0.94);
    if (t < 0.2) return mix(c0, c1, t / 0.2);
    if (t < 0.4) return mix(c1, c2, (t - 0.2) / 0.2);
    if (t < 0.6) return mix(c2, c3, (t - 0.4) / 0.2);
    if (t < 0.8) return mix(c3, c4, (t - 0.6) / 0.2);
    return mix(c4, c5, (t - 0.8) / 0.2);
}

void main() {
    vec2 o = 1.0 / OutSize;
    // a light blur: thermal cameras are soft
    vec3 c = texture(DiffuseSampler, texCoord).rgb * 0.4
           + texture(DiffuseSampler, texCoord + vec2(o.x, 0.0)).rgb * 0.15
           + texture(DiffuseSampler, texCoord - vec2(o.x, 0.0)).rgb * 0.15
           + texture(DiffuseSampler, texCoord + vec2(0.0, o.y)).rgb * 0.15
           + texture(DiffuseSampler, texCoord - vec2(0.0, o.y)).rgb * 0.15;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    l += (hash(floor(texCoord * OutSize / 2.0) + vec2(Time * 977.0, Time * 613.0)) - 0.5) * 0.04;
    fragColor = vec4(ironbow(l) * binocular(texCoord, OutSize), 1.0);
}
