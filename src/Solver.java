import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Aaron Muir
 * Adam Julovich
 * CS 309
 * LP Project
 */
public class Solver
{
    private Matrix initial;
    protected ArrayList<Matrix> tables;
    private ArrayList<Matrix> optimal;
    private ArrayList<Matrix> optimalAux;
    private int infeasibleCount;
    private int unboundedCount;
    private int optimalCount;

    private enum Method
    {
        simplex,
        twoPhaseSimplex
    }
    private Method solveMethod;

    private Printer printer;

    /**
     * LP solving algorithms
     */
    public Solver(Matrix original, Printer printer)
    {
        tables = new ArrayList<>();
        optimal = new ArrayList<>();
        optimalAux = new ArrayList<>();
        this.initial = original;
        this.printer = printer;
        infeasibleCount=0;
        unboundedCount=0;
        optimalCount=0;
        this.solve();
    }

    /**
     * Print the results of the solve
     */
    public void PrintResults()
    {
        if(optimal.size()>0)
        {
            DecimalFormat df = new DecimalFormat(" #0.00;-#");

            printer.Print("\r\n");
            printer.Print("-----------------\r\n");
            printer.Print("OPTIMAL SOLUTIONS\r\n");
            printer.Print("-----------------\r\n");
            printer.Print("Optimal Matrices: "+optimalCount+"\r\n");
            for (Matrix m : optimal)
            {
                Double objValue = m.getObjValue();
                printer.Print("Optimal Value: " + df.format(objValue)+"\r\n");
                printer.Print(m.getSolution().toString());
            }
        }
        else
        {
            printer.Print("-----------------\r\n");
            printer.Print("NO SOLUTIONS FOUND\r\n");
            printer.Print("-----------------\r\n");

            printer.Print("Infeasible Matrices: "+infeasibleCount+"\r\n");
            printer.Print("Unbounded Matrices: "+unboundedCount+"\r\n");
        }
    }

    /**
     * Solve the LP problem
     */
    private void solve()
    {
        try
        {
            printer.Print("Initial Matrix\r\n");
            printer.Print(initial.toString());

            this.determineSolveMethod();

            printer.Print("Using method "+ solveMethod.name()+"...\r\n");

            // copy the initial so it is preserved
            tables.add(initial.copy());

            if (solveMethod == Method.simplex)
            {
                simplex(initial);
            }
            else if (solveMethod == Method.twoPhaseSimplex)
            {
                twoPhaseSimplex(initial);
            }
            else
                throw new Exception("Unexpected solve method being utilized");

        }
        catch(Exception ex)
        {
            ExceptionHandler.Handle(ex);
        }
    }

    /**
     * simplex method of solving
     */
    private void simplex(Matrix current)
    {
        try
        {
            moveToAdjBfs(current);
        }
        catch (Exception ex)
        {
            ExceptionHandler.Handle(ex);
        }
    }

    /**
     * two-phase simplex method
     */
    private void twoPhaseSimplex(Matrix current)
    {
        try
        {
            // create an auxiliary row & column in the current matrix
            current.createAuxiliary();

            printer.Print("Auxiliary Created\r\n");
            printer.Print(current.toString());

            // phase 1 - solve auxiliary function

            // choose variable x0 to enter the basis
            current = selectX0Pivot(current);

            // repeatedly improve aux obj W until W is zero
            moveToAdjBfs(current);

            // generate list of optimal auxiliaries in order to re-purpose the optimal list
            for (Matrix solvedAux : optimal)
            {
                // add to list of optimal auxiliaries (phase 1 complete)
                optimalAux.add(solvedAux);
            }
            optimal.clear();
            optimalCount=0;

            // solve all optimal auxiliaries using the simplex method
            for (Matrix solvedAux : optimalAux)
            {
                // remove auxiliary remnants
                solvedAux.removeAuxiliary();

                // if the solved aux is not infeasible and not unbounded
                if (!solvedAux.isInfeasible() && !solvedAux.isUnbounded())
                {
                    // continue to solve using simplex
                    simplex(solvedAux);
                }
            }
        }
        catch (Exception ex)
        {
            ExceptionHandler.Handle(ex);
        }
    }

