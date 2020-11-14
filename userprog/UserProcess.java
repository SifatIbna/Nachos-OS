package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import javax.management.Descriptor;
import java.io.EOFException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    protected OpenFile stdout;
    protected OpenFile stdin;
    protected OpenFile[] descriptors;
    private final int descriptorSize = 8;
    protected UserProcess parent;
    protected List<UserProcess> children;
    protected final Hashtable<Integer, Integer> childrenExitStatus;
    protected static Lock statusLock = new Lock();
    protected static Lock counterLock = new Lock();
    protected int pID;
    protected static AtomicInteger counter = new AtomicInteger();
    protected UThread thread;


    /**
     * Allocate a new process.
     */
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
        for (int i = 0; i < numPhysPages; i++)
            pageTable[i] = new TranslationEntry(i, 0, false, false, false, false);

        descriptors = new OpenFile[descriptorSize];
        boolean intrpt = Machine.interrupt().disable();
        stdin = UserKernel.console.openForReading();
        stdout = UserKernel.console.openForWriting();
        descriptors[0] = stdin;
        descriptors[1] = stdout;
        Machine.interrupt().restore(intrpt);
        statusLock.acquire();
        childrenExitStatus = new Hashtable<Integer, Integer>();
        statusLock.release();

        children = new ArrayList<UserProcess>();

        counterLock.acquire();
        pID = counter.incrementAndGet();
        counterLock.release();

    }


    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        new UThread(this).setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */

    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
//        if (vaddr < 0 || vaddr >= memory.length)
//            return 0;

        int amount = Math.min(length, memory.length - vaddr);

        int i1 = 0;
        int endVAddr = vaddr + length - 1;
        int stVPage = Processor.pageFromAddress(vaddr);
        int endVPage = Processor.pageFromAddress(endVAddr);

        for (int i = vaddr; i <= endVAddr ; i+=amount) {
            stVPage = Processor.pageFromAddress(i);

            if(stVPage<0 || stVPage >= pageTable.length || pageTable[stVPage] == null || !pageTable[stVPage].valid){
                return i1;
            }

            endVPage = Processor.makeAddress(stVPage,Processor.pageSize -1);

            if(endVPage > endVAddr) endVPage = endVAddr;

            amount = endVPage - i + 1;

            int ppn = pageTable[stVPage].ppn;

            int stPAddr = Processor.makeAddress(ppn,Processor.offsetFromAddress(i));
            System.arraycopy(memory,stPAddr,data,offset,amount);
            offset = amount + offset;
            i1 = amount + i1;

        }

        return i1;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
//        if (vaddr < 0 || vaddr >= memory.length)
//            return 0;

        int amount = Math.min(length, memory.length - vaddr);

        int i1 = 0;
        int endVAddr = vaddr + length - 1;
        int stVPage = Processor.pageFromAddress(vaddr);
        int endVPage = Processor.pageFromAddress(endVAddr);

        for (int i = vaddr; i <= endVAddr ; i+=amount) {
            stVPage = Processor.pageFromAddress(i);

            if(stVPage<0 || stVPage >= pageTable.length || pageTable[stVPage] == null || !pageTable[stVPage].valid){
                return i1;
            }

            endVPage = Processor.makeAddress(stVPage,Processor.pageSize -1);

            if(endVPage > endVAddr) endVPage = endVAddr;

            amount = endVPage - i + 1;

            int ppn = pageTable[stVPage].ppn;

            int stPAddr = Processor.makeAddress(ppn,Processor.offsetFromAddress(i));
            System.arraycopy(data,offset,memory,stPAddr,amount);
            offset = amount + offset;
            i1 = amount + i1;

        }
        return i1;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
//            numPages += section.getLength();
            if (!allocate(numPages, section.getLength(), section.isReadOnly())) {
                releaseResource();
                return false;
            }
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
//        numPages += stackPages;
        if (!allocate(numPages, stackPages, false)) {
            releaseResource();
            return false;
        }
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        if (!allocate(numPages, 1, false)) {
            releaseResource();
            return false;
        }

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;

                // for now, just assume virtual addresses=physical addresses
                TranslationEntry te = null;
                if (vpn >= 0 && vpn < pageTable.length)
                    te = pageTable[vpn];
                if (te == null)
                    return false;
