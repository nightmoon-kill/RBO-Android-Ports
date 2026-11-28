package com.nakpaaiyaa.rbo;

public final class LocalizationTest {
    private static int checks;
    private static void equal(String expected,String actual) {
        ++checks;
        if(!expected.equals(actual))throw new AssertionError("Expected "+expected+"; got "+actual);
    }
    public static void main(String[] args) {
        if(UiText.isThai())throw new AssertionError("The default must be ENG");
        equal("OPTIONS",UiText.text("ตัวเลือก"));
        equal("No spawned enemies remain in this area",UiText.text("ไม่มีมอนสเตอร์ที่เกิดแล้วเหลืออยู่ในพื้นที่นี้"));
        equal("Version 0.6.2",UiText.text("เวอร์ชัน 0.6.2"));
        equal("Slot 2 · Empty",UiText.text("Slot 2 · Empty"));
        UiText.setThai(true);
        equal("ตัวเลือก",UiText.text("OPTIONS"));
        equal("ภาษา",UiText.text("Language"));
        equal("รายการท่า",UiText.text("Move List"));
        equal("ช่อง 2 · ว่าง",UiText.text("Slot 2 · Empty"));
        equal("บันทึกแล้วในช่อง 3.",UiText.text("Saved to slot 3."));
        equal("โจมตีเบา ปุ่มในเกม",UiText.text("Weak attack game key"));
        equal("64×64",UiText.text("64×64"));
        equal("libvulkan_adreno.so",UiText.text("libvulkan_adreno.so"));
        equal("RBO Android",UiText.text("RBO Android"));
        UiText.setThai(false);
        equal("Save State",UiText.text("บันทึกสถานะเกม"));
        equal("Slot 2 · Empty",UiText.text("ช่อง 2 · ว่าง"));
        equal("Renderer · Vulkan",UiText.text("การแสดงผล · Vulkan"));
        equal("Choose folder",UiText.text("เลือกโฟลเดอร์"));
        equal("Port by Nakpaaiyaa",UiText.text("Ports by นักป้ายยา"));
        equal("Character controls",UiText.text("Player 1 controls"));
        for(String[] pair:AppLanguageCatalog.PAIRS){
            String english=UiText.text(pair[1]);
            for(int i=0;i<english.length();++i)if(english.charAt(i)>='\u0e00'&&english.charAt(i)<='\u0e7f')
                throw new AssertionError("Thai leaked into ENG: "+english);
        }
        System.out.println("Language formatter checks passed: "+checks);
    }
}