    /**
     * Improve the objective
     *
     * @param matrix matrix to improve
     * @return returns when selected pivot produces optimal,infeasible, or unbounded matrix
     */
    private void moveToAdjBfs(Matrix matrix)
    {
        tables.add(matrix);
        try
        {
            // a. choose column i to enter the basis by finding largest Ai0
            ArrayList<Integer> cols = getLargestAi0(matrix);

            // if all Ai0 (not b) are <=0 (matrix is optimal)
            if (matrix.isOptimal())
            {
                if (matrix.hasAuxiliary())
                {
                    if (matrix.getObjValue() != 0)
                    {
                        printer.Print("Matrix is infeasible.\r\n");
                        printer.Print(matrix.toString());
                        matrix.flagInfeasible();
                        infeasibleCount++;
                        return;
                    }
                }
                printer.Print("Matrix is optimal. Adding to list of optimal solutions.\r\n");
                printer.Print(matrix.toString());
                optimalCount++;
                optimal.add(matrix);
            }
            else
            {
                // b. choose the row j of the variable to leave
                // list of points to leave the basis
                ArrayList<Point> points = pointsToPivot(matrix, cols);

                // if list of aij is size 0, unbounded
                if (points.size() <= 0)
                {
                    matrix.flagUnbounded();
                    printer.Print("Matrix is unbounded.\r\n");
                    unboundedCount++;
                    return;
                }

                // if this matrix has an aux and x0=1 in any of the points found,
                // we only pivot on that point
                ArrayList<Point> x0points = new ArrayList<>();
                for (Point p : points)
                {
                    if (matrix.hasAuxiliary())
                    {
                        if (matrix.getRow(p.getY()).getElement(0) == 1)
                        {
                            x0points.add(p);
                        }
                    }
                }
                if(x0points.size()>0)
                {
                    points.clear();
                    for(Point x : x0points)
                    {
                        points.add(x);
                    }
                }

                // c. for each aij in list
                // pivot on every aij and move to adj bfs on resulting matrices
                // prioritize x0 points

                printer.Print("Found the following points to pivot on: ");
                for (Point p : points)
                {
                    printer.Print(p.toString() + " ");
                }
                printer.Print("\r\n");

                for (Point p : points)
                {
                    Matrix m = pivot(matrix, p).copy();
                    moveToAdjBfs(m);
                }

            }
        }
        catch (Exception ex)
        {
            ExceptionHandler.Handle(ex);
        }
    }

    /**
     * Find a list of points in the matrix to pivot on
     * @param m - matrix
     * @param cols - pivot columns
     * @return list of points to pivot on
     */
    private ArrayList<Point> pointsToPivot(Matrix m, ArrayList<Integer> cols)
    {
        ArrayList<Point> pivotPoints = new ArrayList<>();

        try
        {
            ArrayList<Point> validPoints = new ArrayList<>();
            // find points where aij > 0
            for(Integer i:cols)
            {
                // skip the first row
                int firstRow = 1;
                if(m.hasAuxiliary())
                    firstRow=2;

                for(int j=firstRow;j<m.getRowSize();j++)
                {
                    if(m.getValue(i,j)>0)
                        validPoints.add(new Point(i,j));
                }
            }

            // find minimum bj/aij for every i where aij > 0
            Double min=Double.MAX_VALUE;
            for(Point p:validPoints)
            {
                Double b = m.getValue(m.getColumnSize()-1,p.getY());
                Double Aij = m.getValue(p.getX(),p.getY());

                assert Aij != 0.0;

                if(b/Aij < min)
                    min = b/Aij;
            }

            // create list of points containing minimum bj/aij
            for(Point p:validPoints)
            {
                Double b = m.getValue(m.getColumnSize()-1,p.getY());
                Double Aij = m.getValue(p.getX(),p.getY());

                if(min.equals(b / Aij))
                    pivotPoints.add(p);
            }
            return pivotPoints;
        }
        catch (Exception ex)
        {
            ExceptionHandler.Handle(ex);
            return null;
        }
    }

