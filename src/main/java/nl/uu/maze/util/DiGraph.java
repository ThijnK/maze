package nl.uu.maze.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import nl.uu.maze.util.DiGraph.DiGraphNode;
import nl.uu.maze.util.HCFG.HCFGNodeType;
import sootup.core.jimple.common.stmt.Stmt;


/**
 * Generic representation of a directed graph, with a single entry node.
 */
public class DiGraph<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty> {
	
	
	public static class DiGraphNode<NodeLabel,NodeProperty> {
		public int id ;
		public NodeLabel label ;
		public List<NodeProperty> properties = new LinkedList<>() ;
		
		public DiGraphNode(NodeLabel label) {
			this.label = label ;
		}
		
		public DiGraphNode(int id, NodeLabel label) {
			this.id = id ;
			this.label = label ;
		}
	}
	
	public static class DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty> {
		public DiGraphNode<NodeLabel,NodeProperty> src ;
		public DiGraphNode<NodeLabel,NodeProperty> dest ;
		public int edgeId ;
		public EdgeLabel label ;
		public List<EdgeProperty> properties = new LinkedList<>() ;
		
		public DiGraphEdge(DiGraphNode<NodeLabel,NodeProperty>  src, DiGraphNode<NodeLabel,NodeProperty>  dest) {
			this.src = src ;
			this.dest = dest ;
		}
		
		public DiGraphEdge(int id, DiGraphNode<NodeLabel,NodeProperty>  src, DiGraphNode<NodeLabel,NodeProperty>  dest) {
			this(src,dest) ;
			this.edgeId = id ;
		}
	}
	
	public String name = "G" ;
	
	public List<DiGraphNode<NodeLabel,NodeProperty>> nodes = new LinkedList<>() ;
	
	/**
	 * A member of {@link #nodes} that is the starting node of this
	 * directed graph.
	 */
	public DiGraphNode<NodeLabel,NodeProperty> start ;
	
	/**
	 * Map every node to edges that branch our from it. If there is none, the node
	 * is mapped to an empty list of edges.
	 */
	public Map<DiGraphNode<NodeLabel,NodeProperty>,
	           List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>>> 
	       successors 
	       = new HashMap<>() ;
	
	/**
	 * A caching distance table, between the nodes in the graph. It will be populated
	 * lazily, whenever a distance between two nodes are asked using 
	 * {@link DiGraph#dist(Object, Object)}.
	 * 
	 */
	Map<DiGraphNode<NodeLabel,NodeProperty>,
	       Map<DiGraphNode<NodeLabel,NodeProperty>,Integer>> 
		   
		   cachedDistanceTable = new HashMap<>() ;
	
	public DiGraph() { }
	
	/**
	 * Add a new node with the given label to the graph. Return the added
	 * node. The added node will be given the id of the number of nodes
	 * before the addition (so, this id will be unique).
	 */
	public DiGraphNode<NodeLabel,NodeProperty> addNode(NodeLabel label) {
		int id = nodes.size() ;
		DiGraphNode<NodeLabel,NodeProperty> nd = new DiGraphNode<>(id, label) ;
		List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>> sucs = new LinkedList<>() ;
		successors.put(nd, sucs) ;
		nodes.add(nd) ;
		return nd ;
	}
	
	public DiGraphNode<NodeLabel,NodeProperty> getNode(int id) {
		for (var nd : nodes) {
			if (nd.id == id) return nd ;
		}
		return null ;
	}
	
	/**
	 * Return all edges in the graph.
	 */
	public List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>> getEdges() {
		
		List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>> edges = new LinkedList<>() ;
		for (var nd : nodes) {
			edges.addAll(successors.get(nd)) ;
		}
		return edges ;
	}
	
	/**
	 * Return a node with the same label. Note that there can be multiple nodes
	 * with the sane label.
	 */
	public DiGraphNode<NodeLabel,NodeProperty> getNodeWithLabel(NodeLabel lab) {
		for (var nd : nodes) {
			if (lab == null && nd.label == null)
				return nd ;
			if (lab != null && lab.equals(nd.label))
				return nd ;
		}
		return null ;
	}
	
	/**
	 * Add a new edge from node src to dest, with the given label. 
	 * Return the newly added edge. The nodes src and dest are assumed
	 * to be already in the graph.
	 */
	public DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty> addEdge(int src, int dest, EdgeLabel label) {
		DiGraphNode<NodeLabel,NodeProperty> nd0 = getNode(src) ;
		DiGraphNode<NodeLabel,NodeProperty> nd1 = getNode(dest) ;
		DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty> E = new DiGraphEdge<>(nd0,nd1) ;
		E.label = label ;
		List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>> succs = successors.get(nd0) ;
		if (succs == null) {
			succs = new LinkedList<>() ;
			successors.put(nd0, succs) ;
		}
		succs.add(E) ;
		return E ;
	}
	
	
	public List<DiGraphNode<NodeLabel,NodeProperty>> predecessors(DiGraphNode<NodeLabel,NodeProperty> nd) {
		List<DiGraphNode<NodeLabel,NodeProperty>> preds = new LinkedList<>() ;
		for (var nd0 : nodes) {
			var succs =  successors.get(nd0) ;
			boolean isPred = succs.stream().anyMatch(E -> E.dest == nd) ;
			if (isPred) preds.add(nd0) ;
		}
		return preds ;
	}
	
