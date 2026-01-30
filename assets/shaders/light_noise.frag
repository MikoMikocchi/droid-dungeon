// Noise-based light variation fragment shader
// Adds organic flickering and variation to light sources

#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform sampler2D u_noiseTexture;
uniform float u_time;
uniform float u_noiseScale;      // How much noise affects the light
uniform float u_noiseSpeed;      // How fast noise animates
uniform vec2 u_lightCenter;      // Light center position (for directional noise)

// Simple noise function
float noise(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

// Fractal Brownian Motion for organic noise
float fbm(vec2 st) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(st * frequency);
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    
    return value;
}

void main() {
    // Sample base light texture
    vec4 lightColor = texture2D(u_texture, v_texCoords);
    
    // Calculate animated noise
    vec2 noiseCoord = v_texCoords * 3.0 + u_time * u_noiseSpeed * 0.1;
    float noiseValue = fbm(noiseCoord);
    
    // Sample noise texture for additional variation
    vec2 animatedUV = v_texCoords + vec2(
        sin(u_time * 1.3) * 0.02,
        cos(u_time * 1.7) * 0.02
    );
    float textureNoise = texture2D(u_noiseTexture, animatedUV * 2.0).r;
    
    // Combine noise sources
    float combinedNoise = mix(noiseValue, textureNoise, 0.5);
    
    // Apply noise as intensity variation
    float noiseEffect = 1.0 + (combinedNoise - 0.5) * u_noiseScale * 2.0;
    noiseEffect = clamp(noiseEffect, 0.7, 1.3);
    
    // Apply to light
    vec3 finalColor = lightColor.rgb * noiseEffect;
    float finalAlpha = lightColor.a * noiseEffect;
    
    // Slight color shift based on noise for more organic feel
    finalColor.r *= 1.0 + (combinedNoise - 0.5) * 0.1;
    finalColor.b *= 1.0 - (combinedNoise - 0.5) * 0.05;
    
    gl_FragColor = vec4(finalColor, finalAlpha);
}
