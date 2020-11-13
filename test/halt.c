/* halt.c
 *	Simple program to test whether running a user program works.
 *	
 *	Just do a "syscall" that shuts down the OS.
 *
 * 	NOTE: for some reason, user programs with global data structures 
 *	sometimes haven't worked in the Nachos environment.  So be careful
 *	out there!  One option is to allocate data structures as 
 * 	automatics within a procedure, but if you do this, you have to
 *	be careful to allocate a big enough stack to hold the automatics!
 */

#include "syscall.h"

int
main()

{   
    int i,j;
    printf("Hello World!!\n");
    printf("Star Printing...\n");
    for (i = 0; i < 5; i++)
    {
        for (j = 0; j <=i; j++)
        {
            printf("*");
        }
        printf("\n");
    }
    
    printf("Part 2 Task 1 Handle read and write complete !! :D\n");
    

    halt();
    return 0; 
    /* not reached */
}
