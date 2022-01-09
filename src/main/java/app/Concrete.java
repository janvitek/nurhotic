package app;

import java.util.ArrayList;
import java.util.List;

import app.Parser.Prog;

class Concrete {

    Prog program;
    protected IState in;
    protected int mainEntryPC;
    protected int mainExitPC;

    Concrete(Prog p) {
        program = p;
        mainEntryPC = new Compiler().compile(program);
        mainExitPC = Op.exitPC();
        in = new State(mainEntryPC);
    }

    IState execute(Dynamic obs) {
        int pc;
        while ((pc = in.pc()) != mainExitPC) {
            var op = Op.get(pc);
            in = op.exec(in);
            App.p(App.pad(pc + " : ", 6) + App.pad(op + " ", 14) + in);
            if (obs != null)
                obs.observe(pc, in.pc());
        }
        return in;
    }
}

class State implements IState {
    private List<Frame> stack = new ArrayList<>();
    private Val last;

    State(int pc) {
        stack.add(new Frame(pc, new ArrayList<Val>()));
    }

    private State(State base) {
        for (var f : base.stack)
            stack.add(new Frame(f));
    }

    private Frame top() {
        return stack.get(stack.size() - 1);
    }

    // height of the stack
    private int height() {
        return stack.size();
    }

    public State pop(Val returnVal) {
        var res = new State(this);
        res.last = returnVal;
        res.stack.remove(stack.size() - 1);
        if (res.height() > 0) {
            res = res.set(((Op.Call) Op.get(res.pc())).targetRegister, returnVal);
            res.top().next(res.pc() + 1);
        }
        return res;
    }

    public State push(int entryPc, List<Val> args) {
        var res = new State(this);
        res.stack.add(new Frame(entryPc, args));
        return res;
    }

    public State set(int reg, Val value) {
        if (!value.isConcrete())
            throw new RuntimeException("exec error got abstract value: " + value);
        var res = new State(this);
        res.top().set(reg, value);
        return res;
    }

    public boolean equals(Object o) {
        if (o instanceof State st)
            return stack.equals(st.stack);
        else
            return false;
    }

    public State next(int[] pcs) {
        if (pcs.length != 1)
            throw new RuntimeException("concrete execution requires a single target");
        var pc = pcs[0];
        var res = new State(this);
        res.top().next(pc);
        return res;
    }

    public String toString() {
        if (height() == 0)
            return "State()";
        else
            return "State(" + top() + (height() > 1 ? " ... " + (height() - 1) : "") + ")";
    }

    // return the last value assigned in the top frame, or null
    public Val last() {
        if (height() == 0)
            return last;
        else
            return top().last();
    }

    // return the pc in the top most frame
    public int pc() {
        return height() == 0 ? -1 : top().pc();
    }

    // return value of register i in topmost frame
    public Val getRegister(int i) {
        return top().get(i);
    }
}

class Frame {
    int pc;
    List<Val> regs = new ArrayList<Val>();
    Val lastValue;

    Frame(int pc, List<Val> params) {
        this.pc = pc;
        for (var v : params)
            regs.add(v);
    }

    Frame(Frame f) {
        this(f.pc, f.regs);
        lastValue = f.lastValue;
    }

    Val get(int reg) {
        return regs.get(reg);
    }

    void set(int reg, Val value) {
        lastValue = value;
        while (reg >= regs.size())
            regs.add(null);
        regs.set(reg, value);
    }

    Val last() {
        return lastValue;
    }

    void next(int pc) {
        this.pc = pc;
    }

    int pc() {
        return pc;
    }

    public String toString() {
        var s = "";
        for (int i = 0; i < regs.size(); i++)
            s += i + "=" + regs.get(i) + ",";
        s = s.length() > 0 ? s.substring(0, s.length() - 1) : s;
        return "[" + s + "]";
    }
}