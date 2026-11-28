package com.nakpaaiyaa.rbo;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public final class LanTest {
    static int checks;
    static void check(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
    interface Checked {void run() throws Exception;}
    static void rejected(Checked action) throws Exception {boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed);}
    static DataInputStream data(byte[] bytes){return new DataInputStream(new ByteArrayInputStream(bytes));}
    static byte[] zip(String name,byte[] bytes)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){z.putNextEntry(new ZipEntry(name));z.write(bytes);z.closeEntry();}return out.toByteArray();
    }
    static void connection()throws Exception{
        try(ServerSocket server=new ServerSocket(0);Socket client=new Socket("127.0.0.1",server.getLocalPort());Socket host=server.accept()){
            host.setSoTimeout(1000);client.setSoTimeout(1000);host.setTcpNoDelay(true);client.setTcpNoDelay(true);
            DataInputStream hi=new DataInputStream(host.getInputStream()),ci=new DataInputStream(client.getInputStream());
            DataOutputStream ho=new DataOutputStream(host.getOutputStream()),co=new DataOutputStream(client.getOutputStream());
            // No game-file fingerprint is sent or required.
            LanProtocol.hello(co,"012345");LanProtocol.accept(hi,ho,"012345");check(LanProtocol.assignedRole(ci)==2);
            LanPeer p1=new LanPeer(hi,ho,1),p2=new LanPeer(ci,co,2);
            ExecutorService pool=Executors.newSingleThreadExecutor();
            try{
                for(int i=0;i<300;i++){
                    final int n=i,a=i&511,b=(i*17)&511;
                    Future<Integer> remote=pool.submit(()->p2.exchange(b,n));
                    int combined=p1.exchange(a,n);check(combined==(a|(b<<9)));check(remote.get(2,TimeUnit.SECONDS)==combined);
                }
                Future<Boolean> mismatch=pool.submit(()->{try{p2.exchange(0,22);return false;}catch(IOException good){return true;}});
                rejected(()->p1.exchange(0,23));check(mismatch.get(2,TimeUnit.SECONDS));
            }finally{pool.shutdownNow();}
        }
        try(ServerSocket server=new ServerSocket(0);Socket client=new Socket("127.0.0.1",server.getLocalPort());Socket host=server.accept()){
            DataInputStream hi=new DataInputStream(host.getInputStream()),ci=new DataInputStream(client.getInputStream());
            DataOutputStream ho=new DataOutputStream(host.getOutputStream()),co=new DataOutputStream(client.getOutputStream());
            LanProtocol.hello(co,"000001");rejected(()->LanProtocol.accept(hi,ho,"000002"));rejected(()->LanProtocol.assignedRole(ci));
        }
        ByteArrayOutputStream malformed=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(malformed);out.writeInt(-1);rejected(()->LanProtocol.payload(data(malformed.toByteArray())));
        malformed.reset();out.writeInt(LanProtocol.MAX_PAYLOAD+1);rejected(()->LanProtocol.payload(data(malformed.toByteArray())));
        malformed.reset();LanProtocol.input(out,1,7);rejected(()->LanProtocol.readInput(data(malformed.toByteArray()),0));
        rejected(()->LanProtocol.input(out,0,512));
        malformed.reset();out.writeInt(0);out.writeLong(1);out.writeInt(512);
        LanPeer invalid=new LanPeer(data(malformed.toByteArray()),new DataOutputStream(new ByteArrayOutputStream()),1);rejected(()->invalid.exchange(0,1));
        malformed.reset();out.writeInt(LanProtocol.MAGIC);out.writeInt(1);rejected(()->LanProtocol.assignedRole(data(malformed.toByteArray())));
    }
    static void packet()throws Exception{
        ByteBuffer packet=ByteBuffer.allocate(16).putInt(0).putLong(-123456789012L).putInt(128);
        ByteArrayInputStream fragments=new ByteArrayInputStream(packet.array()){
            @Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(len,3));}
        };
        final int[] writes={0};
        ByteArrayOutputStream output=new ByteArrayOutputStream(){
            @Override public synchronized void write(byte[] b,int off,int len){writes[0]++;super.write(b,off,len);}
            @Override public synchronized void write(int b){writes[0]++;super.write(b);}
        };
        LanPeer peer=new LanPeer(new DataInputStream(fragments),new DataOutputStream(output),1);
        check(peer.exchange(7,-123456789012L)==(7|(128<<9)));check(writes[0]==1);
        check(output.size()==16);ByteBuffer sent=ByteBuffer.wrap(output.toByteArray());
        check(sent.getInt()==0);check(sent.getLong()==-123456789012L);check(sent.getInt()==7);
    }
    static void saves()throws Exception{
        Path root=Files.createTempDirectory("rbo-lan-test-");Path original=root.resolve("original"),copy=root.resolve("copy");
        Path source=Files.createDirectories(original.resolve("DATA/SaveData")),dest=Files.createDirectories(copy.resolve("DATA/SaveData"));
        byte[] config=new byte[328];ByteBuffer.wrap(config).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4c,77);
        Files.write(source.resolve("KeyConfig.v4"),config);Files.write(source.resolve("chr_data01.sv4"),new byte[]{1,2,3});
        Files.write(source.resolve("do-not-send.bak"),new byte[]{42});
        byte[] bundle=LanFiles.snapshot(original.toFile());LanFiles.install(copy.toFile(),bundle);
        check(Arrays.equals(Files.readAllBytes(source.resolve("KeyConfig.v4")),config));
        check(Arrays.equals(Files.readAllBytes(dest.resolve("chr_data01.sv4")),new byte[]{1,2,3}));check(!Files.exists(dest.resolve("do-not-send.bak")));
        ByteBuffer b=ByteBuffer.wrap(Files.readAllBytes(dest.resolve("KeyConfig.v4"))).order(ByteOrder.LITTLE_ENDIAN);
        for(int p=0;p<3;p++)for(int i=0;i<9;i++)check(b.getInt(4+p*108+72+i*4)==(p==0?LanFiles.P1[i]:p==1?LanFiles.P2[i]:0));
        for(String name:new String[]{"../outside","sub/file","..\\outside","C:outside"})rejected(()->LanFiles.install(copy.toFile(),zip(name,new byte[]{1})));
        check(!Files.exists(copy.resolve("DATA/outside")));
        Files.write(original.resolve("RBO_Ex3.exe"),new byte[]{9});Files.write(original.resolve("DATA/assets.dat"),new byte[]{10});
        Path cache=Files.createDirectory(root.resolve("cache"));Path stale=Files.createDirectories(cache.resolve("lan-old/DATA/SaveData"));
        Files.write(stale.resolve("temporary.sv4"),new byte[]{7});
        LanFiles.cleanup(cache.toFile());check(!Files.exists(cache.resolve("lan-old")));check(Files.exists(source.resolve("chr_data01.sv4")));
        // Test fixtures remain under the OS temporary directory.
    }
    public static void main(String[] args)throws Exception{
        connection();packet();saves();System.out.println("LAN: "+checks+" checks passed: P1/P2, real sockets, input/digest validation, temporary saves and traversal rejection");
        if(args.length==1){byte[] real=LanFiles.snapshot(new File(args[0]));System.out.println("Actual SaveData snapshot: "+real.length+" bytes");}
    }
}
