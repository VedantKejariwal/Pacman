package edu.bu.pas.pacman.rendering;

import edu.bu.pas.pacman.game.Board;
import edu.bu.pas.pacman.game.Constants;
import edu.bu.pas.pacman.game.Game;
import edu.bu.pas.pacman.game.Tile;
import edu.bu.pas.pacman.game.entity.Entity;
import edu.bu.pas.pacman.game.entity.Ghost;
import edu.bu.pas.pacman.game.entity.Pacman;
import edu.bu.pas.pacman.utils.Coordinate;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/*
 * Frontend theme renderer.
 *
 * This class keeps the drawing logic separate from gameplay logic:
 * - `Game` and `Board` still own the real game state.
 * - `ThemeManager` owns image loading and fallback behavior.
 * - `GameRenderer` only decides how that state should look on screen.
 *
 * That split keeps the code easier to replace later if you move the visuals to
 * a browser or another rendering layer.
 */
public class GameRenderer
{
    public static int TOTAL_HEIGHT;
    public static int TOTAL_WIDTH;

    private static final int CELL_NUM_PIXELS = Constants.Rendering.Cells.Dimensions.CELL_NUM_PIXELS;
    private static final int PACMAN_NUM_PIXELS = Constants.Rendering.Entities.Dimensions.PACMAN_NUM_PIXELS;
    private static final int GHOST_NUM_PIXELS = Constants.Rendering.Entities.Dimensions.GHOST_NUM_PIXELS;
    private static final int PELLET_FALLBACK_NUM_PIXELS = Constants.Rendering.Pellets.Dimensions.PELLET_NUM_PIXELS;
    private static final int PELLET_LOGO_NUM_PIXELS = 28;

    private static final Color BACKGROUND_EMPTY_COLOR = new Color(10, 14, 28);
    private static final Color BACKGROUND_WALL_COLOR = new Color(31, 43, 79);
    private static final Color BACKGROUND_GHOST_PEN_COLOR = new Color(54, 41, 90);
    private static final Color CELL_GRID_COLOR = new Color(95, 114, 176, 70);
    private static final Color FALLBACK_PELLET_COLOR = new Color(255, 215, 102);
    private static final Color PACMAN_FALLBACK_COLOR = Constants.Rendering.Entities.Colors.PACMAN_COLOR;
    private static final Color GHOST_SCARED_FALLBACK_COLOR = Constants.Rendering.Entities.Colors.GHOST_SCARED_COLOR;

    private final Game game;
    private final ThemeManager themeManager;
    private final Map<Coordinate, BufferedImage> pelletLogosByCoordinate;

    private Coordinate lastPacmanCoordinate;
    private double lastPacmanAngleDegrees;

    public GameRenderer(final Game game)
    {
        TOTAL_HEIGHT = game.getBoard().getNumRows() * CELL_NUM_PIXELS;
        TOTAL_WIDTH = game.getBoard().getNumCols() * CELL_NUM_PIXELS;
        this.game = game;
        this.themeManager = new ThemeManager();
        this.pelletLogosByCoordinate = new HashMap<>();
        this.lastPacmanCoordinate = null;
        this.lastPacmanAngleDegrees = 0.0;
        this.initializePelletLogoMap();
    }

    public Game getGame()
    {
        return this.game;
    }

    private void initializePelletLogoMap()
    {
        Board board = this.getGame().getBoard();

        for(int row = 0; row < board.getNumRows(); ++row)
        {
            for(int col = 0; col < board.getNumCols(); ++col)
            {
                Tile tile = board.getTile(row, col);
                if(tile.getState() == Tile.State.PELLET)
                {
                    Coordinate coordinate = tile.getCoordinate();
                    BufferedImage logo = this.themeManager.getLogoForCoordinate(coordinate);
                    if(logo != null)
                    {
                        this.pelletLogosByCoordinate.put(coordinate, logo);
                    }
                }
            }
        }
    }

    private int getCellPixelX(final int col)
    {
        return col * CELL_NUM_PIXELS;
    }

    private int getCellPixelY(final int row)
    {
        return row * CELL_NUM_PIXELS;
    }

    private int getCenteredPixel(final int cellPixel, final int objectPixels)
    {
        return cellPixel + ((CELL_NUM_PIXELS - objectPixels) / 2);
    }

