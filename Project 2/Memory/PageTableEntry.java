package osp.Memory;

import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;

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
   The PageTableEntry object contains information about a specific virtual
   page in memory, including the page frame in which it resides.
   
   @OSPProject Memory

*/

public class PageTableEntry extends IflPageTableEntry {
    /**
       The constructor. Must call

       	   super(ownerPageTable,pageNumber);
	   
       as its first statement.

       @OSPProject Memory
    */
    public PageTableEntry(PageTable ownerPageTable, int pageNumber) {
        super(ownerPageTable, pageNumber);
    }

    /**
       This method increases the lock count on the page by one. 

	The method must FIRST increment lockCount, THEN  
	check if the page is valid, and if it is not and no 
	page validation event is present for the page, start page fault 
	by calling PageFaultHandler.handlePageFault().

	@return SUCCESS or FAILURE
	FAILURE happens when the pagefault due to locking fails or the 
	that created the IORB thread gets killed.

	@OSPProject Memory
     */
    public int do_lock(IORB iorb) {
        FrameTableEntry currentFrame = super.getFrame();
        // Check if the frame exists for this page
        if (currentFrame != null) {
            // Check if this page is valid
            if (super.isValid()) {
                // If the page is valid, increment the lock count
                currentFrame.incrementLockCount();
                return SUCCESS;
            }
            else {
                // Get current thread and thread that caused a pagefault
                ThreadCB currentThread = iorb.getThread();
                ThreadCB pagefaultedThread = super.getValidatingThread();
                // Check if the current thread exists
                if (currentThread != null) {
                    // Check if the thread that caused a pagefault exists
                    if (pagefaultedThread != null) {
                        // If both threads are the same, increment the lock count
                        if (pagefaultedThread == currentThread) {
                            currentFrame.incrementLockCount();
                            return SUCCESS;
                        }
                        // Else, wait until the page becomes valid
                        else {
                            currentThread.suspend(this);
                            // If this page becomes valid, increment the lock count
                            if (super.isValid()) {
                                currentFrame.incrementLockCount();
                                return SUCCESS;
                            }
                        }
                    }
                    // Else, page is not involved in pagefault, so it must handle a pagefault
                    else {
                        PageFaultHandler.handlePageFault(currentThread, GlobalVariables.MemoryLock, this);
                        // If this thread was not killed during wait time, increment the lock count
                        if (currentThread.getStatus() != ThreadKill) {
                            currentFrame.incrementLockCount();
                            return SUCCESS;
                        }
                    }
                }
            }
        }
        return FAILURE;
    }

    /** This method decreases the lock count on the page by one. 

	This method must decrement lockCount, but not below zero.

	@OSPProject Memory
    */
    public void do_unlock() {
        FrameTableEntry currentFrame = super.getFrame();
        // If this frame exists, continue with unlocking
        if (currentFrame != null) {
            int currentLockCount = currentFrame.getLockCount();
            // Only unlock if the current lock count is above 0
            if (currentLockCount > 0) {
                super.getFrame().decrementLockCount();
            }
        }
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
