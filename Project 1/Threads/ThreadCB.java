package osp.Threads;

import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;
import java.util.ArrayList;

/**
 * ID: 111157499
 * Name: Daniel Garcia
 * Email: danieljedryl.garcia@stonybrook.edu
 * Project 1: Threads
 * Due Date: March 12, 2019
 * Pledge: I pledge my honor that all parts of this project were done 
 * by me individually, without collaboration with anyone, and without 
 * consulting any external sources that could help with similar 
 * projects.
*/

/**
 * This class is responsible for actions related to threads, including creating,
 * killing, dispatching, resuming, and suspending threads.
 * 
 * @OSPProject Threads
 */
public class ThreadCB extends IflThreadCB {

    static ArrayList<ThreadCB> readyQueue;
    static ArrayList<ThreadCB> allThreads;
    long timePutInQueue;
    long timeReady;
    boolean highestPriorityTimeSlice;

    /**
     * The thread constructor. Must call
     * 
     * super();
     * 
     * as its first statement.
     * 
     * @OSPProject Threads
     */
    public ThreadCB() {
        super();
    }

    /**
     * This method will be called once at the beginning of the simulation. The
     * student can set up static variables here.
     * 
     * @OSPProject Threads
     */
    public static void init() {
        readyQueue = new ArrayList<>();
        allThreads = new ArrayList<>();
    }

    /**
     * Sets up a new thread and adds it to the given task. The method must set the
     * ready status and attempt to add thread to task. If the latter fails because
     * there are already too many threads in this task, so does this method,
     * otherwise, the thread is appended to the ready queue and dispatch() is
     * called.
     * 
     * The priority of the thread can be set using the getPriority/setPriority
     * methods. However, OSP itself doesn't care what the actual value of the
     * priority is. These methods are just provided in case priority scheduling is
     * required.
     * 
     * @return thread or null
     * 
     * @OSPProject Threads
     */
    static public ThreadCB do_create(TaskCB task) {
        if (task == null) {
            dispatch();
            return null;
        } else if (task.getThreadCount() >= MaxThreadsPerTask) {
            dispatch();
            return null;
        }
        ThreadCB threadCB = new ThreadCB();
        threadCB.setTask(task);
        int check = task.addThread(threadCB);
        if (check == FAILURE) {
            dispatch();
            return null;
        }
        threadCB.setStatus(ThreadReady);
        threadCB.timePutInQueue = System.currentTimeMillis();
        threadCB.timeReady = 0;
        threadCB.highestPriorityTimeSlice = false;
        readyQueue.add(threadCB);
        allThreads.add(threadCB);
        dispatch();
        return threadCB;
    }

    /**
     * Kills the specified thread.
     * 
     * The status must be set to ThreadKill, the thread must be removed from the
     * task's list of threads and its pending IORBs must be purged from all device
     * queues.
     * 
     * If some thread was on the ready queue, it must removed, if the thread was
     * running, the processor becomes idle, and dispatch() must be called to resume
     * a waiting thread.
     * 
     * @OSPProject Threads
     */
    public void do_kill() {
        int currentState = this.getStatus();
        TaskCB currentTask = this.getTask();
        if (currentState == ThreadReady) {
            readyQueue.remove(this);
        } else if (currentState == ThreadRunning) {
            currentTask.setCurrentThread(null);
            MMU.setPTBR(null);
        } else if (currentState >= ThreadWaiting) {
            for (int i = 0; i < Device.getTableSize(); i++) {
                Device.get(i).cancelPendingIO(this);
            }
        }
        this.setStatus(ThreadKill);
        ResourceCB.giveupResources(this);
        dispatch();
        currentTask.removeThread(this);
        if (currentTask.getThreadCount() == 0) {
            currentTask.kill();
        }
    }

    /**
     * Suspends the thread that is currenly on the processor on the specified event.
     * 
     * Note that the thread being suspended doesn't need to be running. It can also
     * be waiting for completion of a pagefault and be suspended on the IORB that is
     * bringing the page in.
     * 
     * Thread's status must be changed to ThreadWaiting or higher, the processor set
     * to idle, the thread must be in the right waiting queue, and dispatch() must
     * be called to give CPU control to some other thread.
     * 
     * @param event - event on which to suspend this thread.
     * 
     * @OSPProject Threads
     */
    public void do_suspend(Event event) {
        int currentState = this.getStatus();
        if (currentState == ThreadRunning) {
            this.setStatus(ThreadWaiting);
            MMU.setPTBR(null);
            this.getTask().setCurrentThread(null);
        } else if (currentState >= ThreadWaiting) {
            this.setStatus(currentState + 1);
        }
        if (!(event.contains(this))) {
            event.addThread(this);
        }
        dispatch();
    }

    /**
     * Resumes the thread.
     * 
     * Only a thread with the status ThreadWaiting or higher can be resumed. The
     * status must be set to ThreadReady or decremented, respectively. A ready
     * thread should be placed on the ready queue.
     * 
     * @OSPProject Threads
     */
    public void do_resume() {
        if (this.getStatus() == ThreadWaiting) {
            this.setStatus(ThreadReady);
            this.timePutInQueue = System.currentTimeMillis();
            readyQueue.add(this);
        } else if (this.getStatus() > ThreadWaiting) {
            this.setStatus(this.getStatus() - 1);
        } else {
            return;
        }
        dispatch();
    }

