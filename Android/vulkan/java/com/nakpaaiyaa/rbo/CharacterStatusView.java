package com.nakpaaiyaa.rbo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Locale;

final class CharacterStatusView extends CompanionCanvas {
    private static final String[] STATS = {"STR", "AGI", "VIT", "INT", "DEX", "LUK"};
    private static final String[] LABELS = {"Strength", "Agility", "Vitality", "Intelligence", "Dexterity", "Luck"};
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
    private CompanionData data = CompanionData.empty();
    private int portraitCode = -1;
    private Bitmap portrait;

    CharacterStatusView(Context context) { super(context); }
    void update(CompanionData next) {
        data = next;
        int code = data.playerAvailable() ? data.player(1) : -1;
        if (code != portraitCode) {
            portraitCode = code;
            portrait = null;
            if (code >= 0) {
                try (InputStream stream = getContext().getAssets().open("move-backgrounds/" + CompanionData.portrait(code) + ".webp")) {
                    portrait = BitmapFactory.decodeStream(stream);
                } catch (IOException unavailable) { android.util.Log.w("RBO", "Character portrait unavailable", unavailable); }
            }
        }
        if (code >= 0) setContentDescription(AppLanguage.text(CompanionData.role(code))
                + ", " + AppLanguage.text("LEVEL") + " " + value(2) + ", HP " + value(4) + " / " + value(5)
                + ", SP " + value(6) + " / " + value(7));
        else setContentDescription(AppLanguage.text("Character status: waiting for gameplay"));
        invalidate();
    }
    private int value(int field) { return data.player(field); }
    private String number(int field) { return numbers.format(value(field)); }

    @Override void drawPage(Canvas c) {
        boolean ready = data.playerAvailable();
        card(c,12,12,264,446,0xFF262219,0xFF675333);
        if (portrait != null) {
            c.save();
            c.clipRect(13,13,275,457);
            int bottom = portrait.getHeight() - ("magician_male".equals(CompanionData.portrait(portraitCode)) ? 0 : 26);
            android.graphics.Rect source = new android.graphics.Rect(5,6,portrait.getWidth()-5,Math.max(7,bottom));
            // Portrait banners are wide. Fill the card width and retain the face.
            float scale = 390f / source.height();
            float left=144-source.width()*scale*.44f;
            box.set(left,38,left+source.width()*scale,428);
            paint.setColor(0xFFFFFFFF);
            paint.setColorFilter(new ColorMatrixColorFilter(new float[] {
                .52f,.29f,.08f,0,5, .17f,.61f,.07f,0,2, .12f,.24f,.47f,0,0, 0,0,0,1,0}));
            c.drawBitmap(portrait, source, box, paint); paint.setColorFilter(null);
            paint.setShader(new LinearGradient(0,210,0,450,new int[] {0x00191713,0xE6191713,0xFF191713},new float[] {0,.7f,1},Shader.TileMode.CLAMP));
            c.drawRect(13,13,275,457,paint);paint.setShader(null);
            c.restore();
        }
        text(c,"CHARACTER STATUS",30,42,12,GOLD,true);
        text(c,ready ? CompanionData.role(value(1)) : "Adventure awaits",30,377,27,WHITE,true);
        text(c,ready ? ((value(1)&1)!=0 ? "Female" : "Male") : "เลือกตัวละครแล้วเข้าเกม",30,404,15,MUTED,false);
        text(c,"RAGNAROK BATTLE OFFLINE",30,440,10,0xFF8F805F,true);

        text(c,"CHARACTER",300,38,12,GOLD,true);
        text(c,ready ? "Ready for battle" : "Waiting for character",300,75,29,WHITE,true);
        card(c,810,23,134,62,0xFF352D20,0xFF806338);
        text(c,"LEVEL",825,45,11,GOLD,true);
        right(c,ready ? number(2) : "—",928,72,29,WHITE);
        meter(c,300,111,644,"HP",4,5,0xFF8FC5A5,ready);
        meter(c,300,177,644,"SP",6,7,0xFF91BBD8,ready);

        for (int i=0;i<6;++i) {
            float x=300+(i%3)*218, y=246+(i/3)*76;
            card(c,x,y,208,66,0xFF25221C,0xFF49402E);
            text(c,STATS[i],x+15,y+24,15,GOLD,true);
            text(c,LABELS[i],x+15,y+47,11,MUTED,false);
            right(c,ready ? number(8+i) : "—",x+192,y+43,29,WHITE);
        }
        footer(c,300,"EXP",ready ? number(3) : "—");
        footer(c,518,"STAT POINTS",ready ? number(14) : "—");
        footer(c,736,"SKILL POINTS",ready ? number(15) : "—");
    }
    private void footer(Canvas c,int x,String label,String value) {
        text(c,label,x,425,10,MUTED,true);
        text(c,value,x,451,20,WHITE,true);
    }
    private void meter(Canvas c,float x,float y,float width,String label,int current,int max,int color,boolean ready) {
        text(c,label,x,y,15,color,true);
        right(c,ready ? number(current)+" / "+number(max) : "— / —",x+width,y,17,WHITE);
        box.set(x,y+13,x+width,y+29); paint.setColor(0xFF363027);c.drawRoundRect(box,8,8,paint);
        if (ready && value(max)>0) {
            float ratio=Math.max(0,Math.min(1,value(current)/(float)value(max)));
            if (ratio>0) {
                box.right=x+width*ratio;
                paint.setShader(new LinearGradient(x,0,x+width,0,0xFF416957,color,Shader.TileMode.CLAMP));
                c.drawRoundRect(box,8,8,paint);paint.setShader(null);
            }
        }
    }
}
