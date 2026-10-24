package dev.thida;

import java.util.LinkedHashMap;
import java.util.Map;

/** Hand-authored 16x16 sprites. Shared by the game renderer and the texture exporter. */
public final class PixelSprites {
    public static final Map<String, int[]> ALL = new LinkedHashMap<>();
    private static final int INK=0xFF241D35, STRAW=0xFFBA8358, LIGHT=0xFFFFD58A, DARK=0xFF705046;
    static {
        doll("nilakan", 0xFFAB70D4, 0xFFF5B1EF);
        doll("narotra", 0xFF61B8A5, 0xFFB3F3CA);
        Sprite p = new Sprite();
        p.rect(6,1,4,3,INK).rect(5,3,6,5,INK).rect(6,3,4,4,0xFFDC9367)
                .rect(7,4,1,1,LIGHT).rect(9,4,1,1,INK).rect(4,8,8,2,STRAW)
                .rect(6,7,4,6,INK).rect(7,8,2,4,0xFFE47570).rect(7,12,1,3,STRAW)
                .rect(4,11,2,2,STRAW).rect(3,12,1,3,DARK).rect(10,11,2,2,STRAW).rect(12,12,1,3,DARK);
        ALL.put("parasite", p.pixels);
        p = new Sprite();
        p.rect(2,3,5,6,INK).rect(9,3,5,6,INK).rect(4,7,8,4,INK).rect(6,11,4,2,INK).rect(7,13,2,1,INK)
                .rect(3,4,3,4,0xFFF491AC).rect(10,4,3,4,0xFFD85D96).rect(4,6,8,4,0xFFD85D96)
                .rect(6,10,4,2,0xFFAB477B).rect(7,12,2,1,0xFFAB477B).rect(4,4,2,2,0xFFFFD7DE)
                .rect(1,10,5,1,LIGHT).rect(5,9,4,1,LIGHT).rect(9,8,6,1,LIGHT).rect(12,9,2,1,STRAW);
        ALL.put("heart",p.pixels);
        p = new Sprite();
        p.line(2,11,4,5,0xFF7E69BC).line(4,5,7,2,0xFFB9A0F5).line(7,2,10,3,0xFFB9A0F5)
                .line(10,3,13,9,0xFF7E69BC).rect(2,11,12,2,INK).rect(3,11,10,1,0xFFAD91DF)
                .rect(5,13,6,1,0xFF655391).rect(7,5,2,5,LIGHT).rect(5,7,6,1,LIGHT)
                .rect(0,8,2,1,LIGHT).rect(1,7,1,3,LIGHT).rect(13,2,3,1,LIGHT).rect(14,1,1,3,LIGHT);
        ALL.put("domain", p.pixels);
        shield("shield",0xFF70C6B0,false); shield("vulnerable",0xFFDB778F,true);
        p = new Sprite();
        p.rect(7,1,2,13,0xFF91D8E5).rect(2,6,12,2,0xFF91D8E5).rect(5,4,6,6,0xFF91D8E5)
                .rect(7,4,2,7,0xFFF3FFFF).rect(5,6,6,2,0xFFF3FFFF).rect(1,1,3,1,LIGHT).rect(2,0,1,3,LIGHT)
                .rect(12,12,3,1,LIGHT).rect(13,11,1,3,LIGHT);
        ALL.put("cleanse",p.pixels);
        p = new Sprite();
        p.rect(8,1,4,2,INK).rect(6,3,5,3,INK).rect(4,6,7,3,INK).rect(7,9,4,2,INK)
                .line(8,10,5,14,INK).rect(8,2,3,2,LIGHT).rect(6,4,4,3,0xFFF7B85B)
                .rect(5,7,6,1,LIGHT).rect(8,8,2,2,0xFFF7B85B).line(8,10,6,12,LIGHT)
                .rect(1,5,3,1,0xFF988064).rect(0,9,3,1,0xFF988064);
        ALL.put("speed",p.pixels);
        p = new Sprite();
        p.line(4,10,12,2,INK).line(5,11,13,3,INK).line(5,9,12,2,0xFFC5C3DC)
                .line(6,9,12,3,0xFF827EA8).rect(7,6,3,1,INK).line(3,9,7,13,LIGHT)
                .line(2,12,4,14,STRAW).rect(1,13,2,2,DARK).rect(11,10,3,1,0xFFE77E8E)
                .rect(12,11,2,1,0xFFE77E8E).rect(12,12,1,2,0xFFE77E8E);
        ALL.put("weak",p.pixels);
        p = new Sprite();
        p.rect(7,1,2,3,INK).rect(6,3,4,3,INK).rect(4,6,8,6,INK).rect(6,12,4,2,INK)
                .rect(7,3,2,3,0xFFED8C9A).rect(6,6,4,6,0xFFC54869).rect(5,8,6,3,0xFFC54869)
                .rect(6,7,1,3,0xFFFFBCC7).rect(7,12,2,1,0xFF943857)
                .rect(1,5,3,1,0xFFAB91D5).rect(12,5,3,1,0xFFAB91D5);
        ALL.put("bleed",p.pixels);
        p = new Sprite();
        p.rect(3,3,5,2,STRAW).rect(2,5,2,5,STRAW).rect(4,10,3,2,STRAW)
                .rect(9,4,3,2,STRAW).rect(12,6,2,5,STRAW).rect(8,11,4,2,STRAW)
                .line(3,13,12,2,INK).line(4,13,13,2,0xFFE78394);
        ALL.put("unlink",p.pixels);
        p = new Sprite();
        p.rect(5,2,6,2,0xFFD1BD92).rect(4,4,2,4,0xFFD1BD92).rect(10,4,2,4,0xFFD1BD92)
                .rect(3,7,10,7,INK).rect(4,8,8,5,0xFF9C8261).rect(7,9,2,2,LIGHT).rect(7,11,1,1,LIGHT);
        ALL.put("lock",p.pixels);
    }
    private static void doll(String name,int cloth,int gem) {
        Sprite s = new Sprite();
        s.rect(5,1,6,1,INK).rect(4,2,8,5,INK).rect(5,2,6,4,STRAW).rect(5,2,2,1,LIGHT)
                .rect(6,4,1,1,INK).rect(9,4,1,1,gem).rect(7,6,2,1,DARK)
                .rect(1,7,14,2,INK).rect(2,7,12,1,STRAW).rect(5,7,6,6,INK)
                .rect(6,7,4,5,cloth).rect(7,8,2,2,gem).rect(5,12,2,3,DARK).rect(9,12,2,3,DARK)
                .rect(5,12,1,2,LIGHT).rect(9,12,1,2,LIGHT).rect(3,8,1,2,STRAW).rect(12,8,1,2,STRAW);
        ALL.put(name,s.pixels);
    }
    private static void shield(String name,int color,boolean crack) {
        Sprite s = new Sprite();
        s.rect(3,2,10,8,INK).rect(4,10,8,2,INK).rect(6,12,4,2,INK)
                .rect(4,3,8,6,color).rect(5,9,6,2,color).rect(7,11,2,2,color)
                .rect(5,3,2,5,0xFFD1F0DA).rect(7,5,3,1,LIGHT).rect(8,4,1,5,LIGHT);
        if(crack) s.line(9,1,7,6,INK).line(7,6,10,8,INK).line(10,8,7,13,INK);
        ALL.put(name,s.pixels);
    }
    private static final class Sprite {
        final int[] pixels = new int[256];
        Sprite rect(int x,int y,int w,int h,int c) {
            for(int j=y;j<y+h;j++) for(int i=x;i<x+w;i++) if(i>=0&&i<16&&j>=0&&j<16) pixels[j*16+i]=c;
            return this;
        }
        Sprite line(int x,int y,int ex,int ey,int c) {
            int steps=Math.max(Math.abs(ex-x),Math.abs(ey-y));
            for(int i=0;i<=steps;i++) rect(Math.round(x+(ex-x)*i/(float)Math.max(1,steps)),Math.round(y+(ey-y)*i/(float)Math.max(1,steps)),1,1,c);
            return this;
        }
    }
    private PixelSprites() {}
}
