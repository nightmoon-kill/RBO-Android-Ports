package com.nakpaaiyaa.rbo;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Experimental two-machine input lockstep; host=P1, joining peer=P2. */
public final class LanGame {
    private static int pendingRole;private static String pendingAddress="",pendingCode="";
    private static volatile int role,keys;private static LanPeer peer;
    private static volatile boolean ready,preparing,cancelled;
    private static volatile Socket socket;private static volatile ServerSocket server;
    private static DataInputStream input;private static DataOutputStream output;
    private static File session;private static Activity game;private static AlertDialog waiting;
    private static Thread worker;private static final CountDownLatch start=new CountDownLatch(1);
    private static final int[] scans=LanFiles.P1.clone();
    private static final Handler ui=new Handler(Looper.getMainLooper());
    private LanGame() {}
    private static String text(String en,String th){return AppLanguage.isThai()?th:en;}

    static void menu(Activity activity,Button play){
        new AlertDialog.Builder(activity).setTitle(text("LAN multiplayer","เล่นร่วมกันผ่าน LAN"))
            .setItems(new String[]{text("Create room · P1","สร้างห้อง · P1"),text("Join room · P2","เข้าร่วมห้อง · P2")},(d,which)->{
                LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(24,12,24,12);
                TextView info=new TextView(activity);
                info.setText(text("Use the same Wi-Fi. Game files are not compared. LAN uses a temporary copy of the host's saves. Cheats and Save States are disabled. Intro is skipped. Both machines run the game.","ใช้ Wi-Fi เดียวกัน โดยไม่ตรวจเทียบไฟล์เกม เล่นด้วยสำเนาเซฟชั่วคราวของเจ้าห้อง ปิดสูตรและ Save State และข้ามอินโทร ทั้งสองเครื่องรันเกมเอง"));box.addView(info);
                EditText address=new EditText(activity);address.setSingleLine();address.setHint("192.168.1.10");
                EditText code=new EditText(activity);code.setSingleLine();code.setInputType(2);code.setHint(text("6-digit room code","รหัสห้อง 6 หลัก"));
                String pin=String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));
                if(which==0){TextView host=new TextView(activity);host.setText("P1 · "+addresses()+"\n"+text("Room code: ","รหัสห้อง: ")+pin);host.setTextIsSelectable(true);box.addView(host);}
                else {box.addView(address);box.addView(code);}
                AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(text(which==0?"Create room · P1":"Join room · P2",which==0?"สร้างห้อง · P1":"เข้าร่วมห้อง · P2")).setView(box)
                    .setPositiveButton(text("Continue","ดำเนินการต่อ"),null).setNegativeButton(text("Cancel","ยกเลิก"),null).create();
                dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
                    String ip=address.getText().toString().trim(),room=which==0?pin:code.getText().toString().trim();
                    if(!room.matches("[0-9]{6}")||(which==1&&!validAddress(ip))){code.setError(text("Enter IPv4 and a 6-digit code","กรอก IPv4 และรหัส 6 หลัก"));return;}
                    if(!play.isEnabled()){Toast.makeText(activity,text("Import the game first","นำเข้าเกมก่อน"),1).show();return;}
                    pendingRole=which==0?1:2;pendingAddress=ip;pendingCode=room;dialog.dismiss();
                    play.performClick();pendingRole=0;
                }));dialog.show();
            }).show();
    }
    static boolean validAddress(String ip){
        String[] parts=ip.split("\\.",-1);if(parts.length!=4)return false;
        for(String s:parts)try{if(!s.matches("[0-9]{1,3}")||Integer.parseInt(s)>255)return false;}catch(NumberFormatException bad){return false;}
        return !ip.equals("0.0.0.0")&&!ip.equals("255.255.255.255");
    }
    private static String addresses(){
        ArrayList<String> found=new ArrayList<>();try{Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()){NetworkInterface n=interfaces.nextElement();if(!n.isUp()||n.isLoopback())continue;
                Enumeration<InetAddress> ips=n.getInetAddresses();while(ips.hasMoreElements()){InetAddress a=ips.nextElement();if(a instanceof Inet4Address)found.add(a.getHostAddress());}}
        }catch(Exception ignored){}return found.isEmpty()?text("Check Wi-Fi IP in Android settings","ดู IP ของ Wi-Fi ในการตั้งค่า Android"):android.text.TextUtils.join(" / ",found);
    }
    public static void badge(Activity activity,FrameLayout frame){
        int player=activity.getIntent().getIntExtra("lan_role",0);if(player==0)return;
        TextView label=new TextView(activity);label.setText("LAN · P"+player);label.setTextColor(0xfff1d391);label.setTextSize(13);
        label.setPadding(12,6,12,6);label.setBackgroundColor(0x99000000);
        frame.addView(label,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT));
    }
    static void putSelection(Intent intent){if(pendingRole==0)return;intent.putExtra("lan_role",pendingRole).putExtra("lan_address",pendingAddress).putExtra("lan_code",pendingCode);}
    public static boolean active(){return role!=0;}
    public static boolean awaitReady(Activity activity){
        int selected=activity.getIntent().getIntExtra("lan_role",0);if(selected!=1&&selected!=2)return false;
        role=selected;if(ready)return false;if(preparing)return true;
        preparing=true;game=activity;
        waiting=new AlertDialog.Builder(activity).setTitle("LAN · P"+role)
            .setMessage(text("Preparing LAN room…","กำลังเตรียมห้อง LAN…")).setCancelable(false)
            .setPositiveButton(text("Start game","เริ่มเกม"),null)
            .setNegativeButton(text("Leave room","ออกจากห้อง"),(d,w)->{cancel();activity.finish();}).create();
        waiting.setOnShowListener(d->{waiting.getButton(-1).setEnabled(false);waiting.getButton(-1).setOnClickListener(v->start.countDown());});waiting.show();
        worker=new Thread(()->prepare(activity),"RBO LAN setup");worker.start();return true;
    }
    private static void status(String message){ui.post(()->{if(waiting!=null&&waiting.isShowing())waiting.setMessage(message);});}
    private static void prepare(Activity activity){
        try{
            File imported=new File(activity.getFilesDir(),"game");
            // Resolve the actual imported root through the existing package-private helper.
            Class<?> files=Class.forName("com.nakpaaiyaa.rbo.GameFiles");
            java.lang.reflect.Method root=files.getDeclaredMethod("root",File.class);root.setAccessible(true);
            imported=(File)root.invoke(null,activity.getFilesDir());
            if(cancelled)return;
            session=LanFiles.session(imported,activity.getCacheDir());
            String code=activity.getIntent().getStringExtra("lan_code");
            if(role==1){
                server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(LanProtocol.PORT));server.setSoTimeout(120000);
                status(text("Waiting for P2. IP: ","กำลังรอ P2 · IP: ")+addresses()+"\n"+text("Room code: ","รหัสห้อง: ")+code);
                while(!cancelled){
                    Socket candidate=server.accept();socket=candidate;candidate.setSoTimeout(10000);candidate.setTcpNoDelay(true);
                    DataInputStream nextIn=new DataInputStream(candidate.getInputStream());DataOutputStream nextOut=new DataOutputStream(candidate.getOutputStream());
                    try{LanProtocol.accept(nextIn,nextOut,code);input=nextIn;output=nextOut;break;}
                    catch(IOException invalid){android.util.Log.w("RBO LAN",invalid.getMessage());candidate.close();socket=null;}
                }
                if(cancelled)return;server.close();server=null;
                status(text("P2 connected. Preparing temporary saves…","P2 เชื่อมต่อแล้ว กำลังเตรียมเซฟชั่วคราว…"));
                byte[] snapshot=LanFiles.snapshot(imported);LanFiles.install(session,snapshot);
                output.writeInt(snapshot.length);output.write(snapshot);output.flush();socket.setSoTimeout(120000);
                if(input.readInt()!=LanProtocol.START)throw new IOException("P2 was not ready");
                status(text("P1 · Host ready\nP2 · Connected and ready\nSelect Start game","P1 · เจ้าห้องพร้อม\nP2 · เชื่อมต่อและพร้อมแล้ว\nกดเริ่มเกม"));
                ui.post(()->{if(waiting!=null)waiting.getButton(-1).setEnabled(true);});
                if(!start.await(120,TimeUnit.SECONDS))throw new IOException("Room start timed out");
                output.writeInt(LanProtocol.START);output.flush();
            }else{
                status(text("Connecting to P1…","กำลังเชื่อมต่อ P1…"));
                long deadline=System.nanoTime()+90000000000L;
                while(!cancelled){
                    Socket next=new Socket();socket=next;
                    try{next.connect(new InetSocketAddress(activity.getIntent().getStringExtra("lan_address"),LanProtocol.PORT),1500);break;}
                    catch(IOException fail){next.close();if(System.nanoTime()>deadline)throw fail;Thread.sleep(500);}
                }
                if(cancelled)return;socket.setTcpNoDelay(true);socket.setSoTimeout(120000);
                input=new DataInputStream(socket.getInputStream());output=new DataOutputStream(socket.getOutputStream());
                LanProtocol.hello(output,code);LanProtocol.assignedRole(input);
                status(text("Receiving temporary saves from P1…","กำลังรับเซฟชั่วคราวจาก P1…"));
                LanFiles.install(session,LanProtocol.payload(input));output.writeInt(LanProtocol.START);output.flush();
                status(text("P2 ready. Waiting for P1 to start…","P2 พร้อมแล้ว กำลังรอ P1 เริ่มเกม…"));
                if(input.readInt()!=LanProtocol.START)throw new IOException("Host did not start the game");
            }
            socket.setSoTimeout(10000);readBindings(activity);keys=0;peer=new LanPeer(input,output,role);ready=true;
            ui.post(()->{if(cancelled||activity.isFinishing())return;waiting.dismiss();waiting=null;
                try{java.lang.reflect.Method launch=activity.getClass().getDeclaredMethod("startGame");launch.setAccessible(true);launch.invoke(activity);
                    java.lang.reflect.Method sync=activity.getClass().getDeclaredMethod("syncGameState");sync.setAccessible(true);sync.invoke(activity);}
                catch(Exception failure){failed(failure);}
            });
        }catch(Exception failure){failed(failure);}
    }
    private static void readBindings(Activity activity){
        try{byte[] bytes=Files.readAllBytes(new File(activity.getFilesDir(),"player-one-controls").toPath());
            if(bytes.length==76){ByteBuffer b=ByteBuffer.wrap(bytes);if(b.getInt()==1)for(int i=0;i<9;i++){scans[i]=b.getInt();b.getInt();}}
        }catch(IOException ignored){}
    }
    public static void key(int scan,boolean down){
        if(!active()){keyNative(scan,down);return;}
        synchronized(scans){for(int i=0;i<9;i++)if(scans[i]==scan){if(down)keys|=1<<i;else keys&=~(1<<i);}}
    }
    // Called only on the emulator thread; socket close wakes a blocked exchange.
    static int exchange(long digest){
        if(cancelled||!ready)return -1;
        try{
            return peer.exchange(keys,digest);
        }catch(IOException failure){failed(failure);return -1;}
    }
    public static int start(String root,String log,boolean widescreen){
        System.loadLibrary("rbo_vulkan");
        return startNative(active()?session.getAbsolutePath():root,log,widescreen,active());
    }
    public static void background(Activity activity){
        if(active()&&!activity.isChangingConfigurations()){
            if(activity.isFinishing())cancel();else failed(new IOException("LAN ended because the app went to the background"));
        }
    }
    public static void cancel(){cancelled=true;keys=0;
        try{if(socket!=null)socket.close();}catch(IOException ignored){}
        try{if(server!=null)server.close();}catch(IOException ignored){}
        if(worker!=null&&worker!=Thread.currentThread())worker.interrupt();
    }
    public static void destroy(){cancel();ui.postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),150);}
    public static boolean keepErrorVisible(){return active()&&cancelled;}
    private static void failed(Exception failure){
        if(cancelled)return;cancel();String reason=failure.getMessage();android.util.Log.e("RBO LAN","LAN stopped",failure);
        ui.post(()->{if(waiting!=null){waiting.dismiss();waiting=null;}
            if(game!=null&&!game.isFinishing())new AlertDialog.Builder(game).setTitle(text("LAN session ended","สิ้นสุดการเล่น LAN"))
                .setMessage(text("Could not continue LAN: ","ไม่สามารถเล่น LAN ต่อได้: ")+reason)
                .setPositiveButton("OK",(d,w)->game.finish()).show();
        });
    }
    private static native int startNative(String root,String log,boolean widescreen,boolean lan);
    private static native void keyNative(int scan,boolean down);
}
