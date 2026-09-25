package nl.uu.tests.maze;

import org.junit.jupiter.api.Test;
import nl.uu.maze.util.DiGraph;
import nl.uu.maze.util.DiGraph.DiGraphEdge;
import nl.uu.maze.util.DiGraph.DiGraphNode;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

public class DiGraph_Test {
	
    @Test
    void dotExportSupportsBothConsoleAndFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws IOException {
        var graph = mk_G();
        String expected = graph.asDot().toString();
        var destination = directory.resolve("graph.dot");
        graph.saveAsDot(destination.toString());
        assertEquals(expected, java.nio.file.Files.readString(destination));

        var captured = new java.io.ByteArrayOutputStream();
        var original = System.out;
        try (var console = new java.io.PrintStream(captured, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(console);
            graph.saveAsDot(null);
        } finally {
            System.setOut(original);
        }
        assertEquals(expected + System.lineSeparator(), captured.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

	DiGraph<String,String,String,String> mk_G() {
		DiGraph<String,String,String,String> G = new DiGraph<>() ;
		DiGraphNode<String,String> st = G.addNode("start") ;
		DiGraphNode<String,String> n1 = G.addNode("N1") ;
		DiGraphNode<String,String> n2 = G.addNode("N2") ;
		DiGraphNode<String,String> nx =G.addNode("Nx") ;
		DiGraphNode<String,String> nz =G.addNode("Nz") ;
		DiGraphNode<String,String> end = G.addNode("end") ;
		G.start = st ;
		G.addEdge(st.id, n1.id, null) ;
		G.addEdge(st.id, nx.id, null) ;
		G.addEdge(n1.id, n2.id, null) ;
		G.addEdge(n2.id, end.id, null) ;
		G.addEdge(nx.id, end.id, "ch") ;
		G.addEdge(nx.id, end.id, "duck") ;
		G.addEdge(nz.id, end.id, null) ;
		return G ;
	}
	
	DiGraph<String,String,String,String> mk_G_withloop() {
		DiGraph<String,String,String,String> G = new DiGraph<>() ;
		G.name = "loop" ;
		DiGraphNode<String,String> st = G.addNode("start") ;
		DiGraphNode<String,String> loophead = G.addNode("while") ;
		DiGraphNode<String,String> n1 = G.addNode("if") ;
		DiGraphNode<String,String> n2 = G.addNode("N2") ;
		DiGraphNode<String,String> end = G.addNode("end") ;
		G.start = st ;
		G.addEdge(st.id, loophead.id, null) ;
		G.addEdge(loophead.id, end.id, "w-false") ;
		G.addEdge(loophead.id, n1.id, "w-true") ;
		G.addEdge(n1.id, end.id, "if-false-brk") ;
		G.addEdge(n1.id, n2.id, "if-true") ;
		G.addEdge(n2.id, loophead.id, "back-edge") ;
		return G ;
	}
	
	DiGraph<String,String,String,String> three_ifthenelses() {
		DiGraph<String,String,String,String> G = new DiGraph<>() ;
		G.name = "three_ifs" ;
		DiGraphNode<String,String> st = G.addNode("start") ;
		DiGraphNode<String,String> if1 = G.addNode("if1") ;
		DiGraphNode<String,String> if2 = G.addNode("if2") ;
		DiGraphNode<String,String> if3 = G.addNode("if3") ;

		DiGraphNode<String,String> t1 = G.addNode("true1") ;
		DiGraphNode<String,String> t2 = G.addNode("true2") ;
		DiGraphNode<String,String> t3 = G.addNode("true3") ;
		
		DiGraphNode<String,String> f1 = G.addNode("false1") ;
		DiGraphNode<String,String> f2 = G.addNode("false2") ;
		DiGraphNode<String,String> f3 = G.addNode("false3") ;

		DiGraphNode<String,String> end = G.addNode("end") ;
		G.start = st ;
		
		G.addEdge(st.id, if1.id, null) ;
		G.addEdge(if1.id, t1.id, null) ;
		G.addEdge(if1.id, f1.id, null) ;
		G.addEdge(t1.id, if2.id, null) ;
		G.addEdge(f1.id, if2.id, null) ;
		
		G.addEdge(if2.id, t2.id, null) ;
		G.addEdge(if2.id, f2.id, null) ;
		G.addEdge(t2.id, if3.id, null) ;
		G.addEdge(f2.id, if3.id, null) ;
		
		G.addEdge(if3.id, t3.id, null) ;
		G.addEdge(if3.id, f3.id, null) ;
		G.addEdge(t3.id, end.id, null) ;
		G.addEdge(f3.id, end.id, null) ;
		
		return G ;
	}
	
	static String showPath(List<DiGraphEdge<String,String,String,String>> sigma) {
		String z = "[" ;
		int k = 0 ;
		for(var E : sigma) {
			if (k>0) z+= ", " ;
			z += E.src.label.toString() ;
			k++ ;
		}
		if (sigma.size()>0) {
			z += ", " + sigma.getLast().dest.label.toString() ;
		}
		z += "]" ;
		return z ;
	}


	@Test
	public void test_dist() throws IOException {
		var G = mk_G() ;
		var st = G.getNodeWithLabel("start") ;
		var n1 = G.getNodeWithLabel("N1") ;
		var n2 = G.getNodeWithLabel("N2") ;
		var nx =G.getNodeWithLabel("Nx") ;
		var nz =G.getNodeWithLabel("Nz") ;
		var end = G.getNodeWithLabel("end") ;

		
		G.saveAsDot(null);
		
		// ask repeatedly to test caching too:
		for (int k=0; k<3; k++) {
			assertEquals(0, G.dist(st,st)) ;
			assertEquals(2, G.dist(st,end)) ;
			assertEquals(-1, G.dist(st,nz)) ;
			assertEquals(1, G.dist(nx,end)) ;
			assertEquals(2, G.dist(n1,end)) ;

			assertEquals(-1, G.dist(n1,st)) ;
			assertEquals(-1, G.dist(end,st)) ;

			// ask again to test the caching:
			assertEquals(0, G.dist(st,st)) ;
			assertEquals(2, G.dist(st,end)) ;
			assertEquals(-1, G.dist(st,nz)) ;
			assertEquals(1, G.dist(nx,end)) ;
			assertEquals(2, G.dist(n1,end)) ;
		}	
	}
	
	@Test
	public void test_dist_on_G_with_loop() throws IOException {
		var G = mk_G_withloop() ;
		var st = G.getNodeWithLabel("start") ;
		var loophead = G.getNodeWithLabel("while") ;
		var ifhead = G.getNodeWithLabel("if") ;
		var n2 = G.getNodeWithLabel("N2") ;
		var end = G.getNodeWithLabel("end") ;
		
		G.saveAsDot(null);
		
		assertEquals(3,G.dist(st,n2)) ;
		assertEquals(2,G.dist(st,end)) ;
		assertEquals(2,G.dist(n2,end)) ;
		assertEquals(0,G.dist(loophead,loophead)) ;
		assertEquals(-1,G.dist(end,loophead)) ;
		
	}
	
	@Test
	public void test_pathgen1() throws IOException {
		var G = mk_G() ;
		System.out.println(">> G") ;
		var paths = G.getMaxElementaryPaths(1) ;
		assertEquals(G.getEdges().size(), paths.size()) ;
		
		paths = G.getMaxElementaryPaths(2) ;
		assertEquals(5, paths.size()) ;
		
		paths = G.getMaxElementaryPaths(3) ;
		assertEquals(4, paths.size()) ;
		
		paths = G.getMaxElementaryPaths(4) ;
		assertEquals(4, paths.size()) ;
		
		// the prime paths:
		paths = G.getMaxElementaryPaths(-1) ;
		assertEquals(4, paths.size()) ;
		
		for (var sigma : paths) {
			System.out.println(">> p = " + showPath(sigma)) ;
		}
	}
	
	@Test
	public void test_pathgen_loop() throws IOException {
		var G = mk_G_withloop() ;
		System.out.println(">> loop") ;
		
		var paths = G.getMaxElementaryPaths(2) ;
		assertEquals(7, paths.size()) ;
		
		paths = G.getMaxElementaryPaths(-1) ;
		assertEquals(7, paths.size()) ;
		
		for (var sigma : paths) {
			System.out.println(">> p = " + showPath(sigma)) ;
		}
	}
	
	@Test
	public void test_pathgen_ifthenelses() throws IOException {
		var G = three_ifthenelses() ;
		System.out.println(">> if-then-else 3x") ;
		var paths = G.getMaxElementaryPaths(-1) ;
		assertEquals(8, paths.size()) ;
		for (var sigma : paths) {
			System.out.println(">> p = " + showPath(sigma)) ;
		}
	}
}
