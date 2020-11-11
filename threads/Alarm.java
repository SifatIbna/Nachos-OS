package nachos.threads;

import nachos.machine.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        threadMap = new HashMap<KThread, Long>();

        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() { timerInterrupt(); }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {

        long currTime = Machine.timer().getTime();

         for (Map.Entry<KThread, Long> entry : threadMap.entrySet()) {
             KThread thread = entry.getKey();
             Long value = entry.getValue();

             if (value <= currTime){
                 thread.ready();
                 threadMap.remove(thread);
             }
         }

	    KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {

        //long wakeTime = Machine.timer().getTime() + x;
        //while (wakeTime > Machine.timer().getTime())
        //	KThread.yield();

        //get wake time
        if(x > 0)
        {
            long wakeTime = Machine.timer().getTime() + x;
//            System.out.println(wakeTime);
            Machine.interrupt().setStatus(false);
            KThread currentThread = KThread.currentThread();
            threadMap.put(currentThread, wakeTime);
            currentThread.sleep();
            Machine.interrupt().restore(true);
        }

    }

    public static void alarmTest() {
        int [] times  = {1, 10*100, 100*1000};

        long initialTime, finishTime;

        for (int d : times) {
            initialTime = Machine.timer().getTime();
            // new Alarm().waitUntil(d); // Doesn't Work
            ThreadedKernel.alarm.waitUntil(d);
            finishTime = Machine.timer().getTime();
            System.out.println ("Waiting for " + (finishTime - initialTime) + " ticks");
        }
    }

    public static void selfTest() {
        System.out.println();
        System.out.println("Alarm Test >>>>>:");
        alarmTest();
        // Invoke your other test methods here ...
    }

    final HashMap<KThread, Long> threadMap;
}
