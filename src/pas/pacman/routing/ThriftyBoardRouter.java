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
    // High-level idea:
    // this router is still basically BFS, but it changes which neighbors are preferred.
    // Safe tiles are expanded first, risky tiles only if needed, and immediate ghost-danger
    // tiles are treated like a last resort instead of normal moves.

    // If you want to encode other information you think is useful for Coordinate routing
    // besides Coordinates and data available in GameView you can do so here.
    public static class BoardExtraParams
        extends ExtraParams
    {
        // When `ignoreGhosts` is true, the BFS falls back to plain walkable-tile routing.
        // This is the escape hatch that prevents Pacman from getting completely stuck.
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
        // Chebyshev distance is `max(dx, dy)`, which matches the framework's
        // ghost chase radius definition better than Manhattan distance here.
        return Math.max(Math.abs(c1.x() - c2.x()), Math.abs(c1.y() - c2.y()));
    }

    private boolean isWalkableCoordinate(final Coordinate c,
                                         final GameView game)
    {
        // The chained `&&` means "all of these conditions must be true".
        // So a coordinate is walkable only if it is in bounds, not a wall, and not the ghost pen.
        return game.isInBounds(c)
            && game.getTile(c).getState() != Tile.State.WALL
            && game.getTile(c).getState() != Tile.State.GHOST_PEN;
    }

    private boolean isImmediateGhostDanger(final Coordinate c,
                                           final GameView game)
    {
        // This loop scans every entity id and filters down to live, non-scared ghosts.
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

            // Distance <= 1 means the tile is the ghost's tile or directly adjacent,
            // which is dangerous enough to treat as the very last fallback choice.
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
        // This method is softer than immediate danger:
        // tiles inside the chase radius are "risky", not always forbidden.
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
        // When a ghost is scared, we flip the logic:
        // nearby tiles become slightly attractive instead of scary.
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
        // `Path<Coordinate>` stores the current tile plus a parent pointer,
        // so BFS can rebuild the route after it reaches the target.
        Path<Coordinate> start = new Path<>(src);
        Queue<Path<Coordinate>> q = new ArrayDeque<>();
        Set<Coordinate> visited = new HashSet<>();
        Path<Coordinate> u;

        visited.add(src);
        q.add(start);
        while(!q.isEmpty())
        {
            // `remove()` pops the next FIFO item from the queue,
            // which is why this search is breadth-first rather than depth-first.
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

                // `new Path<>(nxt, 1.0f, u)` means:
                // destination `nxt`, step cost `1.0f`, and parent path `u`.
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
        // `instanceof` checks whether `params` really is a `BoardExtraParams` object
        // before casting it, which avoids a bad cast at runtime.
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

        // `arr` is just the four cardinal neighbors:
        // right, left, down, up.
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

            // We sort neighbors into three buckets:
            // safe first, risky second, forced-danger last.
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

        // Returning the safest non-empty bucket first lets the BFS prefer safety
        // without rewriting the whole search into a weighted shortest-path algorithm.
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

        // First try the ghost-aware version, because that is the behavior we actually want.
        BoardExtraParams safeParams = new BoardExtraParams();
        List<Coordinate> safeRoute = this.runBreadthFirstSearch(src, tgt, game, safeParams);
        if(safeRoute == null)
        {
            // If the safe search finds no route, do one backup search that ignores ghost risk.
            // This prevents Pacman from freezing when every available path is unpleasant.
            BoardExtraParams forcedParams = new BoardExtraParams();
            forcedParams.ignoreGhosts = true;
            safeRoute = this.runBreadthFirstSearch(src, tgt, game, forcedParams);
        }

        if(safeRoute == null || safeRoute.isEmpty())
        {
            return null;
        }

        // This converts the plain list of coordinates back into the framework's `Path` chain format.
        Path<Coordinate> ans = new Path<>(safeRoute.get(0));
        for(int i = 1; i < safeRoute.size(); i++)
        {
            ans = new Path<Coordinate>(safeRoute.get(i), 1.0f, ans);
        }

        return ans;
    }

}

