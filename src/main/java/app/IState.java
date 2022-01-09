package app;

import java.util.List;

interface IState {

    // height of the stack
    int height();

    IState pop(AVal returnVal);

    IState push(int entryPc, List<AVal> args);

    IState set(int reg, AVal value);

    IState next(int[] pcs);

    // return the last value assigned in the top frame, or null
    AVal last();

    // return the pc in the top most frame
    int pc();

    // return value of register i in topmost frame
    AVal getRegister(int i);
}
