package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;

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

/**
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/
public class DiskInterruptHandler extends IflDiskInterruptHandler {
    /** 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
    public void do_handleInterrupt() {

        // Obtain information from the interrupt vector
        IORB interruptIORB = (IORB) InterruptVector.getEvent();

        // Decrement IORB count of open-file handle
        OpenFile iorbOpenFile = interruptIORB.getOpenFile();
        iorbOpenFile.decrementIORBCount();

        // Try to close the file
        int openFileIORBs = iorbOpenFile.getIORBCount();
        if (openFileIORBs == 0) {
            // If there are no associated IORBs, check if the closePending flag is true
            boolean closePending = iorbOpenFile.closePending;
            if (closePending) {
                iorbOpenFile.close();
            }
        }

        // Unlock page associated with the IORB and get its frame
        PageTableEntry iorbPage = interruptIORB.getPage();
        iorbPage.unlock();
        FrameTableEntry pageFrame = iorbPage.getFrame();

        // Check if the task associated with the IORB thread is still alive
        ThreadCB iorbThread = interruptIORB.getThread();
        TaskCB iorbTask = iorbThread.getTask();
        int taskStatus = iorbTask.getStatus();
        if (taskStatus == TaskLive) {
            // Check if the thread is still alive
            if (iorbThread.getStatus() != ThreadKill) {
                // Check if the I/O operation is not a page swap-in or swap-out
                int iorbID = interruptIORB.getDeviceID();
                if (iorbID != SwapDeviceID) {
                    pageFrame.setReferenced(true);
                    // Check if this was also a read operation
                    if (interruptIORB.getIOType() == FileRead) {
                        pageFrame.setDirty(true);
                    }
                }
                // Else, mark the frame as clean
                else {
                    pageFrame.setDirty(false);
                }
            }
        }
        // Else, if it's dead, unreserve if the frame was reserved by the task
        else {
            if (pageFrame.getReserved() == iorbTask) {
                pageFrame.setUnreserved(iorbTask);
            }
        }

        // Wake up threads waiting on the IORBs
        interruptIORB.notifyThreads();

        // Set device to idle
        int iorbID = interruptIORB.getDeviceID();
        Device iorbDevice = Device.get(iorbID);
        iorbDevice.setBusy(false);

        // Service a new request on the I/O device and append a new queue to close current queue
        IORB shortestIORB = iorbDevice.dequeueIORB();
        if (shortestIORB != null) {
            iorbDevice.startIO(shortestIORB);
        }

        // Dispatch a new thread
        ThreadCB.dispatch();

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