    /**
     * Finds all columns with the largest possible value in row zero
     * @param m matrix
     * @return column index with the largest value
     */
    ArrayList<Integer> getLargestAi0(Matrix m)
    {
        ArrayList<Integer> cols = new ArrayList<>();
        try
        {
            // find ALL i with largest Ai0
            AugRow rowZero = m.getRow(0);

            // find the largest value - omit b column
            Double largest=0.0;
            for(int i=0; i<rowZero.size()-1;i++)
            {
                if (rowZero.getElement(i) > largest)
                    largest = rowZero.getElement(i);
            }
            // build list of columns with the value
            for(int i=0;i<rowZero.size()-1;i++)
            {
                Double val = rowZero.getElement(i);
                if(val.equals(largest))
                    cols.add(i);
            }
            return cols;
        }
        catch (Exception ex)
        {
            ExceptionHandler.Handle(ex);
            return null;
        }
    }

    /**
     * find and pivot on row in column x0
     * @param matrix matrix
     */
    private Matrix selectX0Pivot(Matrix matrix)
    {
        try
        {
            // find the row of the most negative basic var.
            // default to row 2 if no negatives found.
            int negRow = 2;
            Double mostNegB = 0.0;

            for (int j = 2; j < matrix.getRowSize(); j++)
            {
                if (matrix.getRow(j).getB() < mostNegB)
                {
                    mostNegB = matrix.getRow(j).getB();
                    negRow = j;
                }
            }

            Point pivotPoint = new Point(0, negRow);

            // pivot on x0 in the row with the most negative basic
            return pivot(matrix, pivotPoint).copy();
        }
        catch (Exception ex)
        {
            ExceptionHandler.Handle(ex);
            return null;
        }
    }

    /**
     * determine the solve method to be used
     */
    private void determineSolveMethod()
    {
        if(initial.getSolution().isBfs())
            solveMethod = Method.simplex;
        else
            solveMethod = Method.twoPhaseSimplex;

    }

    /**
     * Pivot on an element in row j column i reducing xi to an identity vector.
     * xi enters the basis.
     *
     * @param matrix matrix to pivot on
     * @param p point to pivot on
     */
    private Matrix pivot(Matrix matrix, Point p)
    {
        try
        {
            printer.Print("Before pivot on " + p + "\r\n");
            printer.Print(matrix.toString(p));

            int i = p.getX();
            int j = p.getY();

            // create a new copy of the matrix before pivoting
            Matrix m = matrix.copy();

            Double Aij = m.getValue(i, j);

            // reduce Aij to 1
            m.setValue(i, j, 1.0);

            // reduce all other elements in row j by Aij
            AugRow rowJ = m.getRow(j);
            for (int k = 0; k < m.getColumnSize(); k++)
            {
                if (k != i)
                {
                    double val = rowJ.getElement(k) / Aij;
                    rowJ.setElement(k, val);
                }
            }

            // reduce elements in all other rows
            for (int l = 0; l < m.getRowSize(); l++)
            {
                if (l != j)
                {
                    // find the row multiplier y in the equation Ail - Aij*y = 0; y = Ail / Aij
                    Double y = m.getValue(i, l);
                    m.setValue(i, l, 0.0);

                    // reduce all other elements using multiplier
                    for (int k = 0; k < m.getColumnSize(); k++)
                    {
                        if (k != i)
                        {
                            // Akl = Akl - Akj*y
                            Double val = m.getValue(k, l) - m.getValue(k, j) * y;

                            m.setValue(k, l, val);
                        }
                    }
                }
            }
            printer.Print("After Pivot\r\n");
            printer.Print(m.toString());
            return m;
        }
        catch (Exception ex)
        {
            ExceptionHandler.Handle(ex);
            return null;
        }
    }
}