    private double getPacmanAngleDegrees(final Coordinate pacmanCoordinate)
    {
        if(this.lastPacmanCoordinate == null)
        {
            this.lastPacmanCoordinate = pacmanCoordinate;
            return this.lastPacmanAngleDegrees;
        }

        int deltaX = pacmanCoordinate.x() - this.lastPacmanCoordinate.x();
        int deltaY = pacmanCoordinate.y() - this.lastPacmanCoordinate.y();

        if(deltaX > 0)
        {
            this.lastPacmanAngleDegrees = 0.0;
        }
        else if(deltaY > 0)
        {
            this.lastPacmanAngleDegrees = 90.0;
        }
        else if(deltaX < 0)
        {
            this.lastPacmanAngleDegrees = 180.0;
        }
        else if(deltaY < 0)
        {
            this.lastPacmanAngleDegrees = 270.0;
        }

        this.lastPacmanCoordinate = pacmanCoordinate;
        return this.lastPacmanAngleDegrees;
    }

    public void drawCellBackgroundColors(final Graphics2D graphics)
    {
        Board board = this.getGame().getBoard();

        for(int row = 0; row < board.getNumRows(); ++row)
        {
            for(int col = 0; col < board.getNumCols(); ++col)
            {
                Tile tile = board.getTile(row, col);
                Color backgroundColor = BACKGROUND_EMPTY_COLOR;

                if(tile.getState() == Tile.State.WALL)
                {
                    backgroundColor = BACKGROUND_WALL_COLOR;
                }
                else if(tile.getState() == Tile.State.GHOST_PEN)
                {
                    backgroundColor = BACKGROUND_GHOST_PEN_COLOR;
                }

                int pixelX = this.getCellPixelX(col);
                int pixelY = this.getCellPixelY(row);

                graphics.setColor(backgroundColor);
                graphics.fillRect(pixelX, pixelY, CELL_NUM_PIXELS, CELL_NUM_PIXELS);

                // A light grid keeps the board readable even when custom assets are busy.
                graphics.setColor(CELL_GRID_COLOR);
                graphics.drawRect(pixelX, pixelY, CELL_NUM_PIXELS, CELL_NUM_PIXELS);
            }
        }
    }

    public void drawPellets(final Graphics2D graphics)
    {
        Board board = this.getGame().getBoard();

        for(int row = 0; row < board.getNumRows(); ++row)
        {
            for(int col = 0; col < board.getNumCols(); ++col)
            {
                Tile tile = board.getTile(row, col);
                if(tile.getState() != Tile.State.PELLET)
                {
                    continue;
                }

                Coordinate coordinate = tile.getCoordinate();
                BufferedImage logo = this.pelletLogosByCoordinate.get(coordinate);
                int cellPixelX = this.getCellPixelX(col);
                int cellPixelY = this.getCellPixelY(row);

                if(logo != null)
                {
                    int drawX = this.getCenteredPixel(cellPixelX, PELLET_LOGO_NUM_PIXELS);
                    int drawY = this.getCenteredPixel(cellPixelY, PELLET_LOGO_NUM_PIXELS);
                    graphics.drawImage(logo, drawX, drawY, PELLET_LOGO_NUM_PIXELS, PELLET_LOGO_NUM_PIXELS, null);
                }
                else
                {
                    int drawX = this.getCenteredPixel(cellPixelX, PELLET_FALLBACK_NUM_PIXELS);
                    int drawY = this.getCenteredPixel(cellPixelY, PELLET_FALLBACK_NUM_PIXELS);
                    graphics.setColor(FALLBACK_PELLET_COLOR);
                    graphics.fillOval(drawX, drawY, PELLET_FALLBACK_NUM_PIXELS, PELLET_FALLBACK_NUM_PIXELS);
                }
            }
        }
    }

