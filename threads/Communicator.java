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


    final Lock lock;
    final Condition2 speaker;
    final Condition2 listener;
    final Condition2 interCon;
    private int word;
    private boolean hasWord;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {

        lock = new Lock();
        speaker = new Condition2(lock);
        listener = new Condition2(lock);
        interCon = new Condition2(lock);
        word = 0;
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

        lock.acquire();
        while(hasWord)
        {
            speaker.sleep();
        }

        this.word = word;
        System.out.println("Word Spoken "+word);
        hasWord = true;

        listener.wake();
        interCon.sleep();
        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */

    public int listen() {
        lock.acquire();
        while(!hasWord)
        {
            listener.sleep();
        }

        speaker.wake();
        interCon.wake();
        int returnWord = this.word;
        System.out.println("Word listened "+returnWord);
        this.word = 0;
        hasWord = false;
        lock.release();
        return returnWord;

    }


    public static void CommTest() {
        final Communicator com = new Communicator();
        final long []timeCount = new long[6];
        final int []message = new int[3];
        KThread speaker1 = new KThread( new Runnable () {
            public void run() {
                com.speak(1);
                timeCount[0] = Machine.timer().getTime();
            }
        });
        speaker1.setName("S1");
        KThread speaker2 = new KThread( new Runnable () {
            public void run() {
                com.speak(2);

                timeCount[1] = Machine.timer().getTime();
            }
        });
        speaker2.setName("S2");

        KThread speaker3 = new KThread( new Runnable () {
            public void run() {
                com.speak(3);

                timeCount[2] = Machine.timer().getTime();
            }
        });
        speaker2.setName("S3");

        KThread listener1 = new KThread( new Runnable () {
            public void run() {
                timeCount[3] = Machine.timer().getTime();
                message[0] = com.listen();
            }
        });

        listener1.setName("L1");
        KThread listener2 = new KThread( new Runnable () {
            public void run() {
                timeCount[4] = Machine.timer().getTime();
                message[1] = com.listen();
            }
        });

        listener2.setName("L2");

        KThread listener3 = new KThread( new Runnable () {
            public void run() {
                timeCount[5] = Machine.timer().getTime();
                message[2] = com.listen();
            }
        });

        listener2.setName("L3");

        speaker1.fork();
        speaker2.fork();
        speaker3.fork();
        listener1.fork();
        listener2.fork();
        listener2.join();
        listener3.fork();
        speaker3.join();
        speaker1.join();
        listener1.join();
        speaker2.join();
        listener3.join();


        Lib.assertTrue(message[0] == 1, "Didn't listen back spoken word.");
        Lib.assertTrue(message[1] == 2, "Didn't listen back spoken word.");
        Lib.assertTrue(message[2] == 3, "Didn't listen back spoken word.");
        Lib.assertTrue(timeCount[0] > timeCount[3], "speak() returned before listen() called.");
        Lib.assertTrue(timeCount[1] > timeCount[4], "speak() returned before listen() called.");
        Lib.assertTrue(timeCount[2] > timeCount[5], "speak() returned before listen() called.");

        System.out.println("commTest successful!");
    }

    public static void selfTest() {
        System.out.println();
        System.out.println("Communicator Test>>>>");
        // place calls to simpler Communicator tests that you implement here
        CommTest();

    }


}
