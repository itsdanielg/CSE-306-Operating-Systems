package osp.Memory;

import java.util.*;
import java.lang.Math;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;

/**
 * ID: 111157499
 * Name: Daniel Garcia
 * Email: danieljedryl.garcia@stonybrook.edu
 * Project 2: Memory
 * Due Date: April 11, 2019
 * Pledge: I pledge my honor that all parts of this project were done by me
 * individually, without collaboration with anyone, and without consulting
 * external sources that help with similar projects.
 */

/**
    The MMU class contains the student code that performs the work of
    handling a memory reference.  It is responsible for calling the
    interrupt handler if a page fault is required.

    @OSPProject Memory
*/
public class MMU extends IflMMU {
    /** 
        This method is called once before the simulation starts. 
	Can be used to initialize the frame table and other static variables.

        @OSPProject Memory
    */

    // Declare the frame table
    public static ArrayList<FrameTableEntry> suitableFrames;

    public static void init() {
        int totalFrames = MMU.getFrameTableSize();
        // Initialize each frame in the frame table
        suitableFrames = new ArrayList<>();
        for (int i = 0; i < totalFrames; i++) {
            FrameTableEntry frameTableEntry = new FrameTableEntry(i);
            MMU.setFrame(i, frameTableEntry);
        }
    }

    /**
       This method handlies memory references. The method must 
       calculate, which memory page contains the memoryAddress,
       determine, whether the page is valid, start page fault 
       by making an interrupt if the page is invalid, finally, 
       if the page is still valid, i.e., not swapped out by another 
       thread while this thread was suspended, set its frame
       as referenced and then set it as dirty if necessary.
       (After pagefault, the thread will be placed on the ready queue, 
       and it is possible that some other thread will take away the frame.)
       
       @param memoryAddress A virtual memory address
       @param referenceType The type of memory reference to perform 
       @param thread that does the memory access
       (e.g., MemoryRead or MemoryWrite).
       @return The referenced page.

       @OSPProject Memory
    */
    static public PageTableEntry do_refer(int memoryAddress, int referenceType, ThreadCB thread) {
        int virtualAddressBits = MMU.getVirtualAddressBits();
        int pageAddressBits = MMU.getPageAddressBits();
        int bitsAllocated = virtualAddressBits - pageAddressBits;
        int pageSize = (int) Math.pow(2.0, bitsAllocated);
        // Find the page number of the memory address by dividing the address with the page size
        int pageNum = memoryAddress / pageSize;
        // Get the actual page using the page number
        PageTable pageTable = MMU.getPTBR();
        PageTableEntry pageTableEntry = pageTable.pages[pageNum];
        // Check if page is not valid
        if (!pageTableEntry.isValid()) {
            // Get the thread that caused a page fault
            ThreadCB pageFaultThread = pageTableEntry.getValidatingThread();
            // First case; Other thread caused a page fault
            if (pageFaultThread != null) {
                // Suspend the thread passed as a parameter
                thread.suspend(pageTableEntry);
            }
            // Second case; No other thread caused a pagefault 
            else {
                // Set the static fields of InterruptVector
                InterruptVector.setInterruptType(referenceType);
                InterruptVector.setPage(pageTableEntry);
                InterruptVector.setThread(thread);
                // Call interrupt, which will invoke the page fault handler
                CPU.interrupt(GlobalVariables.PageFault);
                // Once it returns, the thread will be ready
            }
            // If the thread was not destroyed while waiting for the page to be valid, set the reference and dirty bits of the page
            if (thread.getStatus() != GlobalVariables.ThreadKill) {
                return pageTableEntry;
            }
        }
        FrameTableEntry pageFrame = pageTableEntry.getFrame();
        // If the frame exists, set the reference and dirty bits of the page
        pageFrame.setReferenced(true);
        // Check if dirty bit should be modified
        if (referenceType == GlobalVariables.MemoryWrite) {
            pageFrame.setDirty(true);
        }
        return pageTableEntry;
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
     
	@OSPProject Memory
     */
    public static void atError() {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
     
      @OSPProject Memory
     */
    public static void atWarning() {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
