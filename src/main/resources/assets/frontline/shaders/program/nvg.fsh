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

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    l = clamp(pow(l, 0.85) * 1.25 + 0.03, 0.0, 1.0);
    // image-intensifier grain and faint scanlines
    vec2 px = floor(texCoord * OutSize / 2.0);
    l += (hash(px + vec2(Time * 1733.0, Time * 911.0)) - 0.5) * 0.12;
    l *= 0.93 + 0.07 * sin(texCoord.y * OutSize.y * 1.5708);
    // tube falloff toward the edges
    vec2 q = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
    l *= 1.0 - 0.35 * dot(q, q);
    vec3 phosphor = mix(vec3(0.02, 0.08, 0.03), vec3(0.55, 1.0, 0.45), clamp(l, 0.0, 1.0));
    fragColor = vec4(phosphor * binocular(texCoord, OutSize), 1.0);
}
