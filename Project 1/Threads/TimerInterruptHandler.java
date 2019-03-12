package osp.Threads;

import osp.IFLModules.*;
import osp.Utilities.*;
import osp.Hardware.*;

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
 * The timer interrupt handler. This class is called upon to handle timer
 * interrupts.
 * 
 * @OSPProject Threads
 */
public class TimerInterruptHandler extends IflTimerInterruptHandler {
    /**
     * This basically only needs to reset the times and dispatch another process.
     * 
     * @OSPProject Threads
     */
    public void do_handleInterrupt() {
        IflThreadCB.dispatch();
    }

    /*
     * Feel free to add methods/fields to improve the readability of your code
     */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */
