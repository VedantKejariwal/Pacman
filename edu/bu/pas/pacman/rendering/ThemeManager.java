package edu.bu.pas.pacman.rendering;

import edu.bu.pas.pacman.utils.Coordinate;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/*
 * Asset folder contract:
 * - assets/pacman_face.png
 * - assets/ghost_1.png
 * - assets/ghost_2.png
 * - assets/ghost_3.png
 * - assets/ghost_4.png
 * - assets/ghost_scared.png
 * - assets/logo_1.png
 * - assets/logo_2.png
 * - assets/logo_3.png
 * - assets/logo_4.png
 * - assets/logo_5.png
 *
 * Replace any of these PNG files with your own artwork and the game will pick
 * them up dynamically without changing the code. Recommended starting size:
 * 32x32 for logos and 64x64 for entity sprites.
 */
public class ThemeManager
{
    private static final String ASSET_FOLDER = "assets";

    // These arrays make the code data-driven:
    // adding or removing filenames here changes what the renderer loads.
    private static final String[] GHOST_FILENAMES = {
        "ghost_1.png",
        "ghost_2.png",
        "ghost_3.png",
        "ghost_4.png"
    };

    private static final String[] LOGO_FILENAMES = {
        "logo_1.png",
        "logo_2.png",
        "logo_3.png",
        "logo_4.png",
        "logo_5.png"
    };

    private final BufferedImage pacmanImage;
    private final BufferedImage scaredGhostImage;
    private final List<BufferedImage> ghostImages;
    private final List<BufferedImage> logoImages;
    private final List<BufferedImage> generatedLogoImages;
    private final Set<String> warnedPaths;

    public ThemeManager()
    {
        this.warnedPaths = new HashSet<>();
        this.pacmanImage = this.loadImage("pacman_face.png");
        this.scaredGhostImage = this.loadImage("ghost_scared.png");
        this.ghostImages = this.loadImages(GHOST_FILENAMES);
        this.logoImages = this.loadImages(LOGO_FILENAMES);
        this.generatedLogoImages = this.createGeneratedLogoImages();
    }

    private BufferedImage loadImage(final String fileName)
    {
        File assetFile = new File(ASSET_FOLDER, fileName);
        if(!assetFile.exists())
        {
            this.warnMissingAsset(assetFile.getPath());
            return null;
        }

        try
        {
            return ImageIO.read(assetFile);
        }
        catch(IOException e)
        {
            this.warnMissingAsset(assetFile.getPath());
            return null;
        }
    }

    private List<BufferedImage> loadImages(final String[] fileNames)
    {
        List<BufferedImage> images = new ArrayList<>();
        for(String fileName : fileNames)
        {
            BufferedImage image = this.loadImage(fileName);
            if(image != null)
            {
                images.add(image);
            }
        }
        return images;
    }

    private void warnMissingAsset(final String path)
    {
        // The set prevents the same warning from printing every frame.
        if(this.warnedPaths.add(path))
        {
            System.out.println("[ThemeManager] Missing or unreadable asset: " + path + ". Falling back to default rendering.");
        }
    }

    public BufferedImage getPacmanImage()
    {
        return this.pacmanImage;
    }

    public BufferedImage getScaredGhostImage()
    {
        return this.scaredGhostImage;
    }

    public BufferedImage getGhostImageForId(final int ghostId)
    {
        if(this.ghostImages.isEmpty())
        {
            return null;
        }

        // `Math.floorMod` is safer than `%` for indexing because it never returns a negative number.
        return this.ghostImages.get(Math.floorMod(ghostId, this.ghostImages.size()));
    }

    public BufferedImage getLogoForCoordinate(final Coordinate coordinate)
    {
        // This coordinate-based hash keeps the logo stable for a given pellet tile
        // across repaints, while still scattering different logos around the map.
        int rawIndex = (coordinate.x() * 31) + (coordinate.y() * 17);
        List<BufferedImage> activeLogoImages = this.logoImages.isEmpty()
            ? this.generatedLogoImages
            : this.logoImages;

        if(activeLogoImages.isEmpty())
        {
            return null;
        }

        return activeLogoImages.get(Math.floorMod(rawIndex, activeLogoImages.size()));
    }

    private List<BufferedImage> createGeneratedLogoImages()
    {
        List<BufferedImage> images = new ArrayList<>();
        images.add(this.createBadgeImage(new Color(39, 110, 241), "AI"));
        images.add(this.createBadgeImage(new Color(115, 86, 249), "ML"));
        images.add(this.createBadgeImage(new Color(0, 170, 140), "JS"));
        images.add(this.createBadgeImage(new Color(223, 126, 47), "DB"));
        images.add(this.createBadgeImage(new Color(214, 65, 111), "OS"));
        return images;
    }

    private BufferedImage createBadgeImage(final Color accentColor, final String label)
    {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(12, 16, 28));
        graphics.fillRoundRect(0, 0, 32, 32, 10, 10);
        graphics.setColor(accentColor);
        graphics.fillRoundRect(3, 3, 26, 26, 8, 8);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 11));

        FontMetrics metrics = graphics.getFontMetrics();
        int textX = (32 - metrics.stringWidth(label)) / 2;
        int textY = ((32 - metrics.getHeight()) / 2) + metrics.getAscent();
        graphics.drawString(label, textX, textY);
        graphics.dispose();

        return image;
    }
}