    /**
     * Selects a thread from the run queue and dispatches it.
     * 
     * If there is just one theread ready to run, reschedule the thread currently on
     * the processor.
     * 
     * In addition to setting the correct thread status it must update the PTBR.
     * 
     * @return SUCCESS or FAILURE
     * 
     * @OSPProject Threads
     */
    public static int do_dispatch() {
        ThreadCB currentRunningThread = null;
        try {
            currentRunningThread = MMU.getPTBR().getTask().getCurrentThread();
            if (HTimer.get() == 0) {
                currentRunningThread.setStatus(ThreadReady);
                currentRunningThread.timePutInQueue = System.currentTimeMillis();
                readyQueue.add(currentRunningThread);
                currentRunningThread.getTask().setCurrentThread(null);
                MMU.setPTBR(null);
                currentRunningThread = null;
            } else if (HTimer.get() > 10) {
                currentRunningThread.highestPriorityTimeSlice = true;
                currentRunningThread.setStatus(ThreadReady);
                currentRunningThread.timePutInQueue = System.currentTimeMillis();
                readyQueue.add(currentRunningThread);
                currentRunningThread.getTask().setCurrentThread(null);
                MMU.setPTBR(null);
                currentRunningThread = null;
            }
        } catch (NullPointerException e) {
            MMU.setPTBR(null);
            if (readyQueue.isEmpty()) {
                return FAILURE;
            }
        }
        updateAllThreads();
        ThreadCB maxPriorityThread = getHighestPriorityThread();
        if (maxPriorityThread == null) {
            return SUCCESS;
        }
        if (currentRunningThread == null) {
            if (maxPriorityThread.highestPriorityTimeSlice == true) {
                maxPriorityThread.highestPriorityTimeSlice = false;
            }
            readyQueue.remove(maxPriorityThread);
            maxPriorityThread.setStatus(ThreadRunning);
            MMU.setPTBR(maxPriorityThread.getTask().getPageTable());
            maxPriorityThread.getTask().setCurrentThread(maxPriorityThread);
            HTimer.set(100);
            return SUCCESS;
        }
        if (maxPriorityThread.highestPriorityTimeSlice == true) {
            maxPriorityThread.highestPriorityTimeSlice = false;
            currentRunningThread.setStatus(ThreadReady);
            currentRunningThread.timePutInQueue = System.currentTimeMillis();
            readyQueue.add(currentRunningThread);
            currentRunningThread.getTask().setCurrentThread(null);
            MMU.setPTBR(null);
        } else if (maxPriorityThread.getPriority() > currentRunningThread.getPriority()) {
            currentRunningThread.setStatus(ThreadReady);
            currentRunningThread.timePutInQueue = System.currentTimeMillis();
            readyQueue.add(currentRunningThread);
            currentRunningThread.getTask().setCurrentThread(null);
            MMU.setPTBR(null);
        } else {
            return SUCCESS;
        }
        readyQueue.remove(maxPriorityThread);
        maxPriorityThread.setStatus(ThreadRunning);
        MMU.setPTBR(maxPriorityThread.getTask().getPageTable());
        maxPriorityThread.getTask().setCurrentThread(maxPriorityThread);
        HTimer.set(100);
        return SUCCESS;
    }

    /**
     * Called by OSP after printing an error message. The student can insert code
     * here to print various tables and data structures in their state just after
     * the error happened. The body can be left empty, if this feature is not used.
     * 
     * @OSPProject Threads
     */
    public static void atError() {
        // your code goes here

    }

    /**
     * Called by OSP after printing a warning message. The student can insert code
     * here to print various tables and data structures in their state just after
     * the warning happened. The body can be left empty, if this feature is not
     * used.
     * 
     * @OSPProject Threads
     */
    public static void atWarning() {
        // your code goes here

    }

    public static void updateAllThreads() {
        if (readyQueue.isEmpty()) {
            return;
        } else {
            for (ThreadCB thread : readyQueue) {
                thread.timeReady = System.currentTimeMillis() - thread.timePutInQueue;
                thread.setPriority(calculatePriority(thread));
            }
        }
    }

    public static int calculatePriority(ThreadCB thread) {
        int totalCPUTime = 0;
        TaskCB currentTask = thread.getTask();
        for (ThreadCB currentThread : allThreads) {
            if (currentThread.getTask() == currentTask) {
                totalCPUTime += currentThread.getTimeOnCPU();
            }
        }
        int priority = (int) (thread.timeReady / (1 + totalCPUTime));
        return priority;
    }

    public static ThreadCB getHighestPriorityThread() {
        if (readyQueue.isEmpty()) {
            return null;
        }
        ThreadCB currentMaxThread = readyQueue.get(0);
        for (ThreadCB thread : readyQueue) {
            if (thread.highestPriorityTimeSlice == true) {
                currentMaxThread = thread;
                break;
            }
            if (currentMaxThread.getPriority() < thread.getPriority()) {
                currentMaxThread = thread;
            }
        }
        return currentMaxThread;
    }

}

/*
 * Feel free to add local classes to improve the readability of your code
 */
