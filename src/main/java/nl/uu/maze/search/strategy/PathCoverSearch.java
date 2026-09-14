package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.EngineConfiguration;
import nl.uu.maze.execution.symbolic.CoverageTracker;
import nl.uu.maze.execution.symbolic.SymbolicExecutor;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.TargetPath;
import nl.uu.maze.execution.symbolic.TargetPath.TargetPathStatus;
import nl.uu.maze.util.HCFG;
import nl.uu.maze.util.HCFG.HCFGPath;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.core.JavaSootMethod;

public class PathCoverSearch extends SearchStrategy<SymbolicState> {
	
	static final Logger logger = LoggerFactory.getLogger(PathCoverSearch.class);
    
	
	static int ordering1(SymbolicState S1, SymbolicState S2) {
		var tp1 = S1.getTargetPath() ;
		var tp2 = S2.getTargetPath() ;
		int c = Integer.compare(targetPathStatusToInt(tp1.status), targetPathStatusToInt(tp2.status)) ;
		if (c != 0) return c ;
		c = Float.compare(tp1.hdist, tp2.hdist) ;
		return c ;
	}
	
	static int ordering2(SymbolicState S1, SymbolicState S2) {
		var tp1 = S1.getTargetPath() ;
		var tp2 = S2.getTargetPath() ;
		int c = Integer.compare(targetPathStatusToInt(tp1.status), targetPathStatusToInt(tp2.status)) ;
		if (c != 0) return c ;
		c = Float.compare(tp1.hdist, tp2.hdist) ;
		if (c != 0) return c ;
		double cost1 = S1.getPathConstraints().stream()
				.mapToDouble(C -> C.getEstimatedCost())
			    .sum() ;
		double cost2 = S2.getPathConstraints().stream()
				.mapToDouble(C -> C.getEstimatedCost())
			    .sum() ;
		return Double.compare(cost1, cost2) ;
	}
	
	static int targetPathStatusToInt(TargetPathStatus status) {
		switch(status) {
		case TARGET_COVERED : return 0 ;
		case TARGET_PARTIALLY_COVERED : return 1 ;
		case APPROACHING_TARGET : return 2 ;
		case HAS_NO_TARGET : return 3 ;
		}
		return 4 ;
	}
	
	static class TargetProgress {
		TargetPathStatus bestStatus ;
		float bestHdist ;
		int lastImprovement ;
	}
	
	
	CoverageTracker coverageTracker = CoverageTracker.getInstance() ;
	
	PriorityQueue<SymbolicState> priority = new PriorityQueue<>((S1,S2) -> ordering2(S1,S2)) ;
	Queue<SymbolicState> theRest = new LinkedList<>() ;
	
	Map<HCFGPath,TargetProgress> progressTracking = new IdentityHashMap<>() ;

	Integer agingLimit = null ;
	
	/** iteration count. Updated whenever next() is invoked. */
	int iteration = 0 ;
	
	
	public PathCoverSearch() {
		int pl = EngineConfiguration.getInstance().pathLengthCoverage ;
    	if (! (pl == -1 || pl > 0)) {
    		logger.error("Strategy PathCoverSearch requires engine-configuration pathLengthCoverage to be -1 or >0");
            throw new IllegalArgumentException("PathCoverSearch requires --path-length-coverage=-1 or a positive length");
    	}
	}
	
	@Override
	public String getName() {
		return "PathCoverSearch" ;
	}
	
	@Override
    public boolean requiresPathTargetingAndTracking() {
        return true;
    }

	@Override
	public void add(SymbolicState target) {
		/*
		if (target.getCallDepth() > 5) {
			return ;
		}
		*/
		var hcfg = CoverageTracker.getInstance().getHCFG(target.getMethod()) ;
		Stmt stmt = target.getStmt() ;
		updateTargetPathStatus(target) ;
		//if (hcfg != null && hcfg.isEdgeOutStmt(stmt)) {
		
		/*
		if (hcfg != null && hcfg.start.label == stmt) {
			// limiting number of recursion...
			Integer n = stmtVisitCount.get(stmt) ;
			if (n == null) {
				n = 0 ;
			}
			if (n > 15) {
				return ;
			}
			stmtVisitCount.put(stmt, n+1) ;
		}
		*/
		
		switch (target.getTargetPath().status) {
		  case TARGET_COVERED : priority.offer(target) ; break ;
		  case TARGET_PARTIALLY_COVERED : priority.offer(target) ; break ;		
		  default : theRest.offer(target) ; break ;
		}
		target.setIteration(iteration);		
		count++ ;
	}
	

	@Override
	public void remove(SymbolicState target) {
		boolean changed = priority.remove(target) ;
		if (changed) return ;
		theRest.remove(target) ;		
	}

	
	
