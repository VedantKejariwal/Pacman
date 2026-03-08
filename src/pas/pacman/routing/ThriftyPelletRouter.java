package src.pas.pacman.routing;


import java.util.ArrayList;
// SYSTEM IMPORTS
import java.util.Collection;


// JAVA PROJECT IMPORTS
import edu.bu.pas.pacman.game.Game.GameView;
import edu.bu.pas.pacman.graph.Path;
import edu.bu.pas.pacman.graph.PelletGraph.PelletVertex;
import edu.bu.pas.pacman.routing.PelletRouter;
import edu.bu.pas.pacman.utils.Coordinate;
import edu.bu.pas.pacman.utils.DistanceMetric;
import java.util.Set;
import java.util.List;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.HashSet;
import edu.bu.pas.pacman.routing.BoardRouter;

class PelletDistance implements Comparable<PelletDistance>
{
    public final Coordinate coord;
    public final float distance;
    public PelletDistance(Coordinate coord, float distance)
    {
        this.coord = coord;
        this.distance = distance;
    }
    @Override
    public int compareTo(PelletDistance other)
    {
        return Float.compare(this.distance, other.distance);
    }
}

public class ThriftyPelletRouter
    extends PelletRouter
{

    // If you want to encode other information you think is useful for planning the order
    // of pellets ot eat besides Coordinates and data available in GameView
    // you can do so here.
    public static class PelletExtraParams
        extends ExtraParams
    {
        public final GameView game;
        public final BoardRouter boardRouter;

        public final HashMap<Coordinate, HashMap<Coordinate, Float>> cache = new HashMap<>();
        public final HashMap<Set<Coordinate>, Float> mstCache = new HashMap<>();

        public PelletExtraParams (GameView game, BoardRouter boardRouter)
        {
            this.game = game;
            this.boardRouter = boardRouter;
        }
    }

    // feel free to add other fields here!

    public ThriftyPelletRouter(int myUnitId,
                               int pacmanId,
                               int ghostChaseRadius)
    {
        super(myUnitId, pacmanId, ghostChaseRadius);

        // if you add fields don't forget to initialize them here!
    }

    private float getCachedDistance(final Coordinate c1,
                                    final Coordinate c2,
                                    final PelletExtraParams params)
    {
        // This first branch reads the already-known board distance in O(1) time,
        // which is the whole reason the heuristic stops re-running BFS inside Prim's algorithm.
        if(params.cache.containsKey(c1) && params.cache.get(c1).containsKey(c2))
        {
            return params.cache.get(c1).get(c2);
        }

        // This fallback keeps the method correct even if a pair was not precomputed for some reason.
        Path<Coordinate> path = params.boardRouter.graphSearch(c1, c2, params.game);
        float dist = (path == null) ? Float.POSITIVE_INFINITY : path.getTrueCost();

        // These two puts mirror the undirected grid distance in both directions,
        // so later lookups from either endpoint are constant-time.
        params.cache.putIfAbsent(c1, new HashMap<>());
        params.cache.get(c1).put(c2, dist);
        params.cache.putIfAbsent(c2, new HashMap<>());
        params.cache.get(c2).put(c1, dist);

        return dist;
    }

    private float getCachedDistance(final PelletVertex src,
                                    final Coordinate dst,
                                    final PelletExtraParams params)
    {
        return this.getCachedDistance(src.getPacmanCoordinate(), dst, params);
    }

    private float calculateMstWeight(final PelletVertex src,
                                     final PelletExtraParams params)
    {
        float totalWeight = 0.0f;
        PriorityQueue<PelletDistance> edgeDistance = new PriorityQueue<>();
        Set<Coordinate> visited = new HashSet<>();
        Coordinate start = src.getRemainingPelletCoordinates().iterator().next(); //any random pellet as the starting point

        visited.add(start);
        // Prim's algorithm stays in place, but every edge weight now comes from the precomputed cache.
        for(Coordinate p : src.getRemainingPelletCoordinates())
        {
            if(visited.contains(p))
            {
                continue;
            }
            edgeDistance.add(new PelletDistance(p, this.getCachedDistance(start, p, params)));
        }

        while(!edgeDistance.isEmpty())
        {
            PelletDistance currentEdge = edgeDistance.poll();
            if(visited.contains(currentEdge.coord))
            {
                continue;
            }
            totalWeight = totalWeight + currentEdge.distance;
            visited.add(currentEdge.coord);
            for(Coordinate p : src.getRemainingPelletCoordinates())
            {
                if(!visited.contains(p))
                {
                    edgeDistance.add(new PelletDistance(p, this.getCachedDistance(currentEdge.coord, p, params)));
                }
            }
        }

        return totalWeight;
    }

    @Override
    public Collection<PelletVertex> getOutgoingNeighbors(final PelletVertex src,
                                                         final GameView game,
                                                         final ExtraParams params)
    {
        // TODO: implement me!
        List<PelletVertex> rem = new ArrayList<>();
        for(Coordinate p : src.getRemainingPelletCoordinates())
        {
            
            rem.add(src.removePellet(p));
        }
        return rem;
    }

    @Override
    public float getEdgeWeight(final PelletVertex src,
                               final PelletVertex dst,
                               final ExtraParams params)
    {
        // TODO: implement me!
        if(params == null)
        {
        float distance = 0.0f;
        distance = DistanceMetric.manhattanDistance(src.getPacmanCoordinate(), dst.getPacmanCoordinate());
        return distance;
        }
        PelletExtraParams ep = (PelletExtraParams)params;
        Coordinate c1 = src.getPacmanCoordinate();
        Coordinate c2 = dst.getPacmanCoordinate();
        return this.getCachedDistance(c1, c2, ep);
    }

    @Override
    public float getHeuristic(final PelletVertex src,
                              final GameView game,
                              final ExtraParams params)
    {
        if(!src.getRemainingPelletCoordinates().isEmpty())
        {
            PelletExtraParams ep = (params == null) ? null : (PelletExtraParams)params;
            float totalWeight = 0.0f;
            Set<Coordinate> mstKey = new HashSet<>(src.getRemainingPelletCoordinates());

            // The MST depends only on which pellets remain, not on the order the search reached them,
            // so memoizing by the pellet set avoids recomputing the same Prim expansion many times.
            if(ep != null)
            {
                totalWeight = ep.mstCache.computeIfAbsent(mstKey, key -> this.calculateMstWeight(src, ep));
            }

            float minSourceDist = Float.POSITIVE_INFINITY;
            for(Coordinate p : src.getRemainingPelletCoordinates())
            {
                // This stays admissible because it is the true shortest board distance
                // from Pacman to one remaining pellet, never an inflated estimate.
                float d = (ep == null)
                    ? DistanceMetric.manhattanDistance(src.getPacmanCoordinate(), p)
                    : this.getCachedDistance(src, p, ep);
                if(d<minSourceDist)
                {
                    minSourceDist = d;
                }
            }
            return (minSourceDist + totalWeight);
        }
        return 0.0f; //already at the goal state
    }

    @Override
    public Path<PelletVertex> graphSearch(final GameView game) 
    {
        PelletVertex initial = new PelletVertex(game);
        
        BoardRouter br = new ThriftyBoardRouter(this.getMyUnidId(), this.getPacmanId(), this.getGhostChaseRadius());
        PelletExtraParams sessionParams = new PelletExtraParams(game, br);

        sessionParams.cache.clear();
        sessionParams.mstCache.clear();

        PriorityQueue<Path<PelletVertex>> q = new PriorityQueue<>(
            (p1,p2) -> {
                float f1 = p1.getTrueCost() + p1.getEstimatedPathCostToGoal();
                float f2 = p2.getTrueCost() + p2.getEstimatedPathCostToGoal();
                return Float.compare(f1,f2);
            }
        );
        Set<PelletVertex> visited = new HashSet<>(); //store the visited states
        Path<PelletVertex> start = new Path<>(initial);
        float pathCost;
        float heuristic;
        q.add(start);
        // This one-time nested loop pays the board-search cost up front,
        // so every later heuristic edge lookup becomes a HashMap read instead of a BFS.
        List<Coordinate> importantCoords = new ArrayList<>(initial.getRemainingPelletCoordinates());
        importantCoords.add(initial.getPacmanCoordinate());
        for(int i = 0; i < importantCoords.size(); i++)
        {
            Coordinate c1 = importantCoords.get(i);
            sessionParams.cache.putIfAbsent(c1, new HashMap<>());
            for(int j = i + 1; j < importantCoords.size(); j++)
            {
                Coordinate c2 = importantCoords.get(j);
                if(sessionParams.cache.get(c1).containsKey(c2))
                {
                    continue;
                }

                float dist = this.getCachedDistance(c1, c2, sessionParams);
                sessionParams.cache.get(c1).put(c2, dist);
                sessionParams.cache.putIfAbsent(c2, new HashMap<>());
                sessionParams.cache.get(c2).put(c1, dist);
            }
        }

        while(!q.isEmpty())
        {
            Path<PelletVertex> pathChain = q.poll();
            PelletVertex u = pathChain.getDestination();

            if(u.getRemainingPelletCoordinates().isEmpty())
            {
                return pathChain;
            }
            if(!visited.contains(u))
            {
                visited.add(u);
                for(PelletVertex p : getOutgoingNeighbors(u, game, sessionParams))
                {
                    pathCost = getEdgeWeight(u, p, sessionParams);
                    heuristic = getHeuristic(p, game, sessionParams);
                    Path<PelletVertex> nextPath = new Path<>(p, pathCost, heuristic, pathChain);
                    q.add(nextPath);
                }
            }

        }
        return null;
    }

}

