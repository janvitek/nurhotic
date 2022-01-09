package app;

import java.util.List;

interface IState {

    // height of the stack
    int height();

    IState pop(Val returnVal);

    IState push(int entryPc, List<Val> args);

    IState set(int reg, Val value);

    IState next(int[] pcs);

    // return the last value assigned in the top frame, or null
    Val last();

    // return the pc in the top most frame
    int pc();

    // return value of register i in topmost frame
    Val getRegister(int i);
}