//				if(section.isReadOnly()) te.readOnly = true;
                section.loadPage(i, te.ppn);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        releaseResource();
        for (OpenFile opf:
                descriptors) {
            if(opf!=null) opf.close();
        }
        for(int i=0;i<descriptorSize;i++){
            descriptors[i] = null;
        }
        coff.close();
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {

        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }


    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(cha r *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallExit:
                return handleExit(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);


            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    private int handleExec(int vAddr, int numofPages, int startVAddr) {
        if(vAddr<0||numofPages<0||startVAddr<0){
            Lib.debug(dbgProcess, "Error in function handleExec - address out of range");
            return -1;
        }
        String fileName=readVirtualMemoryString(vAddr, 512);
        if(fileName==null){
            Lib.debug(dbgProcess, "Error in function handleExec - No filename given");
            return -1;
        }
        if(!fileName.contains(".coff")){
            String [] ext = fileName.split(".");
            Lib.debug(dbgProcess, "Error in function handleExec - unknown file type : "+ext[ext.length-1]);
            return -1;
        }
        String[] pages=new String[numofPages];
        byte[] buffer;
        int readLength;
        for(int i=0;i<numofPages;i++){
            buffer=new byte[4];
            readLength=readVirtualMemory((4*i)+startVAddr,buffer);
            if(readLength!=4){
                Lib.debug(dbgProcess, "Error in function handleExec - Reading Coff file failed");
                return -1;
            }
            int intAddress=Lib.bytesToInt(buffer, 0);
            String arg=readVirtualMemoryString(intAddress,256);
            if(arg == null){
                Lib.debug(dbgProcess, "Error in function handleExec - Reading .Coff file failed");
                return -1;
            }
            pages[i]=arg;
        }
        UserProcess child=UserProcess.newUserProcess();
        boolean isSuccessful=child.execute(fileName, pages);
        if(!isSuccessful){
            Lib.debug(dbgProcess, "handleExec:Execute child process failed");
            return -1;
        }
        child.parent=this;
        this.children.add(child);
        return child.pID;
    }

    private int handleJoin(int callerProcessID, int vAddr) {
        if (callerProcessID < 0 || vAddr < 0) {
            return -1;
        }
        UserProcess child = null;
        for (UserProcess process :
                children) {
            if (callerProcessID == process.pID) {
                child = process;
                break;
            }
        }

        if (child == null) {
            Lib.debug(dbgProcess, "Error in function handleJoin - caller is not the child of current thread");
            return -1;
        }
        child.thread.join();

        child.parent = null;
        children.remove(child);
        statusLock.acquire();
        Integer status = childrenExitStatus.get(child.pID);
        statusLock.release();
        if (status == null) {
            Lib.debug(dbgProcess, "Error in function handleJoin - child thread termination unknown");
            return 0;
        } else {
            //status int 32bits
            byte[] buffer = new byte[4];
            buffer = Lib.bytesFromInt(status);
            int count = writeVirtualMemory(vAddr, buffer);
            if (count != 0x4) {
                Lib.debug(dbgProcess, "Error in function handleJoin - child thread termination status writing error");
                return 0;
            } else {
                Lib.debug(dbgProcess, "Process with ID - "+child.pID+" Joined");
                return 1;
            }
        }
    }

    private int handleExit(int a0) {
        if (parent != null) {
            statusLock.acquire();
            parent.childrenExitStatus.put(pID, a0);
            statusLock.release();
        }
        unloadSections();
        for (UserProcess p :
                children) {
            p.parent = null;
        }
        children.clear();
        Lib.debug(dbgProcess,"Exited process : " + pID + " with Status : " + a0);

        if (pID == 0) {
            Kernel.kernel.terminate();
            return 0;
        }

        UThread.finish();

        return 0;
    }

    private int handleWrite(int descriptorIndex, int vaddr, int size) {
        if (size < 0) {
            Lib.debug(dbgProcess, "Error in function handleWrite() - Size is negative");
            return -1;
        } else if (descriptorIndex < 0 || descriptorIndex > descriptorSize - 1) {
            Lib.debug(dbgProcess, "Error in function handleWrite() - Descriptor value > descriptor array length");
            return -1;
        }
        OpenFile writeFile;
        if (descriptors[descriptorIndex] == null) {
            Lib.debug(dbgProcess, "Error in function handleWrite() - File doesn't exist in the descriptor table");
            return -1;
        }

        writeFile = descriptors[descriptorIndex];

        int length = 0;
        byte[] writer = new byte[size];
        length = readVirtualMemory(vaddr, writer, 0, size);
        int count = 0;
        count = writeFile.write(writer, 0, length);
        if (count == -1) {
            Lib.debug(dbgProcess, "Error in function handleWrite() - Unknown Error occurred while writing file");
            return -1;
        }
        return count;
    }

    private int handleRead(int descriptorIndex, int vaddr, int size) {
        if (size < 0) {
            Lib.debug(dbgProcess, "Error in function handleRead - Size is negative");
            return -1;
        } else if (descriptorIndex < 0 || descriptorIndex > descriptorSize - 1) {
            Lib.debug(dbgProcess, "Error in function handleRead - Descriptor value > descriptor array length");
            return -1;
        }
        OpenFile readFile;
        if (descriptors[descriptorIndex] == null) {
            Lib.debug(dbgProcess, "Error in function handleRead - File doesn't exist in the descriptor table");
            return -1;
        }

        readFile = descriptors[descriptorIndex];

        int length = 0;
        byte[] reader = new byte[size];
        length = readFile.read(reader, 0, size);
        if (length == -1) {
            Lib.debug(dbgProcess, "Error in function handleRead - Unknown Error occurred while reading file");
            return -1;
        }
        int count = 0;
        count = writeVirtualMemory(vaddr, reader, 0, length);
        return count;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    private boolean allocate(int vpn, int desiredPages, boolean readOnly) {
        LinkedList<TranslationEntry> allocated = new LinkedList<TranslationEntry>();

        for (int i = 0; i < desiredPages; ++i) {
            if (vpn >= pageTable.length)
                return false;

            int ppn = UserKernel.newPage();
            if (ppn == -1) {
                Lib.debug(dbgProcess, "\tcannot allocate new page");

                for (TranslationEntry te: allocated) {
                    pageTable[te.vpn] = new TranslationEntry(te.vpn, 0, false, false, false, false);
                    UserKernel.deletePage(te.ppn);
                    numPages-=1;
                }

                return false;
            } else {
                TranslationEntry a = new TranslationEntry(vpn + i,
                        ppn, true, readOnly, false,false);
                allocated.add(a);
                pageTable[vpn + i] = a;
                ++numPages;
            }
        }
        return true;
    }

    private void releaseResource() {
        for (int i = 0; i < pageTable.length; ++i)
            if (pageTable[i].valid) {
                UserKernel.deletePage(pageTable[i].ppn);
                pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false, false, false, false);
            }
        numPages = 0;
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

}
