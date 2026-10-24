import dev.thida.PixelSprites;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Rebuild textures from the same pixel definitions used by the HUD. Run from project root. */
public final class ExportPixelArt {
    public static void main(String[] args) throws Exception {
        Path root=Path.of("src/main/resources/assets/thida");
        Files.createDirectories(root.resolve("textures/item"));
        Files.createDirectories(root.resolve("textures/gui"));
        Files.createDirectories(Path.of("build/art"));
        BufferedImage sheet=new BufferedImage(576,56+((PixelSprites.ALL.size()+3)/4)*140+12,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=sheet.createGraphics();
        g.setColor(new Color(0x211D2D)); g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setColor(new Color(0xE2BC79)); g.setFont(new Font("Monospaced",Font.BOLD,22));
        g.drawString("THIDA / PIXEL RELICS",24,34);
        int i=0;
        for(var entry:PixelSprites.ALL.entrySet()) {
            BufferedImage icon=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            icon.setRGB(0,0,16,16,entry.getValue(),0,16);
            ImageIO.write(icon,"png",root.resolve("textures/gui/"+entry.getKey()+".png").toFile());
            String item=switch(entry.getKey()) {
                case "nilakan","narotra","parasite" -> entry.getKey()+"_doll";
                case "heart" -> "heart_doll";
                case "domain" -> "domain_talisman";
                default -> null;
            };
            if(item!=null) {
                ImageIO.write(icon,"png",root.resolve("textures/item/"+item+".png").toFile());
                Files.writeString(root.resolve("models/item/"+item+".json"),
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"thida:item/"+item+"\"}}\n");
            }
            int x=24+(i%4)*138,y=56+(i/4)*140;
            g.setColor(new Color(0x382C44));g.fillRect(x,y,120,128);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(icon,x+12,y+6,96,96,null);
            g.setColor(new Color(0xF5E8D2));g.setFont(new Font("Monospaced",Font.PLAIN,14));
            g.drawString(entry.getKey(),x+8,y+118);
            i++;
        }
        g.dispose();
        ImageIO.write(sheet,"png",Path.of("build/art/pixel-relics.png").toFile());
        System.out.println("Exported "+i+" pixel icons, five item textures, and contact sheet.");
    }
}

