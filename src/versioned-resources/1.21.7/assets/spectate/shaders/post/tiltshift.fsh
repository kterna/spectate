#version 150

uniform sampler2D InSampler;

layout(std140) uniform TiltShiftConfig {
    float FocusY;
    float FocusWidth;
    float BlurRadius;
    float Falloff;
    float SaturationBoost;
};

in vec2 texCoord;

out vec4 fragColor;

float getBlurFactor(float y) {
    float dist = abs(y - FocusY);
    float edge = FocusWidth * 0.5;
    return smoothstep(edge, edge + Falloff * 0.5, dist);
}

vec3 applyBlur(vec2 uv, float radiusScale) {
    vec2 offset = vec2(0.0016, 0.0010) * radiusScale;

    vec3 color = texture(InSampler, uv).rgb * 0.28;
    color += texture(InSampler, uv + vec2(offset.x, 0.0)).rgb * 0.16;
    color += texture(InSampler, uv - vec2(offset.x, 0.0)).rgb * 0.16;
    color += texture(InSampler, uv + vec2(0.0, offset.y)).rgb * 0.16;
    color += texture(InSampler, uv - vec2(0.0, offset.y)).rgb * 0.16;
    color += texture(InSampler, uv + offset).rgb * 0.04;
    color += texture(InSampler, uv - offset).rgb * 0.04;
    return color;
}

vec3 adjustSaturation(vec3 color, float saturation) {
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    return mix(vec3(gray), color, saturation);
}

void main() {
    float blurFactor = getBlurFactor(texCoord.y);
    float radiusScale = (BlurRadius / 8.0) * max(blurFactor, 0.01);

    vec3 sharpColor = texture(InSampler, texCoord).rgb;
    vec3 blurColor = applyBlur(texCoord, radiusScale);

    vec3 finalColor = mix(sharpColor, blurColor, blurFactor);
    finalColor = adjustSaturation(finalColor, SaturationBoost);

    fragColor = vec4(finalColor, 1.0);
}
