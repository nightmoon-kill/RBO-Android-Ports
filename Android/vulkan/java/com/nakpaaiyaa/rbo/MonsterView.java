package com.nakpaaiyaa.rbo;

import android.content.Context;
import android.graphics.Canvas;

/** Live count of spawned, living enemies in the current area. */
final class MonsterView extends CompanionCanvas {
    private CompanionData data = CompanionData.empty();
    MonsterView(Context context) { super(context); }
    void update(CompanionData next) {
        data = next;
        setContentDescription(AppLanguage.text("Monsters remaining") + ": "
                + (data.monstersAvailable() ? data.monsterCount() : AppLanguage.text("Waiting for gameplay")));
        invalidate();
    }
    @Override void drawPage(Canvas c) {
        boolean ready = data.monstersAvailable();
        boolean clear = ready && data.monsterCount() == 0;
        text(c,"FIELD REPORT",28,35,12,GOLD,true);
        text(c,"Monsters in this area",28,78,30,WHITE,true);
        right(c,ready ? "LIVE FIELD COUNT" : "Waiting for gameplay",932,40,12,MUTED);
        card(c,28,109,904,294,0xFF26231D,clear ? 0xFF557657 : 0xFF6D5734);
        text(c,"Monsters remaining",58,150,20,MUTED,false);
        String count = ready ? Integer.toString(data.monsterCount()) : "—";
        paint.setTextSize(132);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        text(c,count,480-paint.measureText(count)/2,293,132,clear ? 0xFFB3D9A7 : WHITE,true);
        String state = ready ? (clear ? "Area clear" : "Battle in progress") : "Waiting for gameplay";
        paint.setTextSize(21);
        String translated = AppLanguage.text(state);
        text(c,translated,480-paint.measureText(translated)/2,359,21,clear ? 0xFFB3D9A7 : GOLD,true);
        text(c,"SPAWNED ENEMIES ONLY",28,438,11,GOLD,true);
        text(c,"Future waves are not included",28,460,12,MUTED,false);
    }
}
