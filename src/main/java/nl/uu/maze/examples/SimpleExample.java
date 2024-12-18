package nl.uu.maze.examples;

/**
 * A simple class with some simple example methods.
 */
public class SimpleExample {
    /**
     * A simple method that checks the sign of a number.
     * 
     * @param number The number to check
     * @return "Positive" if the number is positive, "Negative" otherwise
     * @see <a
     *      href=http://magjac.com/graphviz-visual-editor/?dot=digraph+G+%7B%0A%09compound%3Dtrue%0A%09labelloc%3Db%0A%09style%3Dfilled%0A%09color%3Dgray90%0A%09node+%5Bshape%3Dbox%2Cstyle%3Dfilled%2Ccolor%3Dwhite%5D%0A%09edge+%5Bfontsize%3D10%2Carrowsize%3D1.5%2Cfontcolor%3Dgrey40%5D%0A%09fontsize%3D10%0A%0A%2F%2F++lines+%5B13%3A+13%5D+%0A%09subgraph+cluster_1492157219+%7B+%0A%09%09label+%3D+%22Block+%231%22%0A%09%091630288887%5Blabel%3D%22this+%3A%3D+%40this%3A+nl.uu.maze.examples.SimpleExample%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%09482375824%5Blabel%3D%22number+%3A%3D+%40parameter0%3A+int%22%5D%0A%09%09199922459%5Blabel%3D%22if+number+%26gt%3B%3D+0%22%5D%0A%0A%09%091630288887+-%3E+482375824+-%3E+199922459%0A%09%7D%0A%09199922459%3As+-%3E+1632606617%3An%5Blabel%3D%22false%22%5D%0A%09199922459%3As+-%3E+108050531%3An%5Blabel%3D%22true%22%5D%0A%0A%2F%2F++lines+%5B16%3A+16%5D+%0A%09subgraph+cluster_298940542+%7B+%0A%09%09label+%3D+%22Block+%232%22%0A%09%09108050531%5Blabel%3D%22return+%26quot%3BPositive%26quot%3B%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%2F%2F++lines+%5B14%3A+14%5D+%0A%09subgraph+cluster_2119465432+%7B+%0A%09%09label+%3D+%22Block+%233%22%0A%09%091632606617%5Blabel%3D%22return+%26quot%3BNegative%26quot%3B%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%7D>CFG
     *      for this method</a>
     */
    public String checkSign(int number) {
        if (number < 0) {
            return "Negative";
        } else {
            return "Positive";
        }
    }

    /**
     * A simple method that sums up the numbers from 1 to n.
     * 
     * @param n The number to sum up to
     * @return The sum of the numbers from 1 to n
     * @see <a
     *      href=http://magjac.com/graphviz-visual-editor/?dot=digraph+G+%7B%0A%09compound%3Dtrue%0A%09labelloc%3Db%0A%09style%3Dfilled%0A%09color%3Dgray90%0A%09node+%5Bshape%3Dbox%2Cstyle%3Dfilled%2Ccolor%3Dwhite%5D%0A%09edge+%5Bfontsize%3D10%2Carrowsize%3D1.5%2Cfontcolor%3Dgrey40%5D%0A%09fontsize%3D10%0A%0A%2F%2F++lines+%5B24%3A+25%5D+%0A%09subgraph+cluster_530112130+%7B+%0A%09%09label+%3D+%22Block+%231%22%0A%09%09108050531%5Blabel%3D%22this+%3A%3D+%40this%3A+nl.uu.maze.examples.SimpleExample%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%09176478743%5Blabel%3D%22n+%3A%3D+%40parameter0%3A+int%22%5D%0A%09%091924254751%5Blabel%3D%22sum+%3D+0%22%5D%0A%09%091426368852%5Blabel%3D%22i+%3D+1%22%5D%0A%0A%09%09108050531+-%3E+176478743+-%3E+1924254751+-%3E+1426368852%0A%09%7D%0A%091426368852%3As+-%3E+897161157%3An%0A%0A%2F%2F++lines+%5B25%3A+25%5D+%0A%09subgraph+cluster_368215159+%7B+%0A%09%09label+%3D+%22Block+%232%22%0A%09%09897161157%5Blabel%3D%22if+i+%26gt%3B+n%22%5D%0A%09%7D%0A%09897161157%3As+-%3E+229793671%3An%5Blabel%3D%22false%22%5D%0A%09897161157%3As+-%3E+34573059%3An%5Blabel%3D%22true%22%5D%0A%0A%2F%2F++lines+%5B28%3A+28%5D+%0A%09subgraph+cluster_1485037943+%7B+%0A%09%09label+%3D+%22Block+%233%22%0A%09%0934573059%5Blabel%3D%22return+sum%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%2F%2F++lines+%5B26%3A+25%5D+%0A%09subgraph+cluster_2063297966+%7B+%0A%09%09label+%3D+%22Block+%234%22%0A%09%09229793671%5Blabel%3D%22sum+%3D+sum+%2B+i%22%5D%0A%09%091463093567%5Blabel%3D%22i+%3D+i+%2B+1%22%5D%0A%09%091154376620%5Blabel%3D%22goto%22%5D%0A%0A%09%09229793671+-%3E+1463093567+-%3E+1154376620%0A%09%7D%0A%091154376620%3Ae+-%3E+897161157%3An%0A%0A%7D>CFG
     *      for this method</a>
     */
    public int sumUpTo(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }

