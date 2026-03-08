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

        if(ep.cache.containsKey(c1) && ep.cache.get(c1).containsKey(c2))
        {
            return ep.cache.get(c1).get(c2);
        }

        Path<Coordinate> path = ep.boardRouter.graphSearch(c1, c2, ep.game);
        float dist = (path == null) ? Float.POSITIVE_INFINITY : path.getTrueCost();

        ep.cache.putIfAbsent(c1, new HashMap<>());
        ep.cache.get(c1).put(c2,dist);
        ep.cache.putIfAbsent(c2, new HashMap<>());
        ep.cache.get(c2).put(c1, dist);

        return dist;
    }

    @Override
    public float getHeuristic(final PelletVertex src,
                              final GameView game,
                              final ExtraParams params)
    {
        if(!src.getRemainingPelletCoordinates().isEmpty())
        {
            PelletExtraParams ep = (params == null) ? null : (PelletExtraParams)params;
            float totalWeight =0.0f;
            float distance;
            PriorityQueue<PelletDistance> edgeDistance = new PriorityQueue<>();
            Set<Coordinate> visited = new HashSet<>();
            Set<Coordinate> mstKey = new HashSet<>(src.getRemainingPelletCoordinates());
            Coordinate start = src.getRemainingPelletCoordinates().iterator().next(); //any random pellet as the starting point

            if(ep != null && ep.mstCache.containsKey(mstKey))
            {
                totalWeight = ep.mstCache.get(mstKey);
            }
            else
            {
                visited.add(start);
                //prim's algorithm to find MST
                for(Coordinate p : src.getRemainingPelletCoordinates())
                {
                    if(visited.contains(p))
                    {
                        continue;
                    }
                    distance = getEdgeWeight(src.removePellet(start), src.removePellet(p), params);
                    edgeDistance.add(new PelletDistance(p,distance));
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
                            float d = getEdgeWeight(src.removePellet(currentEdge.coord), src.removePellet(p), params);
                            edgeDistance.add(new PelletDistance(p, d));
                        }
                    }
                }

                if(ep != null)
                {
                    ep.mstCache.put(mstKey, totalWeight);
                }
            }

            float minSourceDist = Float.POSITIVE_INFINITY;
            for(Coordinate p : src.getRemainingPelletCoordinates())
            {
                float d = getEdgeWeight(src, src.removePellet(p), params);
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

                Path<Coordinate> pairPath = br.graphSearch(c1, c2, game);
                float dist = (pairPath == null) ? Float.POSITIVE_INFINITY : pairPath.getTrueCost();

                sessionParams.cache.get(c1).put(c2, dist);
                sessionParams.cache.putIfAbsent(c2, new HashMap<>());
                sessionParams.cache.get(c2).put(c1, dist);
            }
        }

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
        //Collection<PelletVertex> rem = getOutgoingNeighbors(initial, game, null);
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

