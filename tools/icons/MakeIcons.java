import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Generates launcher PNGs from the supplied artwork. Run with JDK 17 source mode. */
public class MakeIcons {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: MakeIcons <source.png> <resDir>");
            System.exit(2);
        }
        BufferedImage source = ImageIO.read(new File(args[0]));
        int[][] sizes = {
            {48, 0}, {72, 1}, {96, 2}, {144, 3}, {192, 4},
        };
        String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
        for (int[] entry : sizes) {
            int size = entry[0];
            String dir = new File(args[1], dirs[entry[1]]).getAbsolutePath();
            File outDir = new File(dir);
            outDir.mkdirs();
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, size, size, null);
            g.dispose();
            ImageIO.write(scaled, "png", new File(outDir, "ic_launcher.png"));
            System.out.println("wrote " + outDir + "/ic_launcher.png " + size + "px");
        }
    }
}
