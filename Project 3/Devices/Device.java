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

    /**
        This constructor initializes a device with the provided parameters.
	As a first statement it must have the following:

	    super(id,numberOfBlocks);

	@param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
    */
    public Device(int id, int numberOfBlocks) {
        super(id, numberOfBlocks);
        GenericList iorbQueue = new GenericList();
        iorbQueue.append(new ArrayList<IORB>());
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
    */
    public static void init() {
        
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
            ArrayList<IORB> openQueue = (ArrayList<IORB>)deviceIorbQueue.getTail();
            openQueue.add(iorb);
        }
        return SUCCESS;

    }

    /**
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
    */
    public IORB do_dequeueIORB() {
        // your code goes here
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
        // your code goes here
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

}

/*
      Feel free to add local classes to improve the readability of your code
*/
