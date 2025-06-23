package nl.uu.maze.benchmarks;

/**
 * Benchmark class that provides methods to find paths in a graph represented
 * by an adjacency matrix, with weighted edges.
 * <p>
 * This class implements both a depth-first search (DFS) for path existence and
 * Dijkstra's algorithm for finding the shortest path between two nodes.
 * It uses custom stack and priority queue implementations to avoid
 * dependencies on Java's standard library collections.
 */
public class MatrixGraphPaths {
    private final int[][] adj;
    private final int n;

    public MatrixGraphPaths(int[][] adj) {
        if (adj.length != adj[0].length)
            throw new IllegalArgumentException("Adjacency matrix must be square");
        // Validate weights
        for (int i = 0; i < adj.length; i++) {
            for (int j = 0; j < adj[i].length; j++) {
                if (adj[i][j] < 0)
                    throw new IllegalArgumentException("Weights must be non-negative");
                if (adj[i][j] > 1000)
                    throw new IllegalArgumentException("Weights must be less than or equal to 1000");
            }
        }

        this.adj = adj;
        this.n = adj.length;
    }

    // Method to check if there is a path from src to dest using DFS
    public boolean reachable(int src, int dest) {
        boolean[] seen = new boolean[n];
        IntStack stack = new IntStack(n);
        stack.push(src);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            if (u == dest)
                return true;
            seen[u] = true;
            for (int v = 0; v < n; v++) {
                if (adj[u][v] != 0 && !seen[v])
                    stack.push(v);
            }
        }
        return false;
    }

    // Simple method to fill an array with a specific value
    private static void fill(int[] arr, int val) {
        for (int i = 0; i < arr.length; i++)
            arr[i] = val;
    }

    // Dijkstra's algorithm to find the shortest path from src to dest
    public int shortestPath(int src, int dest) {
        int[] dist = new int[n];
        fill(dist, Integer.MAX_VALUE);
        dist[src] = 0;
        MinHeap pq = new MinHeap(n * n);
        pq.add(new int[] { src, 0 });
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            int u = cur[0], d = cur[1];
            if (u == dest)
                return d;
            if (d > dist[u])
                continue;
            for (int v = 0; v < n; v++) {
                int w = adj[u][v];
                if (w > 0 && d + w < dist[v]) {
                    dist[v] = d + w;
                    pq.add(new int[] { v, dist[v] });
                }
            }
        }
        return -1; // unreachable
    }

    // Simple stack implementation for DFS
    private static class IntStack {
        private int[] data;
        private int size = 0;

        public IntStack(int cap) {
            data = new int[cap];
        }

        public void push(int x) {
            data[size++] = x;
        }

        public int pop() {
            return data[--size];
        }

        public boolean isEmpty() {
            return size == 0;
        }
    }

    // Minimal priority queue for (node, distance) pairs
    private static class MinHeap {
        private int[] nodes;
        private int[] distances;
        private int size = 0;

        public MinHeap(int cap) {
            nodes = new int[cap];
            distances = new int[cap];
        }

        public void add(int[] pair) {
            int node = pair[0];
            int dist = pair[1];
            nodes[size] = node;
            distances[size] = dist;
            int i = size++;
            while (i > 0) {
                int p = (i - 1) / 2;
                if (distances[p] <= distances[i])
                    break;
                // swap nodes
                int tmpNode = nodes[p];
                nodes[p] = nodes[i];
                nodes[i] = tmpNode;
                // swap distances
                int tmpDist = distances[p];
                distances[p] = distances[i];
                distances[i] = tmpDist;
                i = p;
            }
        }

        public int[] poll() {
            int resNode = nodes[0];
            int resDist = distances[0];
            nodes[0] = nodes[--size];
            distances[0] = distances[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1, r = 2 * i + 2, min = i;
                if (l < size && distances[l] < distances[min])
                    min = l;
                if (r < size && distances[r] < distances[min])
                    min = r;
                if (min == i)
                    break;
                // swap nodes
                int tmpNode = nodes[i];
                nodes[i] = nodes[min];
                nodes[min] = tmpNode;
                // swap distances
                int tmpDist = distances[i];
                distances[i] = distances[min];
                distances[min] = tmpDist;
                i = min;
            }
            return new int[] { resNode, resDist };
        }

        public boolean isEmpty() {
            return size == 0;
        }
    }
}
