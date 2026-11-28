package com.nakpaaiyaa.rbo;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Disposable LAN saves: neither peer overwrites the imported game's saves. */
final class LanFiles {
    static final int[] P1 = {0xc8,0xd0,0xcb,0xcd,0x1e,0x1f,0x20,0x21,0x02};
    static final int[] P2 = {0x48,0x50,0x4b,0x4d,0x4f,0x4c,0x51,0x52,0x03};
    private static final int LIMIT = 512*1024*1024;
    static File child(File root,String name) throws IOException {
        File[] list=root.listFiles();
        if(list!=null) for(File f:list) if(f.getName().equalsIgnoreCase(name)) return f;
        return new File(root,name);
    }
    static File session(File imported, File cache) throws IOException {
        cleanup(cache);
        File session=Files.createTempDirectory(cache.toPath(),"lan-").toFile();
        File data=child(imported,"DATA");
        for(File f:sorted(imported)) if(!f.equals(data)) link(session,f);
        File target=new File(session,data.getName());
        if(!target.mkdir()) throw new IOException("Cannot create LAN DATA directory");
        for(File f:sorted(data)) if(!f.getName().equalsIgnoreCase("SaveData")) link(target,f);
        if(!new File(target,"SaveData").mkdir()) throw new IOException("Cannot create LAN save directory");
        return session;
    }
    static void cleanup(File cache) throws IOException {
        Path base=cache.getCanonicalFile().toPath();
        try(DirectoryStream<Path> dirs=Files.newDirectoryStream(base,"lan-*")){
            for(Path dir:dirs){
                if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)||!dir.toFile().getCanonicalFile().toPath().startsWith(base))continue;
                try(java.util.stream.Stream<Path> paths=Files.walk(dir)){
                    Iterator<Path> reverse=paths.sorted(Comparator.reverseOrder()).iterator();
                    while(reverse.hasNext()){
                        Path path=reverse.next().toAbsolutePath().normalize();
                        if(!path.startsWith(dir.toAbsolutePath().normalize()))throw new IOException("Invalid LAN cache path");
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }
    private static void link(File parent,File target) throws IOException {
        Files.createSymbolicLink(new File(parent,target.getName()).toPath(),target.toPath().toAbsolutePath());
    }
    static File saves(File root) throws IOException {return child(child(root,"DATA"),"SaveData");}
    static File[] sorted(File dir) throws IOException {
        File[] list=dir.listFiles();if(list==null) throw new IOException("Cannot read "+dir.getName());
        Arrays.sort(list,Comparator.comparing(File::getName));return list;
    }
    static byte[] snapshot(File original) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int total=0;
        try(ZipOutputStream zip=new ZipOutputStream(new FilterOutputStream(bytes){
            int written;
            @Override public void write(byte[] b,int off,int len)throws IOException{
                if(len>LanProtocol.MAX_PAYLOAD-written)throw new IOException("LAN save snapshot exceeds 32 MiB compressed");
                out.write(b,off,len);written+=len;
            }
            @Override public void write(int b)throws IOException{write(new byte[]{(byte)b},0,1);}
        })){
            for(File f:sorted(saves(original))){
                String name=f.getName().toLowerCase(Locale.ROOT);
                if(!f.isFile()||name.endsWith(".bak")||name.endsWith(".png")||name.endsWith(".state")||name.endsWith(".tmp"))continue;
                if(f.length()>LIMIT-total)throw new IOException("LAN saves exceed 512 MiB");
                zip.putNextEntry(new ZipEntry(f.getName()));
                try(InputStream in=new FileInputStream(f)){
                    byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                        if(n>LIMIT-total)throw new IOException("LAN saves exceed 512 MiB");total+=n;zip.write(block,0,n);
                    }
                }
                zip.closeEntry();
            }
        }
        if(bytes.size()>LanProtocol.MAX_PAYLOAD)throw new IOException("LAN save snapshot is too large");return bytes.toByteArray();
    }
    static void install(File session,byte[] bytes) throws IOException {
        File target=saves(session);int total=0,count=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry entry;Set<String> names=new HashSet<>();byte[] block=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();
                if(++count>2048||entry.isDirectory()||name.contains("/")||name.contains("\\")||name.contains(":")||name.equals(".")||name.equals("..")||!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Invalid LAN save entry");
                try(OutputStream out=new FileOutputStream(new File(target,name))){int n;while((n=zip.read(block))!=-1){total+=n;if(total>LIMIT)throw new IOException("LAN saves exceed 512 MiB");out.write(block,0,n);}}
            }
        }
        configure(target);
    }
    static void configure(File target) throws IOException {
        // Three records: 9 keyboard types, 9 device indexes, 9 DIK codes each.
        for(String name:new String[]{"KeyConfig.cdt","KeyConfig.v2","KeyConfig.v3","KeyConfig.v4"}){
            File file=child(target,name);byte[] bytes=file.isFile()?Files.readAllBytes(file.toPath()):new byte[328];
            if(bytes.length!=328||ByteBuffer.wrap(bytes).getInt()!=0)throw new IOException("Unrecognized key configuration");
            ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for(int p=0;p<3;p++)for(int i=0;i<9;i++){
                b.putInt(4+p*108+i*4,1);b.putInt(4+p*108+36+i*4,0);
                b.putInt(4+p*108+72+i*4,p==0?P1[i]:p==1?P2[i]:0);
            }
            Files.write(file.toPath(),bytes);
        }
    }
}
