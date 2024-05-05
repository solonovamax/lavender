#version 150

uniform sampler2D DiffuseSampler;

uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord) * vertexColor;
    color.a = min(1.0f, color.a * 1e8f);

    // blit final output of compositor into displayed back buffer
    fragColor = color * ColorModulator;
}
