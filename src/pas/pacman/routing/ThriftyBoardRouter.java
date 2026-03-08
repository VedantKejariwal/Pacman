package src.pas.pacman.routing;


// SYSTEM IMPORTS
import java.util.Collection;


// JAVA PROJECT IMPORTS
import edu.bu.pas.pacman.game.Game.GameView;
import edu.bu.pas.pacman.game.Tile;
import edu.bu.pas.pacman.game.entity.Entity;
import edu.bu.pas.pacman.game.entity.Ghost;
import edu.bu.pas.pacman.graph.Path;
import edu.bu.pas.pacman.routing.BoardRouter;
import edu.bu.pas.pacman.utils.Coordinate;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.Set;


// This class is responsible for calculating routes between two Coordinates on the Map.
// Use this in your PacmanAgent to calculate routes that (if followed) will lead
// Pacman from some Coordinate to some other Coordinate on the map.
public class ThriftyBoardRouter
    extends BoardRouter
{

    // If you want to encode other information you think is useful for Coordinate routing
    // besides Coordinates and data available in GameView you can do so here.
    public static class BoardExtraParams
        extends ExtraParams
    {
        public boolean ignoreGhosts;

    }

    // feel free to add other fields here!

    public ThriftyBoardRouter(int myUnitId,
                              int pacmanId,
                              int ghostChaseRadius)
    {
        super(myUnitId, pacmanId, ghostChaseRadius);

        // if you add fields don't forget to initialize them here!
    }

    private int getChebyshevDistance(final Coordinate c1,
                                     final Coordinate c2)
    {
        return Math.max(Math.abs(c1.x() - c2.x()), Math.abs(c1.y() - c2.y()));
    }

    private boolean isWalkableCoordinate(final Coordinate c,
                                         final GameView game)
    {
        return game.isInBounds(c)
            && game.getTile(c).getState() != Tile.State.WALL
            && game.getTile(c).getState() != Tile.State.GHOST_PEN;
    }

    private boolean isImmediateGhostDanger(final Coordinate c,
                                           final GameView game)
    {
        for(Integer id : game.getAllEntityIds())
        {
            Entity entity = game.getEntity(id);
            if(!(entity instanceof Ghost))
            {
                continue;
            }

            Ghost ghost = (Ghost)entity;
            if(!ghost.getIsAlive() || ghost.getIsScared())
            {
                continue;
            }

            if(this.getChebyshevDistance(c, ghost.getCurrentCoordinate()) <= 1)
            {
                return true;
            }
        }

        return false;
    }

    private boolean isGhostRisky(final Coordinate c,
                                 final GameView game)
    {
        for(Integer id : game.getAllEntityIds())
        {
            Entity entity = game.getEntity(id);
            if(!(entity instanceof Ghost))
            {
                continue;
            }

            Ghost ghost = (Ghost)entity;
            if(!ghost.getIsAlive() || ghost.getIsScared())
            {
                continue;
            }

            if(this.getChebyshevDistance(c, ghost.getCurrentCoordinate()) <= this.getGhostChaseRadius())
            {
                return true;
            }
        }

        return false;
    }

    private boolean isScaredGhostOpportunity(final Coordinate c,
                                             final GameView game)
    {
        for(Integer id : game.getAllEntityIds())
        {
            Entity entity = game.getEntity(id);
            if(!(entity instanceof Ghost))
            {
                continue;
            }

            Ghost ghost = (Ghost)entity;
            if(!ghost.getIsAlive() || !ghost.getIsScared())
            {
                continue;
            }

            if(this.getChebyshevDistance(c, ghost.getCurrentCoordinate()) <= 1)
            {
                return true;
            }
        }

        return false;
    }

    private List<Coordinate> runBreadthFirstSearch(final Coordinate src,
                                                   final Coordinate tgt,
                                                   final GameView game,
                                                   final BoardExtraParams params)
    {
        Path<Coordinate> start = new Path<>(src);
        Queue<Path<Coordinate>> q = new ArrayDeque<>();
        Set<Coordinate> visited = new HashSet<>();
        Path<Coordinate> u;

        visited.add(src);
        q.add(start);
        while(!q.isEmpty())
        {
            u = q.remove();
            if (u.getDestination().equals(tgt))
            {
                List<Coordinate> ans = new ArrayList<>();
                while(u != null)
                {
                    ans.add(0, u.getDestination());
                    u = u.getParentPath();
                }
                return ans;
            }

            List<Coordinate> abc = new ArrayList<>(this.getOutgoingNeighbors(u.getDestination(), game, params));
            for(int i =0; i<abc.size(); i ++)
            {
                Coordinate nxt = abc.get(i);

                if(visited.contains(nxt))
                {
                    continue;
                }

                visited.add(nxt);
                q.add(new Path<Coordinate>(nxt, 1.0f, u));
            }
        }

        return null;
    }


    @Override
    public Collection<Coordinate> getOutgoingNeighbors(final Coordinate src,
                                                       final GameView game,
                                                       final ExtraParams params)
    {
        BoardExtraParams ep = (params instanceof BoardExtraParams) ? (BoardExtraParams)params : null;
        List<Coordinate> xyz = new ArrayList<>();
        List<Coordinate> risky = new ArrayList<>();
        List<Coordinate> forced = new ArrayList<>();
        int newX; //new horizontal
        int newY; //new vertical
        newX = src.x();
        newY = src.y();
        Coordinate[] arr;
        arr = new Coordinate[] {
            new Coordinate(newX +1, newY),
            new Coordinate(newX - 1, newY), 
            new Coordinate(newX, newY +1), 
            new Coordinate(newX, newY-1)};
        for(int i=0; i<4; i++)
        {
            if(!this.isWalkableCoordinate(arr[i], game))
            {
                continue;
            }

            if(ep != null && ep.ignoreGhosts)
            {
                xyz.add(arr[i]);
                continue;
            }

            if(this.isImmediateGhostDanger(arr[i], game))
            {
                forced.add(arr[i]);
                continue;
            }

            if(this.isGhostRisky(arr[i], game))
            {
                if(this.isScaredGhostOpportunity(arr[i], game))
                {
                    risky.add(0, arr[i]);
                }
                else
                {
                    risky.add(arr[i]);
                }
                continue;
            }

            if(this.isScaredGhostOpportunity(arr[i], game))
            {
                xyz.add(0, arr[i]);
            }
            else
            {
                xyz.add(arr[i]);
            }
        }

        if(!xyz.isEmpty())
        {
            return xyz;
        }

        if(!risky.isEmpty())
        {
            return risky;
        }

        return forced;
    }

    @Override
    public Path<Coordinate> graphSearch(final Coordinate src,
                                        final Coordinate tgt,
                                        final GameView game)
    {
        if (tgt == null) System.out.println("graphSearch: TARGET IS NULL");

        BoardExtraParams safeParams = new BoardExtraParams();
        List<Coordinate> safeRoute = this.runBreadthFirstSearch(src, tgt, game, safeParams);
        if(safeRoute == null)
        {
            BoardExtraParams forcedParams = new BoardExtraParams();
            forcedParams.ignoreGhosts = true;
            safeRoute = this.runBreadthFirstSearch(src, tgt, game, forcedParams);
        }

        if(safeRoute == null || safeRoute.isEmpty())
        {
            return null;
        }

        Path<Coordinate> ans = new Path<>(safeRoute.get(0));
        for(int i = 1; i < safeRoute.size(); i++)
        {
            ans = new Path<Coordinate>(safeRoute.get(i), 1.0f, ans);
        }

        return ans;
    }

}

