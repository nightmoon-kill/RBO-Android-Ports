"""Exercise the real C decoder against bounded guest-memory fixtures.

Build companion_decode.c as a shared library with your
host C compiler, then:
    python tests/test_companion_decode.py /path/to/companion_decode.so
Optionally append an EX3 executable to check the instruction signatures too.
"""
import ctypes as C
from pathlib import Path
import struct
import sys
import unittest

library = C.CDLL(str(Path(sys.argv.pop(1)).resolve()))
decode = library.companion_decode
decode.argtypes = [C.c_void_p, C.c_size_t, C.c_int, C.POINTER(C.c_int32)]
exe = Path(sys.argv.pop(1)) if len(sys.argv) > 1 else None
SIZE = 0x2600000
PLAYER, VITAL, STATUS = 0x2400000, 0x24F0000, 0x24F1000
PROFILE, ENEMIES = 0x25109C8, 0x22E8E60
SIGNATURES = {
    0x435C00: bytes.fromhex('8b4424048b04855cffad00c3'),
    0x456CF0: bytes.fromhex('8b442404c1e00605309d4900c3'),
}
class DecoderTests(unittest.TestCase):
    def setUp(self):
        self.mem = C.create_string_buffer(SIZE)
        self.out = (C.c_int32 * 288)()
        for address, code in SIGNATURES.items(): C.memmove(C.addressof(self.mem) + address, code, len(code))
    def word(self, address, value): struct.pack_into('<I', self.mem, address, value & 0xFFFFFFFF)
    def run_decode(self): decode(self.mem, SIZE, 42, self.out); return list(self.out)
    def player(self, code=2):
        for a,v in [(0xADFF5C,PLAYER),(PLAYER+0x30,0),(PLAYER+0x34,code),
                    (PLAYER+0x814,VITAL),(PLAYER+0x818,STATUS),
                    (VITAL+4,1),(STATUS+4,1),(PROFILE,code),(STATUS+0x10,code),
                    (STATUS+8,12),(STATUS+12,2200),(VITAL+0x1C,500),(VITAL+0x18,420),
                    (VITAL+0x24,100),(VITAL+0x20,85),(PROFILE+0x38,8),(PROFILE+0x504,3)]:self.word(a,v)
        for i in range(6):self.word(STATUS+0x3C+i*4,10+i)
    def enemy(self, index, code, hp, next_actor=0):
        actor=0x2410000+index*0x1000;vital=0x24F2000+index*0x100
        for a,v in [(actor+4,next_actor),(actor+0x34,code),(actor+0x814,vital),(vital+4,1),(vital+0x18,hp)]:self.word(a,v)
        return actor
    def test_menu_has_no_character_or_enemy_totals(self):
        out=self.run_decode();self.assertEqual(out[1],1);self.assertEqual(out[2:6],[0,0,0,0])
    def test_all_character_variants_and_status_fields(self):
        for code in range(14):
            self.player(code);out=self.run_decode()
            self.assertEqual(out[2],1);self.assertEqual(out[8:24],[1,code,12,2200,420,500,85,100,10,11,12,13,14,15,8,3])
    def test_single_character_ignores_other_player_pointers(self):
        self.word(0xADFF60,0xFFFFFFFF);self.word(0xADFF64,0xFFFFFFFF)
        self.player();self.assertEqual(self.run_decode()[2],1)
    def test_dead_character_keeps_status_and_identity(self):
        self.player();self.word(VITAL+0x18,0);out=self.run_decode();self.assertEqual(out[2],1);self.assertEqual(out[12],0)
    def test_invalid_pointer_and_inconsistent_profile_hide_values(self):
        self.player();self.word(PLAYER+0x814,0xFFFFFFFC);self.assertEqual(self.run_decode()[2],0)
        self.player();self.word(PROFILE,4);self.assertEqual(self.run_decode()[2],0)
    def test_counts_living_enemies_and_last_list_node(self):
        self.player();last=self.enemy(2,101,10);dead=self.enemy(1,102,0,last);first=self.enemy(0,101,20,dead)
        self.word(ENEMIES,first);out=self.run_decode()
        self.assertEqual(out[3:6],[1,2,1]);self.assertEqual(out[32:34],[101,2])
    def test_cycle_and_bad_list_pointer_do_not_publish_a_total(self):
        self.player();first=self.enemy(0,101,20);self.word(first+4,first);self.word(ENEMIES,first)
        self.assertEqual(self.run_decode()[3],0)
        self.word(ENEMIES,0xFFFFFFFC);self.assertEqual(self.run_decode()[3],0)
    def test_scene_transition_clears_previous_snapshot(self):
        self.player();self.run_decode();self.word(0xADFF5C,0)
        out=self.run_decode();self.assertEqual(out[2:6],[0,0,0,0]);self.assertEqual(out[8:32],[0]*24)
    def test_unsupported_executable_or_short_memory(self):
        self.player();self.word(0x435C00,0);self.assertEqual(self.run_decode()[1],0)
        decode(self.mem,32,0,self.out);self.assertEqual(self.out[1],0)
    @unittest.skipUnless(exe,'No game executable supplied')
    def test_signatures_against_executable(self):
        raw=exe.read_bytes();pe=struct.unpack_from('<I',raw,0x3C)[0]
        sections=struct.unpack_from('<H',raw,pe+6)[0];optional=struct.unpack_from('<H',raw,pe+20)[0]
        for address,expected in SIGNATURES.items():
            rva=address-0x400000
            for i in range(sections):
                off=pe+24+optional+i*40
                size,va,length,start=struct.unpack_from('<IIII',raw,off+8)
                if va<=rva<va+length:
                    self.assertEqual(raw[start+rva-va:start+rva-va+len(expected)],expected);break
            else:self.fail('Signature address has no file data')

if __name__=='__main__':unittest.main()
