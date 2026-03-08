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

    private final Random random;
    private BoardRouter  boardRouter;
    private PelletRouter pelletRouter;

    public PacmanAgent(int myUnitId,
                       int pacmanId,
                       int ghostChaseRadius)
    {
        super(myUnitId, pacmanId, ghostChaseRadius);
        this.random = new Random();
        //System.out.println("AGENT LOADED SUCCESSFULLY");

        this.boardRouter = new ThriftyBoardRouter(myUnitId, pacmanId, ghostChaseRadius);
        this.pelletRouter = new ThriftyPelletRouter(myUnitId, pacmanId, ghostChaseRadius);
    }

    public final Random getRandom() { return this.random; }
    public final BoardRouter getBoardRouter() { return this.boardRouter; }
    public final PelletRouter getPelletRouter() { return this.pelletRouter; }

    private int getChebyshevDistance(final Coordinate c1,
                                     final Coordinate c2)
    {
        return Math.max(Math.abs(c1.x() - c2.x()), Math.abs(c1.y() - c2.y()));
    }

    private boolean shouldRefreshPlan(final GameView game,
                                      final Coordinate current)
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
        // TODO: implement me! This method is responsible for calculating
        // the "plan" of Coordinates you should visit in order to get from a starting
        // location and another ending location. I recommend you use
        // this.getBoardRouter().graphSearch(...) to get a path and convert it into
        // a Stack of Coordinates (see the documentation for SearchAgent)
        // which your makeMove can do something with!
        Coordinate current = game.getEntity(this.getMyEntityId()).getCurrentCoordinate();
        Coordinate target = this.getTargetCoordinate();

        if(target == null)
        {
            System.out.println("MAKEPLAN: Target is null");
            return;
        }

        

        Stack<Coordinate> store = new Stack<>();
        Path<Coordinate>  x = this.getBoardRouter().graphSearch(current, target, game);
        //Stack<Coordinate> store = new Stack<>();
        Coordinate a;
        if (x == null) {
            System.out.println("DEBUG: graphSearch returned NULL");
        }
        while(x != null && x.getParentPath() != null)
        {
            a= x.getDestination();
            store.push(a);
            x = x.getParentPath();
            //System.out.println("making the plan");
        }
        //System.out.println("yoooooooooo");
        

        // Coordinate current = game.getEntity(this.getMyEntityId()).getCurrentCoordinate();
        // Coordinate target = new Coordinate(15, 15);
        // if(target == null) System.out.println("DEBUG: TARGET is null");
        // Path<Coordinate> x = this.getBoardRouter().graphSearch(current, target, game);
        // Stack<Coordinate> store = new Stack<>();
        // Coordinate a;
        // if (x == null) {
        //     System.out.println("DEBUG: graphSearch returned NULL");
        // }
        // while(x != null && x.getParentPath() != null)
        // {
        //     a= x.getDestination();
        //     store.push(a);
        //     x = x.getParentPath();
        //     System.out.println("making the plan");
        // }
        // System.out.println("yoooooooooo");
        this.setPlanToGetToTarget(store);
    }

    @Override
    public Action makeMove(final GameView game)
    {
        Coordinate current = game.getEntity(this.getMyEntityId()).getCurrentCoordinate();
        if(this.getTargetCoordinate() != null && this.shouldRefreshPlan(game, current))
        {
            this.makePlan(game);
        }
        if(this.getPlanToGetToTarget() == null || this.getPlanToGetToTarget().isEmpty())
        {
            Path<PelletVertex> pelletpath = this.getPelletRouter().graphSearch(game);
            if(pelletpath!=null)
            {
            List<Coordinate> pelletOrder = new ArrayList<>();
            Path<PelletVertex> curr = pelletpath;
            while(curr != null)
            {
                pelletOrder.add(0, curr.getDestination().getPacmanCoordinate());
                curr = curr.getParentPath();
            }
            if(pelletOrder.size() >1)
            {
                this.setTargetCoordinate(pelletOrder.get(1));
                this.makePlan(game);
            }
            }
        }


        if(this.getPlanToGetToTarget() != null && !this.getPlanToGetToTarget().isEmpty())
        {
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
            //System.out.println("we are checking for directions");
            if(next.x()<current.x()) {//System.out.println("Direction is Left"); 
            return Action.LEFT;}
            if(next.x()>current.x()) {//System.out.println("Direction is Right"); 
            return Action.RIGHT;}
            if(next.y()<current.y()) {//System.out.println("Direction is Up"); 
            return Action.UP;}
            if(next.y()>current.y()) {//System.out.println("Direction is Down"); 
            return Action.DOWN;}
        }
        
        //System.out.println("Action is to stay hehehe 1");
        return Action.DOWN; //default
    }

    @Override
    public void afterGameEnds(final GameView game)
    {
        // if you want to log stuff after a game ends implement me!
        System.out.println("Game Ended");
    }
}
