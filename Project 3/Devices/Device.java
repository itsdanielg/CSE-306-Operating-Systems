package osp.Devices;

/**
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
*/

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Tasks.*;
import java.util.*;

/**
 * ID: 111157499
 * Name: Daniel Garcia
 * Email: danieljedryl.garcia@stonybrook.edu
 * Project 3: Devices
 * Due Date: May 2, 2019
 * Pledge: I pledge my honor that all parts of this project were done by me
 * individually, without collaboration with anyone, and without consulting
 * external sources that help with similar projects.
 */

public class Device extends IflDevice {

    public static int currentHeadCylinder;
    public int openIndex;

    /**
        This constructor initializes a device with the provided parameters.
	As a first statement it must have the following:

	    super(id,numberOfBlocks);

	@param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
    */
    public Device(int id, int numberOfBlocks) {
        super(id, numberOfBlocks);
        iorbQueue = new GenericList();
        openIndex = 0;
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
    */
    public static void init() {
        currentHeadCylinder = 0;
    }

    /**
       Enqueues the IORB to the IORB queue for this device
       according to some kind of scheduling algorithm.
       
       This method must lock the page (which may trigger a page fault),
       check the device's state and call startIO() if the 
       device is idle, otherwise append the IORB to the IORB queue.

       @return SUCCESS or FAILURE.
       FAILURE is returned if the IORB wasn't enqueued 
       (for instance, locking the page fails or thread is killed).
       SUCCESS is returned if the IORB is fine and either the page was 
       valid and device started on the IORB immediately or the IORB
       was successfully enqueued (possibly after causing pagefault pagefault)
       
       @OSPProject Devices
    */
    public int do_enqueueIORB(IORB iorb) {

        // Lock the page associated with iorb
        PageTableEntry iorbPage = iorb.getPage();
        iorbPage.lock(iorb);

        // If thread is stil alive, increase iorb count of open-file handle associated with iorb
        if (iorb.getThread().getStatus() == GlobalVariables.ThreadKill) {
            return FAILURE;
        }
        OpenFile iorbOpenFile = iorb.getOpenFile();
        iorbOpenFile.incrementIORBCount();

        // If thread is stil alive, set the iorb cylinder to cylinder that contains the disk block
        if (iorb.getThread().getStatus() == GlobalVariables.ThreadKill) {
            return FAILURE;
        }
        Disk disk = (Disk)this;
        int blockSizeInBytes = (int) Math.pow(2, (MMU.getVirtualAddressBits() - MMU.getPageAddressBits()));
        int sectorsPerBlock = blockSizeInBytes/disk.getBytesPerSector();
        int blocksPerTrack = disk.getSectorsPerTrack()/sectorsPerBlock;
        int tracksPerCylinder = disk.getPlatters();
        int blocksPerCylinder = blocksPerTrack * tracksPerCylinder;
        int cylinder = iorb.getBlockNumber() / blocksPerCylinder;
        iorb.setCylinder(cylinder);

        // If thread is stil alive, check if the device is idle
        if (iorb.getThread().getStatus() == GlobalVariables.ThreadKill) {
            return FAILURE;
        }

        // Start I/O operation if this device is idle
        if (!this.isBusy()) {
            startIO(iorb);
        }
        // Else, put the request on the most recent device queue that's open
        else {
            GenericList deviceIorbQueue = (GenericList)iorbQueue;
            deviceIorbQueue.append(iorb);
        }
        return SUCCESS;

    }

    /**
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
    */
    public IORB do_dequeueIORB() {

        //  Check if most recent open queue is empty
        GenericList deviceIorbQueue = (GenericList)iorbQueue;
        for (int i = 0; i < deviceIorbQueue.length(); i++) {

            // Get the IORB with the shortest seek time from the open queue
            IORB shortestIORB = getIORB(deviceIorbQueue);

            // Remove this IORB from the queue and return the IORB
            deviceIorbQueue.remove(shortestIORB);
            return shortestIORB;
        }
        
        // If this is reached, this means the queue is empty, so return null
        return null;

    }

    /**
        Remove all IORBs that belong to the given ThreadCB from 
	this device's IORB queue

        The method is called when the thread dies and the I/O 
        operations it requested are no longer necessary. The memory 
        page used by the IORB must be unlocked and the IORB count for 
	the IORB's file must be decremented.

	@param thread thread whose I/O is being canceled

        @OSPProject Devices
    */
    public void do_cancelPendingIO(ThreadCB thread) {

        // Iterate through IORB in the queue
        GenericList deviceIorbQueue = (GenericList)iorbQueue;
        for (int i = 0; i < deviceIorbQueue.length(); i++) {
            IORB iorb = (IORB) deviceIorbQueue.getAt(i);
            ThreadCB iorbThread = iorb.getThread();
            // Check if this iorb is initiated by the given thread
            if (iorbThread == thread) {
                // Get the buffer page used by this IORB and unlock it
                PageTableEntry page = iorb.getPage();
                page.unlock();
                // Decrement the IORB count of the IORB's open-file handle
                OpenFile iorbOpenFile = iorb.getOpenFile();
                iorbOpenFile.decrementIORBCount();
                // Try to close the open-file handle; Check if the file has associated IORBs
                int openFileIORBs = iorbOpenFile.getIORBCount();
                if (openFileIORBs == 0) {
                    // If there are no associated IORBs, check if the closePending flag is true
                    boolean closePending = iorbOpenFile.closePending;
                    if (closePending) {
                        iorbOpenFile.close();
                    }
                }
                // Remove the iorb from the currentQueue
                deviceIorbQueue.remove(iorb);
            }
        }
        

    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atError() {
        // your code goes here
    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atWarning() {
        // your code goes here
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

    /*
       Static method to find the IORB with the shortest seek time
    */
    public static IORB getIORB(GenericList iorbQueue) {
        int queueLength = iorbQueue.length();
        IORB shortestIORB = (IORB) iorbQueue.getAt(queueLength - 1);
        for (int i = 0; i < queueLength; i++) {
            IORB currentIORB = (IORB) iorbQueue.getAt(i);
            int currentCylinder = currentIORB.getCylinder();
            int shortestCylinder = shortestIORB.getCylinder();
            int currentToHead = Math.abs(currentCylinder - currentHeadCylinder);
            int shortestToHead = Math.abs(shortestCylinder - currentHeadCylinder);
            if (currentToHead < shortestToHead) {
                shortestIORB = currentIORB;
            }
        }
        currentHeadCylinder = shortestIORB.getCylinder();
        return shortestIORB;
    }
    
}

/*
      Feel free to add local classes to improve the readability of your code
*/
