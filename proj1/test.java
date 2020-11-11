package nachos.proj1;

import nachos.machine.Lib;
import nachos.threads.Condition2;
import nachos.threads.KThread;
import nachos.threads.Lock;

import java.util.LinkedList;

public class test{

    public static void initiateTest(){
        new JoinTest().PerformTest();
        Condition2Test.ConditionTest();
    }
}

class JoinTest {

    public JoinTest(){}

    public void PerformTest() {
        System.out.println("Testing >>>");

        KThread t0 = new KThread(new PingTest(0)).setName("Forked thread 0");
        System.out.println("Forked thread  0 and Joining");
        t0.fork();
        t0.join();
        System.out.println("Joined with thread 0");

        System.out.println();
        new KThread(new PingTest(1)).setName("Forked thread 1").fork();
        new KThread(new PingTest(2)).setName("Forked thread 2").fork();
        new PingTest(0).run();

        System.out.println();

        KThread t1 = new KThread(new PingTest(1)).setName("Forked thread 1");
        KThread t2 = new KThread(new PingTest(2)).setName("Forked thread 2");

        t1.fork();
        t2.fork();

        t1.join();
        t2.join();

        System.out.println();
//        new PingTest(0).run();
    }

    private static class PingTest implements Runnable {
        private final int which;

        PingTest(int which){
            this.which = which;
        }

        @Override
        public void run() {
            for(int i=0;i<5;i++){
                System.out.println("Thread "+which+" looped "+i+" times");
                KThread.yield();

            }
        }
    }

}

class Condition2Test {
    public Condition2Test(){

    }

    public static void ConditionTest() {
        final Lock lock = new Lock();
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
                while(list.isEmpty()){
                    empty.sleep();
                }

                Lib.assertTrue(list.size() == 5, "List should have 5 values.");

                while(!list.isEmpty()) {
                    // context swith for the fun of it
                    KThread.currentThread().yield();
                    System.out.println("Removed " + list.removeFirst());
                }
                lock.release();
            }
        });

        KThread producer = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
                for (int i = 0; i < 5; i++) {
                    list.add(i);
                    System.out.println("Added " + i);
                    // context swith for the fun of it
                    KThread.currentThread().yield();
                }
                empty.wake();
                lock.release();
            }
        });

        consumer.setName("Consumer");
        producer.setName("Producer");

        consumer.fork();
        producer.fork();

        consumer.join();
        producer.join();
    }
}


