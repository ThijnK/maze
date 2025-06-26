package nl.uu.maze.benchmarks;

/**
 * Benchmark class that provides methods for graph traversal using Depth-First
 * Search (DFS) and Breadth-First Search (BFS) on an undirected graph
 * represented by an
 * adjacency matrix.
 * Returns the order of traversal as an array of vertex indices.
 */
public class GraphTraversal {
    public static int[] dfs(int[][] adj, boolean[] visited, int v) {
        int[] order = new int[adj.length];
        int[] idx = new int[1]; // mutable index holder
        dfsHelper(adj, visited, v, order, idx);
        int[] result = new int[idx[0]];
        for (int i = 0; i < idx[0]; i++)
            result[i] = order[i];
        return result;
    }

    private static void dfsHelper(int[][] adj, boolean[] visited, int v, int[] order, int[] idx) {
        visited[v] = true;
        order[idx[0]++] = v;
        for (int i = 0; i < adj.length; i++)
            if (adj[v][i] != 0 && !visited[i])
                dfsHelper(adj, visited, i, order, idx);
    }

    public static int[] bfs(int[][] adj, boolean[] visited, int start) {
        int n = adj.length;
        int[] queue = new int[n];
        int[] order = new int[n];
        int front = 0, rear = 0, ordIdx = 0;
        queue[rear++] = start;
        visited[start] = true;
        while (front < rear) {
            int v = queue[front++];
            order[ordIdx++] = v;
            for (int i = 0; i < n; i++) {
                if (adj[v][i] != 0 && !visited[i]) {
                    queue[rear++] = i;
                    visited[i] = true;
                }
            }
        }
        int[] result = new int[ordIdx];
        for (int i = 0; i < ordIdx; i++)
            result[i] = order[i];
        return result;
    }
}
