package client;

/**
 * Created by Phil Tremblay on 2015-11-09.
 */
public class StopWatch {
    private final long start;

    /**
     * Initializes a new stopwatch.
     */
    public StopWatch() {
        start = System.currentTimeMillis();
    }


    /**
     * Returns the elapsed CPU time (in seconds) since the stopwatch was created.
     *
     * @return elapsed CPU time (in seconds) since the stopwatch was created
     */
    public double elapsedTime() {
        long now = System.currentTimeMillis();
        return (now - start);
    }
}
