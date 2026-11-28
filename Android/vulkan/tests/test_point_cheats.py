"""Test production point writes against bounded EX3 memory fixtures."""
import ctypes as C
from pathlib import Path
import struct
import sys
import unittest

dll = C.CDLL(str(Path(sys.argv.pop(1)).resolve()))
apply = dll.point_cheats_apply
apply.argtypes = [C.c_void_p, C.c_size_t, C.c_uint]
apply.restype = C.c_int
exe = Path(sys.argv.pop(1))
PROFILE = 0x25109C8
SIZE = PROFILE + 0x660 * 2
SIGNATURES = {
    0x435C00: bytes.fromhex('8b4424048b04855cffad00c3'),
    0x4562E0: bytes.fromhex('8b4c24048bc1c1e00403c1'),
}

class PointCheatsTests(unittest.TestCase):
    def setUp(self):
        self.memory = bytearray(SIZE)
        self.buffer = (C.c_ubyte * SIZE).from_buffer(self.memory)
        for address, signature in SIGNATURES.items():
            self.memory[address:address+len(signature)] = signature
        self.put(PROFILE, 3)
        self.put(PROFILE+0x18, 1)
        self.put(PROFILE+0x38, 7)
        self.put(PROFILE+0x504, 8)
    def put(self, address, value):
        struct.pack_into('<I', self.memory, address, value)
    def points(self):
        return (struct.unpack_from('<I', self.memory, PROFILE+0x38)[0],
                struct.unpack_from('<I', self.memory, PROFILE+0x504)[0])
    def run_mask(self, mask):
        return apply(self.buffer, SIZE, mask)
    def test_before_stage_actor_exists(self):
        self.assertEqual(struct.unpack_from('<I', self.memory, 0xADFF5C)[0], 0)
        self.assertEqual(self.run_mask(3), 1)
        self.assertEqual(self.points(), (999, 999))
    def test_independent_switches(self):
        self.run_mask(1)
        self.assertEqual(self.points(), (999, 8))
        self.put(PROFILE+0x38, 7)
        self.run_mask(2)
        self.assertEqual(self.points(), (7, 999))
    def test_refill_spending_and_disable(self):
        self.run_mask(3)
        self.put(PROFILE+0x38, 990)
        self.put(PROFILE+0x504, 998)
        self.run_mask(3)
        self.assertEqual(self.points(), (999, 999))
        self.put(PROFILE+0x38, 990)
        self.put(PROFILE+0x504, 998)
        self.run_mask(0)
        self.assertEqual(self.points(), (990, 998))
    def test_only_requested_fields_change(self):
        self.memory[PROFILE:SIZE] = bytes([0xA5]) * (SIZE-PROFILE)
        self.put(PROFILE, 12)
        self.put(PROFILE+0x18, 25)
        before = self.memory[:]
        self.run_mask(3)
        expected = before[:]
        for offset in [0x38, 0x504]:
            struct.pack_into('<I', expected, PROFILE+offset, 999)
        self.assertEqual(self.memory, expected)
    def test_class_switch_and_deselection(self):
        for code in range(14):
            self.put(PROFILE, code)
            self.put(PROFILE+0x38, code)
            self.assertEqual(self.run_mask(3), 1)
            self.assertEqual(self.points(), (999, 999))
        for code, level in [(0xFFFFFFFF, 1), (14, 1), (1, 0), (1, 1000)]:
            self.put(PROFILE, code)
            self.put(PROFILE+0x18, level)
            before = self.memory[:]
            self.assertEqual(self.run_mask(3), 0)
            self.assertEqual(self.memory, before)
    def test_invalid_executable_or_buffer(self):
        self.assertEqual(apply(None, SIZE, 3), 0)
        self.assertEqual(apply(self.buffer, PROFILE+0x100, 3), 0)
        for address in SIGNATURES:
            self.memory[address] ^= 1
            before = self.memory[:]
            self.assertEqual(self.run_mask(3), 0)
            self.assertEqual(self.memory, before)
            self.memory[address] ^= 1
    def test_signatures_match_supplied_executable(self):
        # PE executable sections are mapped by their virtual addresses.
        raw = exe.read_bytes()
        pe = struct.unpack_from('<I', raw, 0x3C)[0]
        count, optional = struct.unpack_from('<H', raw, pe+6)[0], struct.unpack_from('<H', raw, pe+20)[0]
        section_table = pe+24+optional
        for address, signature in SIGNATURES.items():
            for i in range(count):
                vs, va, size, offset = struct.unpack_from('<IIII', raw, section_table+i*40+8)
                rva = address-0x400000
                if va <= rva < va+size:
                    self.assertEqual(raw[offset+rva-va:offset+rva-va+len(signature)], signature)
                    break
            else:
                self.fail('Missing EX3 signature section')

if __name__ == '__main__': unittest.main()