    /**
     * A simple method that demonstrates a more complex execution tree.
     * 
     * @param a The first number
     * @param b The second number
     * @return A number representing the path taken in the execution tree
     * @see <a
     *      href=http://magjac.com/graphviz-visual-editor/?dot=digraph+G+%7B%0A%09compound%3Dtrue%0A%09labelloc%3Db%0A%09style%3Dfilled%0A%09color%3Dgray90%0A%09node+%5Bshape%3Dbox%2Cstyle%3Dfilled%2Ccolor%3Dwhite%5D%0A%09edge+%5Bfontsize%3D10%2Carrowsize%3D1.5%2Cfontcolor%3Dgrey40%5D%0A%09fontsize%3D10%0A%0A%2F%2F++lines+%5B42%3A+42%5D+%0A%09subgraph+cluster_1471260755+%7B+%0A%09%09label+%3D+%22Block+%231%22%0A%09%09976665895%5Blabel%3D%22this+%3A%3D+%40this%3A+nl.uu.maze.examples.SimpleExample%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%092063297966%5Blabel%3D%22a+%3A%3D+%40parameter0%3A+int%22%5D%0A%09%091872035706%5Blabel%3D%22b+%3A%3D+%40parameter1%3A+int%22%5D%0A%09%092047205172%5Blabel%3D%22if+a+%26lt%3B%3D+b%22%5D%0A%0A%09%09976665895+-%3E+2063297966+-%3E+1872035706+-%3E+2047205172%0A%09%7D%0A%092047205172%3As+-%3E+1644657469%3An%5Blabel%3D%22false%22%5D%0A%092047205172%3As+-%3E+1053854743%3An%5Blabel%3D%22true%22%5D%0A%0A%2F%2F++lines+%5B49%3A+49%5D+%0A%09subgraph+cluster_1599686761+%7B+%0A%09%09label+%3D+%22Block+%232%22%0A%09%091053854743%5Blabel%3D%22if+b+%26gt%3B%3D+5%22%5D%0A%09%7D%0A%091053854743%3As+-%3E+368215159%3An%5Blabel%3D%22false%22%5D%0A%091053854743%3As+-%3E+2120609175%3An%5Blabel%3D%22true%22%5D%0A%0A%2F%2F++lines+%5B51%3A+51%5D+%0A%09subgraph+cluster_740329568+%7B+%0A%09%09label+%3D+%22Block+%233%22%0A%09%092120609175%5Blabel%3D%22if+a+%26lt%3B%3D+b%22%5D%0A%09%7D%0A%092120609175%3As+-%3E+967804813%3An%5Blabel%3D%22false%22%5D%0A%092120609175%3As+-%3E+1075129278%3An%5Blabel%3D%22true%22%5D%0A%0A%2F%2F++lines+%5B54%3A+54%5D+%0A%09subgraph+cluster_1759434836+%7B+%0A%09%09label+%3D+%22Block+%234%22%0A%09%091075129278%5Blabel%3D%22return+5%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%2F%2F++lines+%5B52%3A+52%5D+%0A%09subgraph+cluster_956263923+%7B+%0A%09%09label+%3D+%22Block+%235%22%0A%09%09967804813%5Blabel%3D%22return+4%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%2F%2F++lines+%5B50%3A+50%5D+%0A%09subgraph+cluster_181291921+%7B+%0A%09%09label+%3D+%22Block+%236%22%0A%09%09368215159%5Blabel%3D%22return+3%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%2F%2F++lines+%5B43%3A+43%5D+%0A%09subgraph+cluster_1485037943+%7B+%0A%09%09label+%3D+%22Block+%237%22%0A%09%091644657469%5Blabel%3D%22if+a+%26lt%3B%3D+10%22%5D%0A%09%7D%0A%091644657469%3As+-%3E+270842418%3An%5Blabel%3D%22false%22%5D%0A%091644657469%3As+-%3E+769023670%3An%5Blabel%3D%22true%22%5D%0A%0A%2F%2F++lines+%5B46%3A+46%5D+%0A%09subgraph+cluster_1788451362+%7B+%0A%09%09label+%3D+%22Block+%238%22%0A%09%09769023670%5Blabel%3D%22return+2%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%2F%2F++lines+%5B44%3A+44%5D+%0A%09subgraph+cluster_1998044883+%7B+%0A%09%09label+%3D+%22Block+%239%22%0A%09%09270842418%5Blabel%3D%22return+1%22%2Cshape%3DMdiamond%2Ccolor%3Dgrey50%2Cfillcolor%3Dwhite%5D%0A%09%7D%0A%0A%7D>CFG
     *      for this method</a>
     */
    public int executionTree(int a, int b) {
        if (a > b) {
            if (a > 10) {
                return 1;
            } else {
                return 2;
            }
        } else {
            if (b < 5) {
                return 3;
            } else if (a > b) {
                return 4; // Unreachable branch
            } else {
                return 5;
            }

        }
    }
}
