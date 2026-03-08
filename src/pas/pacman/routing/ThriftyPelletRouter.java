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
    // `final` means once these fields are assigned in the constructor,
    // this helper object becomes an immutable "coordinate + score" pair.
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
        // `Float.compare(a, b)` returns a negative value when `a < b`,
        // which is exactly what Java's `PriorityQueue` needs for a min-heap.
        return Float.compare(this.distance, other.distance);
    }
}

public class ThriftyPelletRouter
    extends PelletRouter
{
    // High-level idea:
    // 1) Precompute board distances once.
    // 2) Greedily remove pellets while the board is still large.
    // 3) Run weighted A* only on the small remaining tail.
    // This keeps the algorithm fast on 9x9+ boards where full TSP-style A* explodes.

    // When the board still has a lot of pellets left, solving the full ordering problem
    // exactly behaves like a TSP search and explodes in the number of states.
    // This threshold keeps the large early-game in a fast greedy mode and only hands
    // a much smaller late-game to weighted A*.
    private static final int HYBRID_ASTAR_THRESHOLD = 6;

    // A weight greater than 1.0 tells A* to trust the heuristic more aggressively,
    // which trims queue growth in the late-game search where exact optimality is less
    // important than returning a strong move quickly.
    private static final float WEIGHTED_ASTAR_HEURISTIC_WEIGHT = 2.0f;


    // If you want to encode other information you think is useful for planning the order
    // of pellets ot eat besides Coordinates and data available in GameView
    // you can do so here.
    public static class PelletExtraParams
        extends ExtraParams
    {
        // Keeping these references here lets helper methods read the current
        // game board and board router without threading many arguments everywhere.
        public final GameView game;
        public final BoardRouter boardRouter;

        // `HashMap<A, HashMap<B, C>>` is used like a 2D table:
        // `cache.get(c1).get(c2)` stores the shortest board distance from c1 to c2.
        public final HashMap<Coordinate, HashMap<Coordinate, Float>> cache = new HashMap<>();

        // The MST only depends on which pellets remain, so this memoizes
        // "remaining pellet set -> MST weight" and avoids recomputing Prim's algorithm.
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
        // `Path<Coordinate>` means "a linked path whose nodes are Coordinates".
        Path<Coordinate> path = params.boardRouter.graphSearch(c1, c2, params.game);

        // The ternary operator `condition ? a : b` is a compact if/else expression.
        // Here it says: if no path exists, use infinity, otherwise use the path cost.
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
        // This overload is just a convenience wrapper:
        // it extracts Pacman's current coordinate from the pellet-search state.
        return this.getCachedDistance(src.getPacmanCoordinate(), dst, params);
    }

    private float calculateMstWeight(final PelletVertex src,
                                     final PelletExtraParams params)
    {
        float totalWeight = 0.0f;
        PriorityQueue<PelletDistance> edgeDistance = new PriorityQueue<>();
        Set<Coordinate> visited = new HashSet<>();
        Coordinate start = src.getRemainingPelletCoordinates().iterator().next(); //any random pellet as the starting point

        // `visited` is the standard Prim's algorithm set:
        // once a pellet is added to the tree, we do not add it again.
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
            // `poll()` removes and returns the smallest item in the min-heap.
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

    private Coordinate getNearestPelletCoordinate(final PelletVertex src,
                                                  final PelletExtraParams params)
    {
        Coordinate bestPellet = null;
        float bestDistance = Float.POSITIVE_INFINITY;

        // Greedy nearest-neighbor is intentionally cheap here:
        // one scan over the remaining pellets uses cached board distances
        // and picks a strong next target without branching the whole state space.
        for(Coordinate pellet : src.getRemainingPelletCoordinates())
        {
            float dist = this.getCachedDistance(src, pellet, params);

            // This is the usual "best so far" pattern:
            // whenever we find a smaller score, we replace the current answer.
            if(dist < bestDistance)
            {
                bestDistance = dist;
                bestPellet = pellet;
            }
        }

        return bestPellet;
    }

    private Path<PelletVertex> buildGreedyPrefix(final PelletVertex initial,
                                                 final PelletExtraParams params)
    {
        // `new Path<>(initial)` uses Java's diamond operator `<>`,
        // which lets the compiler infer the generic type `PelletVertex`.
        Path<PelletVertex> greedyPath = new Path<>(initial);
        PelletVertex current = initial;

        // This loop is the key scaling change:
        // instead of asking A* to reason about all pellet permutations on big boards,
        // we deterministically peel off nearby pellets until the tail is small enough
        // for the search to finish quickly.
        while(current.getRemainingPelletCoordinates().size() > HYBRID_ASTAR_THRESHOLD)
        {
            Coordinate nextPellet = this.getNearestPelletCoordinate(current, params);
            if(nextPellet == null)
            {
                break;
            }

            // `removePellet(nextPellet)` does not mutate the current state.
            // It returns a brand new successor state with that pellet removed.
            PelletVertex nextState = current.removePellet(nextPellet);
            float pathCost = this.getCachedDistance(current, nextPellet, params);

            // This builds a linked chain of Path nodes:
            // new destination, edge cost to reach it, heuristic placeholder, parent path.
            greedyPath = new Path<>(nextState, pathCost, 0.0f, greedyPath);
            current = nextState;
        }

        return greedyPath;
    }

    @Override
    public Collection<PelletVertex> getOutgoingNeighbors(final PelletVertex src,
                                                         final GameView game,
                                                         final ExtraParams params)
    {
        // Each remaining pellet creates exactly one successor state:
        // move Pacman conceptually to that pellet and remove it from the remaining set.
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
        if(params == null)
        {
            // This null-params branch gives a safe lower bound when no session cache exists yet.
            float distance = 0.0f;
            distance = DistanceMetric.manhattanDistance(src.getPacmanCoordinate(), dst.getPacmanCoordinate());
            return distance;
        }
        PelletExtraParams ep = (PelletExtraParams)params;
        Coordinate c1 = src.getPacmanCoordinate();
        Coordinate c2 = dst.getPacmanCoordinate();

        // In the real search we prefer the true cached board distance,
        // because walls matter and Manhattan distance underestimates too much.
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
                // `computeIfAbsent(key, lambda)` means:
                // if the key is missing, run the lambda to compute it once and store it.
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

            // Heuristic structure:
            // nearest pellet from Pacman + MST over remaining pellets.
            // This stays informative while still being fast because both parts use cached distances.
            return (minSourceDist + totalWeight);
        }
        return 0.0f; //already at the goal state
    }

    @Override
    public Path<PelletVertex> graphSearch(final GameView game) 
    {
        // `initial` represents the full search state at the start of the turn:
        // Pacman's position plus the complete set of remaining pellets.
        PelletVertex initial = new PelletVertex(game);
        
        BoardRouter br = new ThriftyBoardRouter(this.getMyUnidId(), this.getPacmanId(), this.getGhostChaseRadius());
        PelletExtraParams sessionParams = new PelletExtraParams(game, br);

        sessionParams.cache.clear();
        sessionParams.mstCache.clear();
        // This one-time nested loop pays the board-search cost up front,
        // so every later heuristic edge lookup becomes a HashMap read instead of a BFS.
        List<Coordinate> importantCoords = new ArrayList<>(initial.getRemainingPelletCoordinates());
        importantCoords.add(initial.getPacmanCoordinate());

        // The outer loop chooses the first endpoint, and the inner loop chooses the second endpoint.
        // Starting the inner loop at `i + 1` avoids duplicate pairs like (A,B) and (B,A).
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

        // Step 1 of the hybrid algorithm:
        // greedily shrink the huge early-game board before asking weighted A* to help.
        Path<PelletVertex> start = this.buildGreedyPrefix(initial, sessionParams);
        if(start.getDestination().getRemainingPelletCoordinates().isEmpty())
        {
            return start;
        }

        // Step 2 of the hybrid algorithm:
        // run weighted A* only on the much smaller late-game tail.
        PriorityQueue<Path<PelletVertex>> q = new PriorityQueue<>(
            (p1,p2) -> {
                // Weighted A* changes `g + h` into `g + W*h`.
                // Larger `W` makes the search more goal-directed and usually much faster.
                float f1 = p1.getTrueCost() + (WEIGHTED_ASTAR_HEURISTIC_WEIGHT * p1.getEstimatedPathCostToGoal());
                float f2 = p2.getTrueCost() + (WEIGHTED_ASTAR_HEURISTIC_WEIGHT * p2.getEstimatedPathCostToGoal());
                return Float.compare(f1,f2);
            }
        );
        Set<PelletVertex> visited = new HashSet<>(); //store the visited states
        float pathCost;
        float heuristic;

        // The queue starts after the greedy prefix, so weighted A* only solves the small tail.
        start.setEstimatedPathCostToGoal(this.getHeuristic(start.getDestination(), game, sessionParams));
        q.add(start);

        while(!q.isEmpty())
        {
            // `poll()` pops the currently best-scoring candidate path from the priority queue.
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
                    // Each successor gets:
                    // edge cost `g`, heuristic `h`, and a parent pointer back to `pathChain`.
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

