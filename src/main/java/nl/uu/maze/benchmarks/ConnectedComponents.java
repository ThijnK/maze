package nl.uu.maze.benchmarks;

/**
 * Benchmark class that counts the number of connected components in an
 * undirected graph represented by an adjacency matrix.
 */
public class ConnectedComponents {
    public static int countComponents(int[][] adj) {
        if (adj.length != adj[0].length)
            throw new IllegalArgumentException("Adjacency matrix must be square");

        int n = adj.length, count = 0;
        boolean[] visited = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                // Call DFS from another class
                GraphTraversal.dfs(adj, visited, i);
                count++;
            }
        }
        return count;
    }
}
