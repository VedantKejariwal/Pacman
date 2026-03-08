package src.pas.pacman.agents;


// SYSTEM IMPORTS
import edu.bu.pas.pacman.agents.SearchAgent;
import edu.bu.pas.pacman.game.Action;
import edu.bu.pas.pacman.game.Game.GameView;
import edu.bu.pas.pacman.game.entity.Entity;
import edu.bu.pas.pacman.game.entity.Ghost;
import edu.bu.pas.pacman.graph.Path;
import edu.bu.pas.pacman.graph.PelletGraph.PelletVertex;
import edu.bu.pas.pacman.routing.BoardRouter;
import edu.bu.pas.pacman.routing.PelletRouter;
import edu.bu.pas.pacman.utils.Coordinate;
import java.util.Stack;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;


// JAVA PROJECT IMPORTS
import src.pas.pacman.routing.ThriftyBoardRouter;  // responsible for how to get somewhere
import src.pas.pacman.routing.ThriftyPelletRouter; // responsible for pellet order


public class PacmanAgent
    extends SearchAgent
{
    // This class is the coordinator:
    // the pellet router decides what to eat next, and the board router decides how to walk there.

    private final Random random;
    private BoardRouter  boardRouter;
    private PelletRouter pelletRouter;

    public PacmanAgent(int myUnitId,
                       int pacmanId,
                       int ghostChaseRadius)
    {
        // `super(...)` calls the parent class constructor first,
        // which is required before this subclass can finish initializing itself.
        super(myUnitId, pacmanId, ghostChaseRadius);
        this.random = new Random();
        //System.out.println("AGENT LOADED SUCCESSFULLY");

        // These two objects split the planning problem in half:
        // pellet order first, then board movement to the chosen target pellet.
        this.boardRouter = new ThriftyBoardRouter(myUnitId, pacmanId, ghostChaseRadius);
        this.pelletRouter = new ThriftyPelletRouter(myUnitId, pacmanId, ghostChaseRadius);
    }

    // These one-line getters are simple access points so the rest of the class
    // can use the routers and random generator without touching fields directly.
    public final Random getRandom() { return this.random; }
    public final BoardRouter getBoardRouter() { return this.boardRouter; }
    public final PelletRouter getPelletRouter() { return this.pelletRouter; }

    private int getChebyshevDistance(final Coordinate c1,
                                     final Coordinate c2)
    {
        // Same distance rule as the ghost chase radius:
        // `Math.max(dx, dy)` counts how close two tiles are in Chebyshev space.
        return Math.max(Math.abs(c1.x() - c2.x()), Math.abs(c1.y() - c2.y()));
    }

    private boolean shouldRefreshPlan(final GameView game,
                                      final Coordinate current)
    {
        // If a live ghost gets close, an old plan may have become unsafe,
        // so this method asks whether we should recompute the route immediately.
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

            if(this.getChebyshevDistance(current, ghost.getCurrentCoordinate()) <= this.getGhostChaseRadius() + 1)
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void makePlan(final GameView game)
    {
        // This method turns a board-level route into a stack of coordinates.
        // The stack is useful because `makeMove` can repeatedly `pop()` the next step.
        Coordinate current = game.getEntity(this.getMyEntityId()).getCurrentCoordinate();
        Coordinate target = this.getTargetCoordinate();

        if(target == null)
        {
            System.out.println("MAKEPLAN: Target is null");
            return;
        }

        

        Stack<Coordinate> store = new Stack<>();
        Path<Coordinate>  x = this.getBoardRouter().graphSearch(current, target, game);
        Coordinate a;
        if (x == null) {
            System.out.println("DEBUG: graphSearch returned NULL");
        }
        while(x != null && x.getParentPath() != null)
        {
            // `push()` adds to the top of the stack.
            // We walk backward through the linked path and push each step,
            // so later pops will replay the route in forward order.
            a= x.getDestination();
            store.push(a);

            // This line is the critical path-reconstruction step:
            // move from the current path node to its parent until we reach the start.
            x = x.getParentPath();
        }

        this.setPlanToGetToTarget(store);
    }

    @Override
    public Action makeMove(final GameView game)
    {
        Coordinate current = game.getEntity(this.getMyEntityId()).getCurrentCoordinate();

        // Replan when danger changes near Pacman, because a safe path from a moment ago
        // might already be outdated if a ghost stepped into the area.
        if(this.getTargetCoordinate() != null && this.shouldRefreshPlan(game, current))
        {
            this.makePlan(game);
        }

        // If there is no current board-walk plan, ask the pellet router for a pellet order.
        // That order is a higher-level plan: which pellet state should come next.
        if(this.getPlanToGetToTarget() == null || this.getPlanToGetToTarget().isEmpty())
        {
            Path<PelletVertex> pelletpath = this.getPelletRouter().graphSearch(game);
            if(pelletpath!=null)
            {
                List<Coordinate> pelletOrder = new ArrayList<>();
                Path<PelletVertex> curr = pelletpath;
                while(curr != null)
                {
                    // `add(0, value)` inserts at the front of the list,
                    // which reverses the backward parent chain into forward order.
                    pelletOrder.add(0, curr.getDestination().getPacmanCoordinate());
                    curr = curr.getParentPath();
                }

                // Index 0 is Pacman's current location, so index 1 is the next real pellet target.
                if(pelletOrder.size() >1)
                {
                    this.setTargetCoordinate(pelletOrder.get(1));
                    this.makePlan(game);
                }
            }
        }


        if(this.getPlanToGetToTarget() != null && !this.getPlanToGetToTarget().isEmpty())
        {
            // `pop()` removes and returns the top stack item,
            // which is the next coordinate Pacman should move toward.
            Coordinate next = this.getPlanToGetToTarget().pop(); // got the next coordinate the bot should go to
            while (next.equals(current) && !this.getPlanToGetToTarget().isEmpty()) {
                //System.out.println("same as current position");
                next = this.getPlanToGetToTarget().pop();
            }
            if (next.equals(current)) 
                {
                    //System.out.println("Action is still in current position");
                    return Action.UP; //default
                }

            // These comparisons convert a coordinate difference into a movement action.
            // Only one of these branches should be true because neighbors differ in one direction.
            if(next.x()<current.x()) {//System.out.println("Direction is Left"); 
            return Action.LEFT;}
            if(next.x()>current.x()) {//System.out.println("Direction is Right"); 
            return Action.RIGHT;}
            if(next.y()<current.y()) {//System.out.println("Direction is Up"); 
            return Action.UP;}
            if(next.y()>current.y()) {//System.out.println("Direction is Down"); 
            return Action.DOWN;}
        }
        
        // Final fallback if planning somehow produced nothing usable.
        return Action.DOWN; //default
    }

    @Override
    public void afterGameEnds(final GameView game)
    {
        // if you want to log stuff after a game ends implement me!
        System.out.println("Game Ended");
    }
}
