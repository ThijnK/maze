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

    /**
     * Checks if the tree is simultaneously:
     * - A BST
     * - AVL balanced
     * - All root-to-leaf paths have alternating even/odd values
     * - No duplicate values
     * Returns true if all conditions are met.
     */
    public static boolean isComplexValidTree(Node root) {
        return isComplexValidTreeHelper(root, null, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, root);
    }

    // Helper to check if value exists in the path from ancestor to current node
    private static boolean existsInPath(Node ancestor, Node current, int value) {
        if (ancestor == null || ancestor == current)
            return false;
        if (ancestor.val == value)
            return true;
        // Check both subtrees
        return existsInPath(ancestor.left, current, value) || existsInPath(ancestor.right, current, value);
    }

    private static boolean isComplexValidTreeHelper(Node node, Integer parentVal, int min, int max, int depth,
            Node root) {
        if (node == null)
            return true;
        // BST property
        if (node.val <= min || node.val >= max)
            return false;
        // No duplicates in the path from root to this node
        if (existsInPath(root, node, node.val))
            return false;
        // Alternating even/odd with parent
        if (parentVal != null && (node.val % 2 == parentVal % 2))
            return false;
        // AVL balance
        int lh = height(node.left);
        int rh = height(node.right);
        if (IntUtils.abs(lh - rh) > 1)
            return false;
        // Recurse
        boolean left = isComplexValidTreeHelper(node.left, node.val, min, node.val, depth + 1, root);
        boolean right = isComplexValidTreeHelper(node.right, node.val, node.val, max, depth + 1, root);
        return left && right;
    }
}