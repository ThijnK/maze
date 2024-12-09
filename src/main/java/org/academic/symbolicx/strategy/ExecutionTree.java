package org.academic.symbolicx.strategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a generic execution tree where each node can hold value of any
 * type.
 *
 * @param <T> The type of value held in each node.
 */
public class ExecutionTree<T> {

    /**
     * Represents a node in the execution tree.
     *
     * @param <T> The type of value held in each node.
     */
    public static class TreeNode<T> {
        private T value;
        private TreeNode<T> parent;
        private List<TreeNode<T>> children;

        public TreeNode(T value, TreeNode<T> parent) {
            this.value = value;
            this.parent = parent;
            this.children = new ArrayList<>();
        }

        public T getValue() {
            return value;
        }

        public TreeNode<T> getParent() {
            return parent;
        }

        public List<TreeNode<T>> getChildren() {
            return children;
        }

        public int getChildCount() {
            return children.size();
        }

        public TreeNode<T> getChild(int index) {
            return children.get(index);
        }

        public void addChild(TreeNode<T> child) {
            this.children.add(child);
        }

        public void addChildren(List<TreeNode<T>> children) {
            this.children.addAll(children);
        }

        public void addChildrenFromValues(List<T> values) {
            for (T value : values) {
                this.children.add(new TreeNode<>(value, this));
            }
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public void removeChild(TreeNode<T> child) {
            this.children.remove(child);
        }
    }

    private TreeNode<T> root;

    public ExecutionTree(T rootValue) {
        this.root = new TreeNode<>(rootValue, null);
    }

    public TreeNode<T> getRoot() {
        return root;
    }

    public TreeNode<T> findNode(T value) {
        return findNode(root, value);
    }

    public TreeNode<T> findNode(TreeNode<T> current, T value) {
        if (current == null) {
            return null;
        }

        if (current.getValue().equals(value)) {
            return current;
        }

        for (TreeNode<T> child : current.getChildren()) {
            TreeNode<T> result = findNode(child, value);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    public void removePath(TreeNode<T> startNode) {
        if (startNode == null) {
            return;
        }

        TreeNode<T> parent = startNode.getParent();
        if (parent != null) {
            parent.removeChild(startNode);
            if (parent.isLeaf()) {
                removePath(parent); // Recursively remove the parent if it becomes a leaf
            }
        } else if (startNode == root) {
            root = null; // If the startNode is the root, set the root to null
        }

        // Recursively remove all children of the startNode
        for (TreeNode<T> child : new ArrayList<>(startNode.getChildren())) {
            removePath(child);
        }
    }
}