	@Override
	public SymbolicState next() {
		//System.out.println("PCS next() #targets:" + size()) ;
		
		if (coverageTracker.isDirty()) {
			// coverage tracker is dirty, recalculate targets:
			
			System.out.println("### PCS recalculating targets...") ;
			List<SymbolicState> priority__      = new LinkedList<>(priority) ;
			Collection<SymbolicState> therest__ = new LinkedList<>(theRest) ;
			int tel = 0 ;
			
			for (var S : priority__) {
				updateTargetPathStatus(S);
				if (S.getTargetPath().status != TargetPathStatus.TARGET_COVERED 
					|| S.getTargetPath().status != TargetPathStatus.TARGET_PARTIALLY_COVERED) {
					priority.remove(S) ;
					theRest.offer(S) ;
					tel++ ;
				}
			}
			
			for (var S : therest__) {
				updateTargetPathStatus(S);
				if (S.getTargetPath().status == TargetPathStatus.TARGET_COVERED
						|| S.getTargetPath().status == TargetPathStatus.TARGET_PARTIALLY_COVERED) {
					theRest.remove(S) ;
					priority.offer(S) ;
					tel++ ;
				}
			}
		
			logger.info("Re-targeting {}/{} states.", tel, size()) ;
			
			
			coverageTracker.cleanDirtyFlag() ;
		}
		
		
		//System.out.println(">>> PCS P=" + priority.size() + " R=" + theRest.size()) ;
		
		SymbolicState S = priority.poll() ;
		if (S == null) 
			S = theRest.poll() ;
		if (S != null) {
			/*
			System.out.println(">>> PCS next ") ;
			System.out.println("    stmt: " + S.getStmt()) ;
			System.out.println("    target: " + S.getTargetPath().targetpath + ", " + S.getTargetPath().status) ;
			System.out.println("    bhist : " + S.getBranchHistory()) ;
			System.out.println("    HDIST : " +  S.getTargetPath().hdist) ;	
			*/
		}
		
		// updating progress-tracking
		if (iteration % 20 == 0) {
			if (agingLimit == null) {
				agingLimit = EngineConfiguration.getInstance().targetPathAging ;
				if (agingLimit == 0) 
					agingLimit = 2 * CoverageTracker.getInstance().numberOfTargetStmts() ;
			}
			if (agingLimit >= 0) {
				var covered = coverageTracker.whichTargetPathsAreCovered(progressTracking.keySet().stream().toList()) ;
			    //System.out.println("PCS >>> #progresstracking:" + progressTracking.size()
			    //		+ ", #covered:" + covered.size()
			    //		) ;
				for (var sigma : covered) {
					progressTracking.remove(sigma) ;
				}
				//System.out.println("PCS >>> #progresstracking:" + progressTracking.size()) ;
				// checking non-progress:
				int agingLimit_ = agingLimit ;
				var nonprogress = progressTracking.entrySet().stream()
						.filter(P -> P.getValue().lastImprovement < iteration - agingLimit_)
						.map(P -> P.getKey())
						.toList() ;
				int numberRemoved = 0 ;
				for (var sigma : nonprogress) {
					var removed = coverageTracker.markTargetPathUnfeasible(sigma);
					if (removed) numberRemoved++ ;
				}
				if (numberRemoved > 0) {
					logger.info("Dropping {} target paths", numberRemoved) ;
				}
			}	
		}
		
		iteration++ ;
		return S ;
	}

	private void add_(SymbolicState S, Collection<SymbolicState> priority__, Collection<SymbolicState> theRest__) {
		switch (S.getTargetPath().status) {
		  case TARGET_COVERED : priority__.add(S) ; break ;
		  case TARGET_PARTIALLY_COVERED : priority__.add(S) ; break ;		
		  default : theRest__.add(S) ; 
		}
	}
	
	@Override
	public int size() {
		return priority.size() + theRest.size() ;
	}

	@Override
	public void reset() {
		priority.clear() ;
		theRest.clear() ;
	}

