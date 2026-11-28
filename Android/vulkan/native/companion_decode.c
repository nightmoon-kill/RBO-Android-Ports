#include "companion_data.h"
#include <string.h>

static int fits(size_t size, uint32_t address, size_t count) {
    return address >= 0x400000 && address <= size && count <= size - address;
}
static uint32_t word(const uint8_t *memory, uint32_t address) {
    uint32_t value;
    memcpy(&value, memory + address, sizeof value);
    return value;
}
static int object(const uint8_t *memory, size_t size, uint32_t address, size_t count) {
    return fits(size, address, count) && !(address & 3) && word(memory, address + 4) > 0;
}

void companion_decode(const uint8_t *m, size_t size, int frame, int32_t *out) {
    memset(out, 0, COMPANION_WORDS * sizeof *out);
    out[0] = 1;
    out[6] = frame;
    /* RBO EX3 signatures at the player getter and class-name getter. A different
       executable must never be interpreted using this memory layout. */
    static const uint8_t player_getter[] = {0x8b,0x44,0x24,0x04,0x8b,0x04,0x85,0x5c,0xff,0xad,0x00,0xc3};
    static const uint8_t class_getter[] = {0x8b,0x44,0x24,0x04,0xc1,0xe0,0x06,0x05,0x30,0x9d,0x49,0x00,0xc3};
    if (!m || !fits(size, 0x25109c8, 0x660) ||
            memcmp(m + 0x435c00, player_getter, sizeof player_getter) ||
            memcmp(m + 0x456cf0, class_getter, sizeof class_getter)) return;
    out[1] = 1;
    for (int slot = 0; slot < COMPANION_PLAYERS; ++slot) {
        uint32_t actor = word(m, 0xadff5c + slot * 4);
        uint32_t profile = 0x25109c8 + slot * 0x660;
        if (!fits(size, actor, 0x974) || (actor & 3) || word(m, actor + 0x30) != (uint32_t)slot) continue;
        uint32_t vital = word(m, actor + 0x814), status = word(m, actor + 0x818);
        if (!object(m, size, vital, 0x64) || !object(m, size, status, 0x94)) continue;
        int code = (int)word(m, status + 0x10);
        if (code < 0 || code >= 14 || word(m, actor + 0x34) != (uint32_t)code ||
                word(m, profile) != (uint32_t)code) continue;
        int32_t *p = out + 8 + slot * COMPANION_PLAYER_WORDS;
        p[1] = code;
        p[2] = (int32_t)word(m, status + 8);
        p[3] = (int32_t)word(m, status + 12);
        p[4] = (int32_t)word(m, vital + 0x18);
        p[5] = (int32_t)word(m, vital + 0x1c);
        p[6] = (int32_t)word(m, vital + 0x20);
        p[7] = (int32_t)word(m, vital + 0x24);
        if (p[2] < 1 || p[2] > 999 || p[5] <= 0 || p[5] > 999999999 ||
                p[7] <= 0 || p[7] > 999999999 || p[4] < 0 || p[4] > p[5] || p[6] < 0 || p[6] > p[7]) {
            memset(p, 0, COMPANION_PLAYER_WORDS * sizeof *p);
            continue;
        }
        for (int i = 0; i < 6; ++i) p[8 + i] = (int32_t)word(m, status + 0x3c + i * 4);
        p[14] = (int32_t)word(m, profile + 0x38);
        p[15] = (int32_t)word(m, profile + 0x504);
        p[0] = 1;
        out[2] |= 1 << slot;
    }
    if (!out[2]) return;
    /* Group 1 is the live enemy list (4442c0 uses a 0x974 group stride).
       Only spawned, living enemies count; future scripted waves are excluded. */
    uint32_t actor = word(m, 0x22e84ec + 0x974);
    uint32_t seen[512];
    int visited = 0;
    while (actor) {
        if (visited == 512 || !fits(size, actor, 0x974) || (actor & 3)) return;
        for (int i = 0; i < visited; ++i) if (seen[i] == actor) return;
        seen[visited++] = actor;
        uint32_t vital = word(m, actor + 0x814);
        if (object(m, size, vital, 0x64) && (int32_t)word(m, vital + 0x18) > 0) {
            int code = (int32_t)word(m, actor + 0x34);
            if (code < 0 || code > 65535) return;
            int index;
            for (index = 0; index < out[5]; ++index) if (out[32 + index * 2] == code) break;
            if (index == COMPANION_SPECIES) return;
            if (index == out[5]) { out[32 + index * 2] = code; ++out[5]; }
            ++out[33 + index * 2];
            ++out[4];
        }
        actor = word(m, actor + 4);
    }
    for (int i=1;i<out[5];++i) {
        int code=out[32+i*2],count=out[33+i*2],j=i;
        while(j>0&&out[32+(j-1)*2]>code){
            out[32+j*2]=out[32+(j-1)*2];out[33+j*2]=out[33+(j-1)*2];--j;
        }
        out[32+j*2]=code;out[33+j*2]=count;
    }
    out[3] = 1;
}
