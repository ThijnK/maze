package nl.uu.maze.benchmarks;

/**
 * Benchmark class that counts the number of connected components in an
 * undirected graph represented by an adjacency matrix.
 * Also provides a method to check if any component contains a simple cycle of a
 * given length.
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

    /**
     * Returns true if there exists a connected component in the graph that contains
     * a simple cycle of exactly the given length.
     */
    public static boolean hasComponentWithCycleOfLength(int[][] adj, int cycleLen) {
        int n = adj.length;
        boolean[] visited = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                // Collect all nodes in this component
                boolean[] compVisited = new boolean[n];
                GraphTraversal.dfs(adj, compVisited, i);
                // Try to find a cycle of the given length in this component
                for (int v = 0; v < n; v++) {
                    if (compVisited[v]) {
                        boolean[] pathVisited = new boolean[n];
                        if (findCycleOfLength(adj, v, v, cycleLen, 0, pathVisited)) {
                            return true;
                        }
                    }
                }
                // Mark component as visited
                for (int v = 0; v < n; v++) {
                    if (compVisited[v])
                        visited[v] = true;
                }
            }
        }
        return false;
    }

    // Helper: DFS to find a simple cycle of exact length
    private static boolean findCycleOfLength(int[][] adj, int start, int curr, int targetLen, int currLen,
            boolean[] pathVisited) {
        pathVisited[curr] = true;
        if (currLen == targetLen) {
            pathVisited[curr] = false;
            return curr == start && currLen > 0;
        }
        for (int next = 0; next < adj.length; next++) {
            if (adj[curr][next] != 0) {
                if (next == start && currLen + 1 == targetLen) {
                    pathVisited[curr] = false;
                    return true;
                }
                if (!pathVisited[next]) {
                    if (findCycleOfLength(adj, start, next, targetLen, currLen + 1, pathVisited)) {
                        pathVisited[curr] = false;
                        return true;
                    }
                }
            }
        }
        pathVisited[curr] = false;
        return false;
    }
}
