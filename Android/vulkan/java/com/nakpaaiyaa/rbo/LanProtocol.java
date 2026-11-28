package com.nakpaaiyaa.rbo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Versioned LAN wire format. The host assigns roles; peers cannot claim P1. */
final class LanProtocol {
    static final int MAGIC = 0x52424f4c, VERSION = 3, PORT = 27667;
    static final int P1 = 1, P2 = 2, INPUT_MASK = 0x1ff;
    static final int MAX_PAYLOAD = 32 * 1024 * 1024;
    static final int INPUT = 1, START = 2, FRAME = 3, STOP = 4, HEARTBEAT = 5;
    private LanProtocol() {}

    static void hello(DataOutputStream out, String room) throws IOException {
        if (!room.matches("[0-9]{6}")) throw new IOException("Enter a 6-digit room code");
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeUTF(room); out.flush();
    }
    static void accept(DataInputStream in, DataOutputStream out, String room) throws IOException {
        int magic=in.readInt(),version=in.readInt();
        if(magic!=MAGIC || version!=VERSION){
            out.writeInt(MAGIC);out.writeInt(-3);out.flush();
            throw new IOException("LAN versions differ. Install 0.7.0 on both devices.");
        }
        String received = in.readUTF();
        boolean valid = MessageDigest.isEqual(room.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
        out.writeInt(MAGIC); out.writeInt(valid ? P2 : -1); out.flush();
        if (!valid) throw new IOException("Wrong room code. Enter the code shown on P1.");
    }
    static int assignedRole(DataInputStream in) throws IOException {
        if(in.readInt()!=MAGIC)throw new IOException("Not an RBO LAN room");
        int role=in.readInt();
        if(role==-1)throw new IOException("Wrong room code. Enter the code shown on P1.");
        if(role!=P2)throw new IOException("LAN versions differ. Install 0.7.0 on both devices.");
        return P2;
    }
    static void input(DataOutputStream out, int sequence, int mask) throws IOException {
        if (sequence < 0 || (mask & ~INPUT_MASK) != 0) throw new IOException("Invalid input packet");
        out.writeInt(INPUT); out.writeInt(sequence); out.writeInt(mask); out.flush();
    }
    static int readInput(DataInputStream in, int expected) throws IOException {
        if (in.readInt() != INPUT || in.readInt() != expected) throw new IOException("LAN input sequence mismatch");
        int mask = in.readInt();
        if ((mask & ~INPUT_MASK) != 0) throw new IOException("Invalid input buttons");
        return mask;
    }
    static byte[] payload(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_PAYLOAD) throw new IOException("Invalid LAN payload length");
        byte[] bytes = new byte[length]; in.readFully(bytes); return bytes;
    }
}
