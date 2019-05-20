package osp.Memory;
import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
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
    The page fault handler is responsible for handling a page
    fault.  If a swap in or swap out operation is required, the page fault
    handler must request the operation.

    @OSPProject Memory
*/
public class PageFaultHandler extends IflPageFaultHandler {
    /**
        This method handles a page fault. 

        It must check and return if the page is valid, 

        It must check if the page is already being brought in by some other
	thread, i.e., if the page's has already pagefaulted
	(for instance, using getValidatingThread()).
        If that is the case, the thread must be suspended on that page.
        
        If none of the above is true, a new frame must be chosen 
        and reserved until the swap in of the requested 
        page into this frame is complete. 

	Note that you have to make sure that the validating thread of
	a page is set correctly. To this end, you must set the page's
	validating thread using setValidatingThread() when a pagefault
	happens and you must set it back to null when the pagefault is over.

        If a swap-out is necessary (because the chosen frame is
        dirty), the victim page must be dissasociated 
        from the frame and marked invalid. After the swap-in, the 
        frame must be marked clean. The swap-ins and swap-outs 
        must are preformed using regular calls read() and write().

        The student implementation should define additional methods, e.g, 
        a method to search for an available frame.

	Note: multiple threads might be waiting for completion of the
	page fault. The thread that initiated the pagefault would be
	waiting on the IORBs that are tasked to bring the page in (and
	to free the frame during the swapout). However, while
	pagefault is in progress, other threads might request the same
	page. Those threads won't cause another pagefault, of course,
	but they would enqueue themselves on the page (a page is also
	an Event!), waiting for the completion of the original
	pagefault. It is thus important to call notifyThreads() on the
	page at the end -- regardless of whether the pagefault
	succeeded in bringing the page in or not.

        @param thread the thread that requested a page fault
        @param referenceType whether it is memory read or write
        @param page the memory page 

	@return SUCCESS is everything is fine; FAILURE if the thread
	dies while waiting for swap in or swap out or if the page is
	already in memory and no page fault was necessary (well, this
	shouldn't happen, but...). In addition, if there is no frame
	that can be allocated to satisfy the page fault, then it
	should return NotEnoughMemory

        @OSPProject Memory
    */
    public static int do_handlePageFault(ThreadCB thread, int referenceType, PageTableEntry page) {
        // Check if page is invalid in order to proceed with handler
        if (!page.isValid()) {
            // Check if page is in the middle of a pagefault
            if (page.getValidatingThread() == null) {
                // Check if all frames are locked or reserved
                int memoryStatus = checkMemory();
                // Start processing page fault if there is a frame available
                if (memoryStatus == SUCCESS) {
                    // Create SystemEvent object and store in a variable for later use
                    SystemEvent pageFaultEvent = new SystemEvent("Processing page fault");
                    // Suspend the thread
                    thread.suspend(pageFaultEvent);
                    // Set the validating thread
                    page.setValidatingThread(thread);
                    // Find and reserve frame (Using M2HC algorithm)
                    FrameTableEntry eligibleFrame = M2HCDaemon.chooser();
                    eligibleFrame.setReserved(thread.getTask());
                    // Get the page of the eligible frame
                    PageTableEntry eligibleFramePage = eligibleFrame.getPage();
                    // Check if the frame is already free
                    if (eligibleFramePage == null) {
                        // Set the frame before swap in
                        page.setFrame(eligibleFrame);
                        // Perform swap-in
                        doSwapIn(page, thread);
                        // Check if thread was killed during that time
                        if (thread.getStatus() == GlobalVariables.ThreadKill) {
                            swapInCleanup(page, eligibleFrame, thread, eligibleFramePage, pageFaultEvent);
                            ThreadCB.dispatch();
                            return FAILURE;
                        }
                    }
                    else {
                        // Check if the frame is dirty
                        if (eligibleFrame.isDirty()) {
                            // Perform swap-out
                            doSwapOut(page, thread);
                            // Check if thread was killed during that time
                            if (thread.getStatus() == GlobalVariables.ThreadKill) {
                                swapOutCleanup(page, eligibleFrame, thread, eligibleFramePage, pageFaultEvent);
                                ThreadCB.dispatch();
                                return FAILURE;
                            }
                            eligibleFrame.setDirty(false);
                        }
                        // Free the frame
                        eligibleFrame.setReferenced(false);
                        eligibleFrame.setPage(null);
                        // Invalidate the eligible frame's page
                        eligibleFramePage.setValid(false);
                        eligibleFramePage.setFrame(null);
                        // Set the frame of the page that caused the pagefault before swap in
                        page.setFrame(eligibleFrame);
                    }
                    // Update page and frame table to indicate page has become valid
                    eligibleFrame.setPage(page);
                    page.setValid(true);
                    // Unreserve the frame if it is reserved
                    if (eligibleFrame.getReserved() == thread.getTask()) {
                        eligibleFrame.setUnreserved(thread.getTask());
                    }
                    // Notifty the threads waiting on the page
                    page.notifyThreads();
                    // Resume the thread that caused the pagefault
                    pageFaultEvent.notifyThreads();
                    // Set the validating thread to null
                    page.setValidatingThread(null);
                    // Dispatch and return SUCCESS
                    ThreadCB.dispatch();
                    return SUCCESS;
                }
                else {
                    return memoryStatus;
                }
            }
            // Suspend this thread if it's waiting for the page
            thread.suspend(page);
        }
        return FAILURE;
    }

