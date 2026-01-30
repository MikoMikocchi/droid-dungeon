// Light rendering fragment shader
// Creates smooth radial light with falloff, warm color tinting, and intensity control

#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_intensity;      // Light intensity 0-1
uniform float u_maxIntensity;   // Maximum allowed intensity (prevents overexposure)
uniform float u_innerRadius;    // Inner radius ratio where light is full intensity
uniform float u_falloffPower;   // Falloff curve exponent (higher = sharper edge)
uniform float u_time;           // Time for flicker animation
uniform float u_flickerAmount;  // Amount of flicker variation

void main() {
    // Sample the gradient texture (radial falloff baked in)
    float alpha = texture2D(u_texture, v_texCoords).a;
    
    // Calculate distance from center for additional processing
    vec2 centered = v_texCoords * 2.0 - 1.0;
    float dist = length(centered);
    
    // Apply custom falloff curve
    float falloff;
    if (dist < u_innerRadius) {
        falloff = 1.0;
    } else {
        float t = (dist - u_innerRadius) / (1.0 - u_innerRadius);
        t = clamp(t, 0.0, 1.0);
        // Smooth cubic falloff
        falloff = 1.0 - t * t * (3.0 - 2.0 * t);
        // Additional power curve for softness
        falloff = pow(falloff, u_falloffPower);
    }
    
    // Combine with texture alpha
    float finalAlpha = falloff * alpha;
    
    // Apply flicker
    float flicker = 1.0;
    if (u_flickerAmount > 0.0) {
        flicker = 1.0 + u_flickerAmount * (
            sin(u_time * 7.3) * 0.5 +
            sin(u_time * 13.7 + 1.3) * 0.3 +
            sin(u_time * 23.1 + 2.7) * 0.2
        );
        flicker = clamp(flicker, 0.5, 1.5);
    }
    
    // Calculate final intensity
    float intensity = finalAlpha * u_intensity * flicker;
    intensity = min(intensity, u_maxIntensity);
    
    // Output light color with intensity
    vec3 lightColor = v_color.rgb * intensity;
    
    // Slight color warmth boost at lower intensities for atmospheric feel
    float warmth = 1.0 - intensity * 0.1;
    lightColor.r *= warmth * 1.05;
    lightColor.g *= warmth;
    lightColor.b *= warmth * 0.95;
    
    gl_FragColor = vec4(lightColor, intensity);
}
