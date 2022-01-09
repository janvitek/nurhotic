package app;

import java.util.List;

// Computational state, assuming that values are immutable and there is no heap
interface IState {

    // pop the topmost frame and return val
    IState pop(Val val);

    // push a frame for a function starting at entryPc and with arguments args
    IState push(int entryPc, List<Val> args);

    // set register reg to value
    IState set(int reg, Val value);

    // set the next pc to pcs; in concrete execs there will be only one
    IState next(int[] pcs);

    // return the last value assigned in the top frame, or null
    Val last();

    // return the pc in the top most frame
    int pc();

    // return value of register i in topmost frame
    Val getRegister(int i);
}
