// Shadow edge softening fragment shader
// Creates soft penumbra effect at shadow boundaries

#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;       // Screen resolution
uniform float u_softness;        // Shadow edge softness (blur amount)
uniform vec3 u_lightColor;       // Light color for tinting
uniform float u_lightIntensity;  // Light intensity

// Gaussian blur weights
const float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec2 texelSize = 1.0 / u_resolution;
    
    // Sample center
    vec4 result = texture2D(u_texture, v_texCoords) * weights[0];
    
    // Horizontal blur pass
    for (int i = 1; i < 5; i++) {
        float offset = float(i) * u_softness;
        result += texture2D(u_texture, v_texCoords + vec2(texelSize.x * offset, 0.0)) * weights[i];
        result += texture2D(u_texture, v_texCoords - vec2(texelSize.x * offset, 0.0)) * weights[i];
    }
    
    // Vertical blur pass
    vec4 vertical = result * weights[0];
    for (int i = 1; i < 5; i++) {
        float offset = float(i) * u_softness;
        vertical += texture2D(u_texture, v_texCoords + vec2(0.0, texelSize.y * offset)) * weights[i];
        vertical += texture2D(u_texture, v_texCoords - vec2(0.0, texelSize.y * offset)) * weights[i];
    }
    
    // Blend with light color
    vec3 finalColor = mix(result.rgb, u_lightColor, 0.3) * u_lightIntensity;
    float finalAlpha = vertical.a * u_lightIntensity;
    
    gl_FragColor = vec4(finalColor, finalAlpha);
}
