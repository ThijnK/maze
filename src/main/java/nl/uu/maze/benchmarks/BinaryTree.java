package nl.uu.maze.benchmarks;

/**
 * Benchmark class that implements a binary tree with various traversal methods
 * and utility functions.
 */
public class BinaryTree {
    public static class Node {
        public int val;
        public Node left, right;

        public Node(int v) {
            val = v;
        }
    }

    // Returns an array with the inorder traversal
    public static int[] inorder(Node root) {
        int size = size(root);
        int[] out = new int[size];
        inorderHelper(root, out, new int[] { 0 });
        return out;
    }

    private static void inorderHelper(Node root, int[] out, int[] idx) {
        if (root == null)
            return;
        inorderHelper(root.left, out, idx);
        out[idx[0]++] = root.val;
        inorderHelper(root.right, out, idx);
    }

    // Returns an array with the preorder traversal
    public static int[] preorder(Node root) {
        int size = size(root);
        int[] out = new int[size];
        preorderHelper(root, out, new int[] { 0 });
        return out;
    }

    private static void preorderHelper(Node root, int[] out, int[] idx) {
        if (root == null)
            return;
        out[idx[0]++] = root.val;
        preorderHelper(root.left, out, idx);
        preorderHelper(root.right, out, idx);
    }

    // Returns an array with the postorder traversal
    public static int[] postorder(Node root) {
        int size = size(root);
        int[] out = new int[size];
        postorderHelper(root, out, new int[] { 0 });
        return out;
    }

    private static void postorderHelper(Node root, int[] out, int[] idx) {
        if (root == null)
            return;
        postorderHelper(root.left, out, idx);
        postorderHelper(root.right, out, idx);
        out[idx[0]++] = root.val;
    }

    // Returns the number of nodes in the tree
    public static int size(Node root) {
        if (root == null)
            return 0;
        return 1 + size(root.left) + size(root.right);
    }

    // Returns the height of the tree
    public static int height(Node root) {
        if (root == null)
            return 0;
        int lh = height(root.left);
        int rh = height(root.right);
        return 1 + (lh > rh ? lh : rh);
    }

    // Returns true if value exists in the tree
    public static boolean find(Node root, int value) {
        if (root == null)
            return false;
        if (root.val == value)
            return true;
        return find(root.left, value) || find(root.right, value);
    }
}