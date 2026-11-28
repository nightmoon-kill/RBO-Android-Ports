package com.nakpaaiyaa.rbo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure formatter shared by native-canvas UI and the original Android menus. */
final class UiText {
    private static volatile boolean thai;
    private static final Map<String,String[]> aliases = new HashMap<>();
    private static final Pattern fragments;
    static {
        for (String[] pair : AppLanguageCatalog.PAIRS) {
            if (!aliases.containsKey(pair[0])) aliases.put(pair[0],pair);
            if (!aliases.containsKey(pair[1])) aliases.put(pair[1],pair);
        }
        List<String> keys = new ArrayList<>(aliases.keySet());
        Collections.sort(keys,(a,b) -> Integer.compare(b.length(),a.length()));
        StringBuilder regex = new StringBuilder();
        for (String key : keys) {
            if (regex.length()>0) regex.append('|');
            if (asciiLetter(key.charAt(0))) regex.append("(?<![A-Za-z])");
            regex.append(Pattern.quote(key));
            if (asciiLetter(key.charAt(key.length()-1))) regex.append("(?![A-Za-z])");
        }
        fragments=Pattern.compile(regex.toString());
    }
    private static boolean asciiLetter(char c) { return c>='A'&&c<='Z'||c>='a'&&c<='z'; }
    static void setThai(boolean selected) { thai=selected; }
    static boolean isThai() { return thai; }
    static String text(String source) {
        if (source==null||source.isEmpty()) return source;
        if (source.equals("Ports by นักป้ายยา")) source="Port by Nakpaaiyaa";
        if (source.equals("เครดิต Monster แปล")) source="Thai translation: Monster";
        if (source.equals("Player 1 controls")) source="Character controls";
        if (source.equals("Player 1 Cheats")) source="Cheats";
        if (source.startsWith("Player 1 setup: ")) source="Controls: "+source.substring("Player 1 setup: ".length());
        String[] exact=aliases.get(source);
        if(exact!=null)return exact[thai?1:0];
        Matcher match=fragments.matcher(source);
        StringBuffer result=new StringBuffer();
        while(match.find())match.appendReplacement(result,Matcher.quoteReplacement(aliases.get(match.group())[thai?1:0]));
        match.appendTail(result);
        return result.toString();
    }
}
