package nl.uu.maze.benchmarks;

/**
 * Benchmark class that provides methods for graph traversal using Depth-First
 * Search (DFS)
 * and Breadth-First Search (BFS) on an undirected graph represented by an
 * adjacency matrix.
 */
public class GraphTraversal {
    public static void dfs(int[][] adj, boolean[] visited, int v) {
        visited[v] = true;
        for (int i = 0; i < adj.length; i++)
            if (adj[v][i] != 0 && !visited[i])
                dfs(adj, visited, i);
    }

    public static void bfs(int[][] adj, boolean[] visited, int start) {
        int n = adj.length;
        int[] queue = new int[n];
        int front = 0, rear = 0;
        queue[rear++] = start;
        visited[start] = true;
        while (front < rear) {
            int v = queue[front++];
            for (int i = 0; i < n; i++) {
                if (adj[v][i] != 0 && !visited[i]) {
                    queue[rear++] = i;
                    visited[i] = true;
                }
            }
        }
    }
}
