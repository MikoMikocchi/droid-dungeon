// Light map composite fragment shader
// Applies the light map to the scene using multiply blending
// Includes ambient light floor and soft clamping for natural look

#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;     // Light map texture
uniform float u_brightness;      // Global brightness multiplier
uniform vec3 u_ambientColor;     // Base ambient color
uniform float u_ambientMin;      // Minimum ambient light (prevents pure black)
uniform float u_contrast;        // Light/dark contrast adjustment

void main() {
    // Sample light map
    vec4 lightSample = texture2D(u_texture, v_texCoords);
    
    // Extract light contribution
    vec3 lightColor = lightSample.rgb;
    float lightIntensity = lightSample.a;
    
    // Apply brightness
    lightColor *= u_brightness;
    lightIntensity *= u_brightness;
    
    // Add ambient floor
    vec3 ambient = u_ambientColor * u_ambientMin;
    lightColor = max(lightColor, ambient);
    lightIntensity = max(lightIntensity, u_ambientMin);
    
    // Apply contrast curve for more dramatic lighting
    // This makes lit areas pop while keeping shadows deep
    float contrastMid = 0.4;
    float adjustedIntensity = lightIntensity;
    if (lightIntensity < contrastMid) {
        // Darken shadows
        adjustedIntensity = lightIntensity * (1.0 - u_contrast * 0.3);
    } else {
        // Brighten highlights (but not too much)
        float t = (lightIntensity - contrastMid) / (1.0 - contrastMid);
        adjustedIntensity = contrastMid + t * (1.0 - contrastMid) * (1.0 + u_contrast * 0.15);
    }
    
    // Soft clamp to prevent harsh cutoffs while preserving detail
    adjustedIntensity = clamp(adjustedIntensity, 0.0, 1.0);
    
    // Reconstruct color with adjusted intensity
    vec3 finalColor = lightColor * (adjustedIntensity / max(lightIntensity, 0.001));
    finalColor = clamp(finalColor, 0.0, 1.0);
    
    gl_FragColor = vec4(finalColor, adjustedIntensity);
}
