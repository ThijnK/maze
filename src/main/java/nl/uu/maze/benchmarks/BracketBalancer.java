package nl.uu.maze.benchmarks;

/**
 * Benchmark class that checks for balanced brackets in string expressions.
 */
public class BracketBalancer {
    public static boolean isBalanced(char[] expr) {
        char[] stack = new char[expr.length];
        int top = -1;
        for (char c : expr) {
            if (c == '(' || c == '[' || c == '{')
                stack[++top] = c;
            else if (c == ')' || c == ']' || c == '}') {
                if (top < 0)
                    return false;
                char open = stack[top--];
                if ((c == ')' && open != '(') ||
                        (c == ']' && open != '[') ||
                        (c == '}' && open != '{'))
                    return false;
            }
        }
        return top == -1;
    }
}