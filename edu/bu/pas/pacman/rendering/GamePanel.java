package edu.bu.pas.pacman.rendering;

import edu.bu.pas.pacman.game.Game;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

/*
 * Small Swing wrapper around `GameRenderer`.
 *
 * `GamePanel` stays intentionally thin: it owns the panel sizing and forwards
 * paint calls, while `GameRenderer` owns the actual drawing decisions.
 */
public class GamePanel extends JPanel
{
    private final GameRenderer gameRenderer;

    public GamePanel(final Game game)
    {
        this.gameRenderer = new GameRenderer(game);
        this.setPreferredSize(new Dimension(GameRenderer.TOTAL_WIDTH, GameRenderer.TOTAL_HEIGHT));
        this.setBackground(Color.BLACK);
        this.setLayout(null);
    }

    private GameRenderer getGameRenderer()
    {
        return this.gameRenderer;
    }

    @Override
    public void paintComponent(final Graphics graphics)
    {
        super.paintComponent(graphics);

        // Casting to `Graphics2D` unlocks image drawing, transforms, and better rendering hints.
        this.getGameRenderer().draw((Graphics2D)graphics);
    }
}