    public void drawPacman(final Graphics2D graphics)
    {
        Pacman pacman = this.getGame().getPacman();
        if(!pacman.getIsAlive())
        {
            return;
        }

        Coordinate coordinate = pacman.getCurrentCoordinate();
        int cellPixelX = this.getCellPixelX(coordinate.x());
        int cellPixelY = this.getCellPixelY(coordinate.y());
        BufferedImage pacmanImage = this.themeManager.getPacmanImage();
        double pacmanAngleDegrees = this.getPacmanAngleDegrees(coordinate);

        if(pacmanImage != null)
        {
            AffineTransform previousTransform = graphics.getTransform();
            double centerX = cellPixelX + (CELL_NUM_PIXELS / 2.0);
            double centerY = cellPixelY + (CELL_NUM_PIXELS / 2.0);

            graphics.translate(centerX, centerY);
            graphics.rotate(Math.toRadians(pacmanAngleDegrees));
            graphics.drawImage(
                pacmanImage,
                -(PACMAN_NUM_PIXELS / 2),
                -(PACMAN_NUM_PIXELS / 2),
                PACMAN_NUM_PIXELS,
                PACMAN_NUM_PIXELS,
                null
            );
            graphics.setTransform(previousTransform);
            return;
        }

        // The fallback keeps the game playable even before assets are added,
        // and still rotates so direction changes remain visible.
        AffineTransform previousTransform = graphics.getTransform();
        double centerX = cellPixelX + (CELL_NUM_PIXELS / 2.0);
        double centerY = cellPixelY + (CELL_NUM_PIXELS / 2.0);

        graphics.translate(centerX, centerY);
        graphics.rotate(Math.toRadians(pacmanAngleDegrees));
        graphics.setColor(PACMAN_FALLBACK_COLOR);
        graphics.fillArc(
            -(PACMAN_NUM_PIXELS / 2),
            -(PACMAN_NUM_PIXELS / 2),
            PACMAN_NUM_PIXELS,
            PACMAN_NUM_PIXELS,
            30,
            300
        );
        graphics.setTransform(previousTransform);
    }

    private void drawGhostFallback(
        final Graphics2D graphics,
        final Color ghostColor,
        final int drawX,
        final int drawY
    )
    {
        int bodyHeight = GHOST_NUM_PIXELS / 2;
        int waveWidth = GHOST_NUM_PIXELS / 3;
        int waveHeight = 16;
        int waveY = drawY + bodyHeight - 4;

        graphics.setColor(ghostColor);
        graphics.fillRect(drawX, drawY + (bodyHeight / 2), GHOST_NUM_PIXELS, bodyHeight);
        graphics.fillOval(drawX, drawY, GHOST_NUM_PIXELS, bodyHeight + 1);

        // Three small arcs recreate the classic ghost skirt as a simple fallback.
        for(int waveIndex = 0; waveIndex < 3; ++waveIndex)
        {
            graphics.fillOval(drawX + (waveIndex * waveWidth), waveY, waveWidth + 2, waveHeight);
        }
    }

    public void drawGhosts(final Graphics2D graphics)
    {
        for(Entity entity : this.getGame().getBoard().getEntities().values())
        {
            if(!(entity instanceof Ghost))
            {
                continue;
            }

            Ghost ghost = (Ghost)entity;
            if(!ghost.getIsAlive())
            {
                continue;
            }

            Coordinate coordinate = ghost.getCurrentCoordinate();
            int cellPixelX = this.getCellPixelX(coordinate.x());
            int cellPixelY = this.getCellPixelY(coordinate.y());
            int drawX = this.getCenteredPixel(cellPixelX, GHOST_NUM_PIXELS);
            int drawY = this.getCenteredPixel(cellPixelY, GHOST_NUM_PIXELS);

            BufferedImage ghostImage = ghost.getIsScared()
                ? this.themeManager.getScaredGhostImage()
                : this.themeManager.getGhostImageForId(ghost.getId());

            if(ghostImage != null)
            {
                graphics.drawImage(ghostImage, drawX, drawY, GHOST_NUM_PIXELS, GHOST_NUM_PIXELS, null);
            }
            else
            {
                Color ghostColor = ghost.getIsScared() ? GHOST_SCARED_FALLBACK_COLOR : ghost.getColor();
                this.drawGhostFallback(graphics, ghostColor, drawX, drawY);
            }
        }
    }

    public void drawEntities(final Graphics2D graphics)
    {
        this.drawPacman(graphics);
        this.drawGhosts(graphics);
    }

    public void draw(final Graphics2D graphics)
    {
        // Antialiasing gives the fallback shapes and scaled PNGs a cleaner look.
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setStroke(new BasicStroke(1.0f));

        this.drawCellBackgroundColors(graphics);
        this.drawPellets(graphics);
        this.drawEntities(graphics);
    }
}
