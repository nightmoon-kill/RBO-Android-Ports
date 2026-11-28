#include "point_cheats.h"
#include <string.h>

static uint32_t read32(const uint8_t *m, size_t a) {
    uint32_t value;
    memcpy(&value, m + a, 4);
    return value;
}
int point_cheats_apply(uint8_t *m, size_t size, unsigned mask) {
    const size_t profile = 0x25109c8;
    static const uint8_t getter[] = {
        0x8b,0x44,0x24,0x04,0x8b,0x04,0x85,0x5c,0xff,0xad,0x00,0xc3
    };
    /* EX3 profile getter. The selected character's profile exists before its
       in-stage actor, so point cheats must not require a live actor pointer. */
    static const uint8_t profile_getter[] = {
        0x8b,0x4c,0x24,0x04,0x8b,0xc1,0xc1,0xe0,0x04,0x03,0xc1
    };
    if (!m || size < profile + 0x660 || !(mask & 3) ||
            memcmp(m + 0x435c00, getter, sizeof getter) ||
            memcmp(m + 0x4562e0, profile_getter, sizeof profile_getter)) return 0;
    uint32_t code = read32(m, profile), level = read32(m, profile + 0x18);
    if (code >= 14 || level < 1 || level > 999) return 0;
    /* User-requested refill target: 999 for both point types. */
    if (mask & 1) { const uint32_t maximum = 999; memcpy(m + profile + 0x38, &maximum, 4); }
    if (mask & 2) { const uint32_t maximum = 999; memcpy(m + profile + 0x504, &maximum, 4); }
    return 1;
}
