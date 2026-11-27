#version 450
layout(location = 0) in vec4 diffuse;
layout(location = 1) in vec2 uv;
layout(location = 0) out vec4 pixel;
layout(set = 0, binding = 0) uniform sampler2D source_texture;
layout(set = 0, binding = 1) uniform sampler2D key_mask;
layout(push_constant) uniform State {
    vec4 size;
    ivec4 color_stage; // texturing, color op, color arg 1 and 2
    ivec4 alpha_stage; // alpha op, alpha arg 1 and 2, color key
    ivec4 test;        // enabled, compare function, reference, point sampling
    vec4 factor;
} state;

vec4 argument(int index, vec4 texel) {
    if (index == 2) return texel;
    if (index == 3) return state.factor;
    return round(diffuse * 255.0) / 255.0;
}

vec4 combine(int op, vec4 a, vec4 b) {
    switch (op) {
        case 2: return a;
        case 3: return b;
        case 5: return min(round(a * b * 255.0) * (2.0 / 255.0), vec4(1));
        case 6: return min(round(a * b * 255.0) * (4.0 / 255.0), vec4(1));
        case 7: return min(a + b, vec4(1));
        default: return a * b;
    }
}

bool alpha_passes(int alpha) {
    int reference = state.test.z;
    switch (state.test.y) {
        case 1: return false;
        case 2: return alpha < reference;
        case 3: return alpha == reference;
        case 4: return alpha <= reference;
        case 5: return alpha > reference;
        case 6: return alpha != reference;
        case 7: return alpha >= reference;
        default: return true;
    }
}

void main() {
    vec4 color = round(diffuse * 255.0) / 255.0;
    if (state.color_stage.x != 0) {
        vec2 sample_uv = uv;
        if (state.test.w != 0) {
            vec2 texels = state.size.zw;
            sample_uv = (floor(uv * texels + 0.004) + 0.5) / texels;
        }
        if (state.alpha_stage.w != 0 && texture(key_mask, sample_uv).r > 0.999)
            discard;

        vec4 texel = texture(source_texture, sample_uv);
        color.rgb = combine(state.color_stage.y, argument(state.color_stage.z, texel),
                            argument(state.color_stage.w, texel)).rgb;
        if (state.alpha_stage.x != 1) {
            int alpha_op = state.alpha_stage.x == 6 ? 4 : state.alpha_stage.x;
            color.a = combine(alpha_op, argument(state.alpha_stage.y, texel),
                              argument(state.alpha_stage.z, texel)).a;
        }
    }
    if (state.test.x != 0 && !alpha_passes(int(color.a * 255.0 + 0.5)))
        discard;
    pixel = color;
}