	/**
	 * Renumber the nodes-ids so that the numbering is topological.
	 */
	void renumber() {
		int count = 0 ;
		List<DiGraphNode<NodeLabel,NodeProperty>> worklist = new LinkedList<>() ;
		List<DiGraphNode<NodeLabel,NodeProperty>> visited = new LinkedList<>() ;
		List<DiGraphNode<NodeLabel,NodeProperty>> roots = new LinkedList<>() ;
		
		roots.add(start) ;
		for (var nd : nodes) {
			if (nd != start && predecessors(nd).isEmpty()) {
				roots.add(nd) ;
			}
		}
		
		for (var R : roots) {
			if (visited.contains(R)) {
				// should not happen
				continue ;
			}
			// using BFS to renumber:
			worklist.add(R) ;
			while (! worklist.isEmpty()) {
				var nd = worklist.removeFirst() ;
				nd.id = count ;
				visited.add(nd) ;
				count++ ;
				var outEdges = this.successors.get(nd) ;
				for (var E : outEdges) {
					if (! visited.contains(E.dest)) 
						worklist.add(E.dest) ;
				}
			}
		}
	}
	
	/**
	 * Using BFS to calculate the distance from src to dest in the graph.
	 * If dest is not reachable, -1 is returned.
	 * 
	 * Calculated distance is cached in {@link #cachedDistanceTable}, so future request does not trigger 
	 * recomputation.
	 */
	public int dist(DiGraphNode<NodeLabel,NodeProperty> src, DiGraphNode<NodeLabel,NodeProperty> dest) {
		Map<DiGraphNode<NodeLabel,NodeProperty>,Integer> distances_from_src = cachedDistanceTable.get(src) ;
		if (distances_from_src == null) {
			distances_from_src = new HashMap<>() ;
			cachedDistanceTable.put(src, distances_from_src) ;
		}
		Integer D = distances_from_src.get(dest) ;
		if (D != null) {
			// the distance was calculated before, and cached in the table,
			// we can just return it:
			return D ;
		}
		
		D = -1 ; // unreachable as default
		Set<DiGraphNode<NodeLabel,NodeProperty>> visited = new HashSet<>() ;
		Queue<Pair<DiGraphNode<NodeLabel,NodeProperty>,Integer>> worklist = new LinkedList<>() ;
		worklist.add(new Pair<>(src,0)) ;
		while (! worklist.isEmpty()) {
			Pair<DiGraphNode<NodeLabel,NodeProperty>,Integer> v = worklist.poll() ;
			if (v.first().equals(dest)) {
				// dest found, break the search
				D = v.second() ;
				break ;
			}
			boolean changed = visited.add(v.first()) ;
			if (! changed) continue ;
			
			var sucs = successors.get(v.first()) ;
			int dist_now = v.second() ;
			
			for (var E : sucs) {
				worklist.add(new Pair<>(E.dest,dist_now+1)) ;
			}
			
		}
		distances_from_src.put(dest, D) ;
		return D ;
 	}
	
	/**
	 * Check if sequence tau is a strict suffix of sigma. Both tau and sigma
	 * are assumed to be non-cyclic elementary paths.
	 * 
	 * <p>NOTE: deliberately using == to check equality.
	 */
	private static <T> boolean isStrictSuffixOf(List<T> tau, List<T> sigma) {
		
		if (tau.size() >= sigma.size()) return false;
		
		// so ... tau is shorter than sigma
		
		var x0 = tau.getFirst() ;
		int k = 0 ;
		for (var y : sigma) {
			if (y==x0) {
				// found start of tau in sigma
				break ;
			}
			k++ ;
		}
		if (sigma.size() - k < tau.size()) {
			// the remaining part of sigma is too short
			return false ;
		}
		for (var y : tau) {
			var x = sigma.get(k) ;
			if (x != y)
				return false ;
			k++ ;
		}
		return true ;
	}
	