    /*
       Feel free to add methods/fields to improve the readability of your code
    */

    /*
        Page Fault handler init for daemon
    */
    public static void init() {
        // Initialize a daemon for page replacement that sweeps eligible frames every 4000 ticks
        M2HCDaemon myDaemon = new M2HCDaemon();
        Daemon.create("M2HC Daemon", myDaemon, 4000);
    }

    /*
        Check if there is enough memory to free up
    */
    public static int checkMemory() {
        // Get all frames
        int frames = MMU.getFrameTableSize();
        // Set a counter to check how many frames are locked or reserved
        int framesLocked = -1;
        for (int i = 0; i < frames; i++) {
            FrameTableEntry frame = MMU.getFrame(i);
            // Increase framesLocked counter if this frame is reserved or locked
            if (frame.isReserved() || frame.getLockCount() > 0) {
                framesLocked++;
            }
        }
        // Return not enough memory if all frames are reserved or locked
        if (framesLocked == frames) {
            return NotEnoughMemory;
        }
        return SUCCESS;
    }

    /*
        Swap-in operation
    */
    public static void doSwapIn(PageTableEntry page, ThreadCB thread) {
        // Get the task that owns the page
        TaskCB pageTask = page.getTask();
        // Get the swap file of the task
        OpenFile swapFile = pageTask.getSwapFile();
        // Get the block number of the file by getting the ID of the page
        int blockNumber = page.getID();
        // Issue the read command
        swapFile.read(blockNumber, page, thread);
    }

    /*
        Swap-out operation
    */
    public static void doSwapOut(PageTableEntry page, ThreadCB thread) {
        // Get the task that owns the page
        TaskCB pageTask = page.getTask();
        // Get the swap file of the task
        OpenFile swapFile = pageTask.getSwapFile();
        // Get the block number of the file by getting the ID of the page
        int blockNumber = page.getID();
        // Issue the write command
        swapFile.write(blockNumber, page, thread);
    }

    /*
        Method invoked when swap-in or swap-out fails
    */
    public static void swapInCleanup(PageTableEntry page, FrameTableEntry eligibleFrame, ThreadCB thread, PageTableEntry eligibleFramePage, SystemEvent pageFaultEvent) {
        // Set the validating thread to null
        page.setValidatingThread(null);
        // Set the frame of the page to null
        page.setFrame(null);
        // Notifty the threads waiting on the page
        page.notifyThreads();
        // Resume the thread that caused the pagefault
        pageFaultEvent.notifyThreads();
        // Set frame page to null
        eligibleFrame.setPage(null);
    }

    public static void swapOutCleanup(PageTableEntry page, FrameTableEntry eligibleFrame, ThreadCB thread, PageTableEntry eligibleFramePage, SystemEvent pageFaultEvent) {
        // Set the validating thread to null
        page.setValidatingThread(null);
        // Notifty the threads waiting on the page
        page.notifyThreads();
        // Resume the thread that caused the pagefault
        pageFaultEvent.notifyThreads();
    }

}

/*
      Feel free to add local classes to improve the readability of your code
*/

class M2HCDaemon implements DaemonInterface {
    public void unleash(ThreadCB thread) {
        cleaner();
    }

    // First hand of M2HC page replacement algorithm
    public void cleaner() {
        // Get all frames
        int frames = MMU.getFrameTableSize();
        for (int i = 0; i < frames; i++) {
            FrameTableEntry frame = MMU.getFrame(i);
            // Check if the frame is not reserved and unlocked
            if (!frame.isReserved() && frame.getLockCount() == 0) {
                // If the frame is eligible, set the use bit to 0 ("false" flag)
                frame.setReferenced(false);
                // Add this frame to the suitableFrames array if it isn't already
                if (!MMU.suitableFrames.contains(frame)) {
                    MMU.suitableFrames.add(frame);
                }
            }
        }
    }

    // Second hand of M2HC page replacement algorithm
    public static FrameTableEntry chooser() {
        FrameTableEntry frame = null;
        // If there are free frames, choose the first one on the list
        if (MMU.suitableFrames.size() != 0) {
            frame = MMU.suitableFrames.get(0);
            MMU.suitableFrames.remove(frame);
            return frame;
        }
        // If there are no free frames on the list, choose any elegible frame
        int frames = MMU.getFrameTableSize();
        for (int i = 0; i < frames; i++) {
            frame = MMU.getFrame(i);
            // Check if the frame is not reserved and unlocked
            if (!frame.isReserved() && frame.getLockCount() == 0) {
                break;
            }
        }
        return frame;
    }
}