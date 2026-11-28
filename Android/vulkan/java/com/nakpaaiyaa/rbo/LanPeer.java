package com.nakpaaiyaa.rbo;
import java.io.*;
import java.nio.ByteBuffer;
/** One emulator-thread input exchange per keyboard poll. */
final class LanPeer {
    private final DataInputStream input;private final DataOutputStream output;
    private final int role;private int sequence;
    private final byte[] sending=new byte[16],receiving=new byte[16];
    private final ByteBuffer send=ByteBuffer.wrap(sending),receive=ByteBuffer.wrap(receiving);
    LanPeer(DataInputStream in,DataOutputStream out,int role){
        if(role!=1&&role!=2)throw new IllegalArgumentException("Invalid LAN role");
        input=in;output=out;this.role=role;
    }
    int exchange(int local,long digest) throws IOException {
        if((local&~511)!=0)throw new IOException("Invalid local input");
        send.clear();send.putInt(sequence).putLong(digest).putInt(local);
        output.write(sending);output.flush();
        input.readFully(receiving);receive.clear();
        if(receive.getInt()!=sequence||receive.getLong()!=digest)throw new IOException("Game state mismatch at input "+sequence);
        int other=receive.getInt();if((other&~511)!=0)throw new IOException("Invalid input buttons");
        sequence++;return role==1?local|(other<<9):other|(local<<9);
    }
}