	/**
	 * Return all maximal elementary paths of this graph, of length of up k.  Paths are
	 * represented as sequences of edges. The length of a path is defined as the number 
	 * of edges passed by the path. 
	 * So, for k=1 this function will return, essentially, all edges in the graph. 
	 * For k=2, it will return all the edge-pairs in the HCFG.
	 * 
	 * <p>Special case: for k=-1, the method will return all prime paths of the HCFG.
	 * 
	 * <p>An elementary path is a path where no node appears multiple times, except
	 * the first and the last nodes of the path; they can be the same (hence representing
	 * a cycle). A prime path is a maximal elementary path (it is not a subpath of
	 * another elementary path).
	 */
	public List<List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>>> getMaxElementaryPaths(int k) {
		
		if (k < -1)
			throw new IllegalArgumentException() ;
		
		List<List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>>> T = new LinkedList<>() ;
		
		if (k == 0)
			return T ;
		
		// initialize T to contain singleton paths [e] where e
		// is an edge of this graph:
		for (var nd : nodes) {
			for (var E : successors.get(nd)) {
				List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>> sigma = new LinkedList<>() ;
				sigma.add(E) ;
				T.add(sigma) ;
			}
		}

		List<List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>>> cycles = new LinkedList<>() ;
		List<List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>>> alreadyRightMaximal = new LinkedList<>() ;

		
		if (k>0) k-- ;
		
		while (! T.isEmpty() &&  k != 0) {
			
			if (k>0) k-- ;
			
			// extends every sigma in T to the right, if it remains an elementary path:
			List<List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>>> T__ = new LinkedList<>() ;		
			for (var sigma : T) {	
				var last = sigma.getLast() ;
				if (successors.get(last.dest).isEmpty()) {
					// sigma is right-maximal:
					alreadyRightMaximal.add(sigma) ;
					continue ;
				}
				// case that sigma can be extended on the right:
				for (var E2 : successors.get(last.dest)) {
					// make a new (shallow) copy of sigma:
					List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>> sigma__ = new LinkedList<>() ;
					sigma__.addAll(sigma) ;
					boolean stillElementaryPath = ! sigma__.stream().anyMatch(E1 -> E1.dest == E2.dest) ;
					if (stillElementaryPath) {
						sigma__.add(E2) ;
						if (E2.dest == sigma__.getFirst().src) {
							cycles.add(sigma__) ;
						}
						else {
							T__.add(sigma__) ;
						}
					}
				}
			}
			T = T__ ;
		}
		
		//System.out.println(">>> #T=" + T.size()) ;
		
		List<List<DiGraphEdge<NodeLabel,EdgeLabel,NodeProperty,EdgeProperty>>> Tfinal = new LinkedList<>() ;
		Tfinal.addAll(cycles) ;
		
		T.addAll(alreadyRightMaximal) ;
		// only add paths from T that are also left-maximal:
		for (var tau : T) {
			boolean maximal = ! T.stream().anyMatch(sigma -> tau != sigma && isStrictSuffixOf(tau,sigma)) ;
			if (maximal)
				Tfinal.add(tau) ;
		}
		
		// Tfinal now contain all maximal elementary paths of length <= k	

		return Tfinal ;
	}
	
	/**
	 * Show the graph as a dot-file (Graphiz) for visualization. Well.. it will return 
	 * a DotWriter dw. Then dw.toString() will give you the string content,
	 * which you can save to a file as its content,
	 */
	public DotWriter asDot() {
		DotWriter dot = new DotWriter(this.name) ;
		for (var nd : nodes) {
			String label = "" + nd.label ;
			String id = "" + nd.id ;
			label = "[" + id + "] " + label ; 
			if (nd == start) {
				dot.addStartNode(id,label);
			}
			else if (successors.get(nd).isEmpty()) {
				dot.addExitNode(id,label);
			}
			else if(predecessors(nd).isEmpty()) {
				dot.addNode2(id,label);
			}
			else {
				dot.addNode(id,label);
			}
 		}
		for (var nd : nodes) {
			for (var E : successors.get(nd)) {
				String label = null ;
				if (E.label != null)
					label = E.label.toString() ;
				dot.addEdge("" + E.src.id, "" + E.dest.id,label);
			}
		}
		dot.close();
		return dot ;
	}
	
	/**
	 * Save the graph as a dot-file for visualization. If fname is null, the dot
	 * graph will be printed to the console.
	 */
	public void saveAsDot(String fname) throws IOException {
		var dot = asDot();
		if (fname == null) {
			System.out.println(dot);
		} else {
			dot.saveToFile(fname);
		}
	}
	
	
	@Override
	public String toString() {
		String z = "DiGraph of " + this.name ;
		for (var nd : nodes) {
			z += "\n  nd " + nd.id + ": " + nd.label ;
			z += ", props: " + nd.properties ;
			var outs = successors.get(nd) ;
			z += ", #out-edges=" + outs.size() ;
			if (outs.size() > 0) {
				for (var e : outs) {
					z += "\n    --> " + e.dest.id + ":" ;
					if (outs.size() > 1) 
						z += " #" + e.edgeId ;
					z += " " + e.dest.label ;
				}
			}
		}
		return z ;
	}

}
