#version 450
layout(location = 0) in vec4 position_uv;
layout(location = 1) in vec4 vertex_color;
layout(location = 0) out vec4 diffuse;
layout(location = 1) out vec2 uv;
layout(push_constant) uniform State {
    vec4 size;
    ivec4 color_stage;
    ivec4 alpha_stage;
    ivec4 test;
    vec4 factor;
} state;

void main() {
    gl_Position = vec4(position_uv.xy / state.size.xy * 2.0 - 1.0, 0.0, 1.0);
    diffuse = vertex_color;
    uv = position_uv.zw;
}