	@Override
	public Collection<SymbolicState> getAll() {
		List<SymbolicState> U = new LinkedList<>() ;
		U.addAll(priority) ;
		U.addAll(theRest) ;
		return U ;
	}
	
	
	 /**
     * Find and set a (new) target-path (within the same method as this.method) for S,
     * that is reachable from S.stmt. 
     * 
     * For now, no particular selection is made of which path to take, in case 
     * there are multiple choices. We simply take the first one that is reachable;
     * though note that the target paths are also sorted, more or less by how close
     * their heads are from the starting node.
     */
     TargetPath setNewTargetPath(SymbolicState S, HCFG hcfg) {
    	var stmt = S.getStmt() ;
    	var targets = coverageTracker.getStillUncoveredTargetPaths(hcfg.method) ;
    	
    	TargetPath T = null ;
    	
    	// check first if there is a sigma that is covered or partially covered:
    	for (var sigma : targets) {
    		var k = sigma.coverBy(S.getBranchHistory()) ;
    		if (k < 0) continue ;
    		T = new TargetPath(sigma) ;
    		if (k == 0) {
        		T.status = TargetPathStatus.TARGET_COVERED ;
        		T.hdist = hcfg.distToExit(stmt) ;
        	}
        	else {
        		T.status = TargetPathStatus.TARGET_PARTIALLY_COVERED ;
        		T.hdist = k ;
        	}
    		break ;
    	}
    	if (T == null) {
    		// else we check if there is a reachable target:
        	for (var sigma : targets) {		
        		var dist = hcfg.distToPathHead(stmt,sigma) ;
        		if (dist >= 0) {
        			T = new TargetPath(sigma) ;
        			T.status = TargetPathStatus.APPROACHING_TARGET ;
        			T.hdist = dist ;
        			break ;
        		}
        	}
    	}
    	if (T == null) {
    		// else there is no reachable targets
    		T = new TargetPath() ;
    		T.hdist = hcfg.distToExit(stmt) ;
    	}
    	
    	// update the progress tracking
    	if (T.targetpath != null) {
    		TargetProgress progress = progressTracking.get(T.targetpath) ;
        	if (progress == null) {
        		progress = new TargetProgress() ;
        		progress.bestStatus = T.status ;
        		progress.bestHdist = T.hdist ;
        		progress.lastImprovement = this.iteration ;
        		progressTracking.put(T.targetpath, progress) ;
        	}
        	else { 
        		int Tstatus = targetPathStatusToInt(T.status) ;
        	    int oldStatus = targetPathStatusToInt(progress.bestStatus) ;
        	    if (Tstatus < oldStatus || (Tstatus == oldStatus && T.hdist < progress.bestHdist)) {
        	    	progress.bestStatus = T.status ;
        	    	progress.bestHdist = T.hdist ;
        	    	progress.lastImprovement = this.iteration ;
        	    }
        	}
    	}
    	
    	
		S.setTargetPath(T) ;
		return T ;
    }
    
    

	
	void updateTargetPathStatus(SymbolicState S) {
    	
    	var hcfg = coverageTracker.getHCFG(S.getMethod()) ;
    	if (hcfg == null) {
    		var targetpath = new TargetPath() ;
    		// if hcfg is null, we can't get estimation on distance to
    		// exit.. so, just set it to maxint??
    		// there no easy solution for this ...
    		targetpath.hdist = Integer.MAX_VALUE ;
    		S.setTargetPath(targetpath) ;
			return ;
    	}
    	// hcfg is not null
    	
    	var targetpath = S.getTargetPath() ;
    	var stmt = S.getStmt() ;
    	
    	if (targetpath == null || hcfg.isExceptionHandlerHead(stmt)) {
			setNewTargetPath(S,hcfg) ;
			return ;
    	}
		
		// both hcfg and targetpath are not null:
		
		// first check whether the target is still open:
		if (CoverageTracker.getInstance().isDirty()) {
			//System.out.println(">>> SymbolicState updateTargetPath hcfg: " + hcfg.name) ;
			boolean targetStillOpen = coverageTracker.getStillUncoveredTargetPaths(S.getMethod()).contains(targetpath.targetpath) ;
			if (! targetStillOpen){
				// find a new target
				setNewTargetPath(S,hcfg) ;
				return ;
			}
		}
		
		
		// target is till open, so we update towards it:
		//System.out.println(">>> updateTargetPathStatus ") ;
		//System.out.println("    target: " + targetpath.targetpath + ", " + targetpath.status) ;
		//System.out.println("    bhist : " + this.branchHistory) ;
		//System.out.println("    HDIST : " + targetpath.hdist) ;
		
		var branchHistory = S.getBranchHistory() ;
		
    	switch (targetpath.status) {
    	
    	case TARGET_COVERED : 
    		targetpath.hdist = hcfg.distToExit(stmt) ;
    		return ;
    		
    	case TARGET_PARTIALLY_COVERED :
    		int k = targetpath.targetpath.coverBy(branchHistory) ;
    		if (k < 0) {
    			// the path deviates from target! 
    			setNewTargetPath(S,hcfg) ;
        		return ;
    		}
    		if (k == 0) {
    			targetpath.status = TargetPathStatus.TARGET_COVERED ;
    			targetpath.hdist = hcfg.distToExit(stmt) ;
    		}
    		else {
    			targetpath.hdist = k ;
    		}
    		return ;
    		
    	case APPROACHING_TARGET :
    		if (targetpath.hdist == 0) {
    			// execution is was at the target head. If the prev stmt was a branching-stmt,
    			// the current stmt will take it to the next node.
    			// Use branch-hhistory to check if the first edge is taken.
    			// IMPORTANT: check via branch-hist first before re-checking via
    			// distToPathHead
    			k = targetpath.targetpath.coverBy(branchHistory) ;
    			if (k>=0) {
    				targetpath.status = TargetPathStatus.TARGET_PARTIALLY_COVERED ;
    				targetpath.hdist = k ;
    				return ;
    			}
    		}
    		var dist = hcfg.distToPathHead(stmt, targetpath.targetpath) ;
    		targetpath.hdist = dist ;
    		if (dist >= 0) {
    			return ;
    		}
    		// dist negative, so target is no longer reachable:
    		setNewTargetPath(S,hcfg) ;
    		return ;
    		
    	}
    }

}
