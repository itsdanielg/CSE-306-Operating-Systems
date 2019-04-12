package osp.Memory;
/**
    The PageTable class represents the page table for a given task.
    A PageTable consists of an array of PageTableEntry objects.  This
    page table is of the non-inverted type.

    @OSPProject Memory
*/
import java.lang.Math;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Hardware.*;

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

public class PageTable extends IflPageTable {
    /** 
	The page table constructor. Must call
	
	    super(ownerTask)

	as its first statement.

	@OSPProject Memory
    */
    public PageTable(TaskCB ownerTask) {
        super(ownerTask);
        // Get number of bits of page size and convert it to decimal
        int pageAddressBits = MMU.getPageAddressBits();
        int pageTableSize = bitsToDecimal(pageAddressBits);
        // Initialize pages to new array of page table entries
        super.pages = new PageTableEntry[pageTableSize];
        // Initialize each page with a page table entry
        for (int i = 0; i < pageTableSize; i++) {
            super.pages[i] = new PageTableEntry(this, i);
        }
    }

    /**
       Frees up main memory occupied by the task.
       Then unreserves the freed pages, if necessary.

       @OSPProject Memory
    */
    public void do_deallocateMemory() {
        // Get terminating task of this page table
        TaskCB currentTask = this.getTask();
        // Get number of frames
        int frameSize = MMU.getFrameTableSize();
        // Unset flags for each frame allocated to the task
        for (int i = 0; i < frameSize; i++) {
            FrameTableEntry currentFrame = MMU.getFrame(i);
            PageTableEntry framePage = currentFrame.getPage();
            // Check if the page that occupies this frame exists
            if (framePage != null) {
                // Only deallocate frames that are associated with the terminating task
                TaskCB frameTask = framePage.getTask();
                if (frameTask == currentTask) {
                    // Nullify page field that occupies frame
                    currentFrame.setPage(null);
                    // Clean the page
                    currentFrame.setDirty(false);
                    // Unset the reference bit
                    currentFrame.setReferenced(false);
                    // Find task that reserves this frame
                    TaskCB reservedTask = currentFrame.getReserved();
                    // If this reserved task is the terminating task, unreserve the frame
                    if (reservedTask == currentTask) {
                        currentFrame.setUnreserved(currentTask);
                    }
                }
            }
        }

    }
    
    // Helper method to convert number of bits to decimal
    public static int bitsToDecimal(int pageAddressBits) {
        int pageTableSize = 1;
        for (int i = 0; i < pageAddressBits; i++) {
            pageTableSize *= 2;
        }
        return pageTableSize;
    }

}

/*
      Feel free to add local classes to improve the readability of your code
*/
