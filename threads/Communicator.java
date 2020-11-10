package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {

        myLock = new Lock();
        speakCon = new Condition2(myLock);
        listenCon = new Condition2(myLock);
        returnCon = new Condition2(myLock);
        myword = 0;
        hasWord = false;

    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {

        myLock.acquire();
        while(hasWord)
        {
            speakCon.sleep();
        }
        myword = word;
        hasWord = true;

        listenCon.wake();
        returnCon.sleep();
        myLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        myLock.acquire();
        while(!hasWord)
        {
            listenCon.sleep();
        }
        speakCon.wake();
        returnCon.wake();
        int toReturn = myword;
        myword = 0;
        hasWord = false;
        myLock.release();
        return toReturn;

    }


    public static void CommTest() {
        final Communicator com = new Communicator();
        final long times[] = new long[4];
        final int words[] = new int[2];
        KThread speaker1 = new KThread( new Runnable () {
            public void run() {
                com.speak(4);
                times[0] = Machine.timer().getTime();
            }
        });
        speaker1.setName("S1");
        KThread speaker2 = new KThread( new Runnable () {
            public void run() {
                com.speak(7);
                times[1] = Machine.timer().getTime();
            }
        });
        speaker2.setName("S2");
        KThread listener1 = new KThread( new Runnable () {
            public void run() {
                times[2] = Machine.timer().getTime();
                words[0] = com.listen();
            }
        });

        listener1.setName("L1");
        KThread listener2 = new KThread( new Runnable () {
            public void run() {
                times[3] = Machine.timer().getTime();
                words[1] = com.listen();
            }
        });

        listener2.setName("L2");

        speaker1.fork(); speaker2.fork(); listener1.fork(); listener2.fork();
        speaker1.join(); speaker2.join(); listener1.join(); listener2.join();

        Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word.");
        Lib.assertTrue(words[1] == 7, "Didn't listen back spoken word.");
        Lib.assertTrue(times[0] > times[2], "speak() returned before listen() called.");
        Lib.assertTrue(times[1] > times[3], "speak() returned before listen() called.");
        System.out.println("commTest successful!");
    }

    public static void selfTest() {
        System.out.println();
        System.out.println("Communicator Test>>>>");
        // place calls to simpler Communicator tests that you implement here
        CommTest();

    }


    private Lock myLock;
    private Condition2 speakCon;
    private Condition2 listenCon;
    private Condition2 returnCon;
    private int myword;
    private boolean hasWord;
}
