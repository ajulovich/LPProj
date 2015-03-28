/**
 * Aaron Muir
 * Adam Julovich
 * CS 309
 * LP Project
 */

public class Eps
{
    public static final double eps = 1.0E-10;

    public static double zero(double val)
    {
        if(Math.abs(val)<eps)
            return 0.0;
        else
            return val;
    }
}
