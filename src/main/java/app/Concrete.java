package app;

import java.util.ArrayList;
import java.util.List;

import app.Abstract.AVal;
import app.Op.Exit;
import app.Parser.Prog;

class Concrete {

    Prog program;
    IState in;

    Concrete(Prog p) {
        program = p;
        in = new State(new Compiler().compile(program));
    }

    void execute() {
        IState st_in = null;
        IState st_out = in;
        while (!done(st_out.pc())) {
            st_in = st_out;
            var op = Op.get(st_in.pc());
            st_out = op.exec(st_in);
            App.p(App.pad(st_in.pc() + " : ", 6) + App.pad(op + " ", 14) + st_out);
        }
        in = st_out;
    }

    boolean done(int pc) {
        var op = Op.get(pc);
        return op instanceof Exit e && e.funName.equals("main");
    }
}

class State implements IState {
    List<Frame> stack = new ArrayList<>();
    AVal last;

    State(int pc) {
        stack.add(new Frame(pc, new ArrayList<AVal>()));
    }

    private State(State base) {
        for (var f : base.stack)
            stack.add(new Frame(f));
    }

    private Frame top() {
        return stack.get(stack.size() - 1);
    }

    // height of the stack
    public int height() {
        return stack.size();
    }

    public State pop(AVal returnVal) {
        var res = new State(this);
        res.last = last();
        res.stack.remove(stack.size() - 1);
        if (res.height() == 0)
            return res;
        if (Op.code[res.pc()] instanceof Op.Call c)
            return res.set(c.targetRegister, returnVal);
        throw new RuntimeException("Miscompilation");
    }

    public State push(int entryPc, List<AVal> args) {
        var res = new State(this);
        res.stack.add(new Frame(entryPc, args));
        return res;
    }

    public State set(int reg, AVal value) {
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
    public AVal last() {
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
    public AVal getRegister(int i) {
        return top().get(i);
    }
}

class Frame {
    int pc;
    List<AVal> regs = new ArrayList<AVal>();
    AVal lastValue;

    Frame(int pc, List<AVal> params) {
        this.pc = pc;
        for (var v : params)
            regs.add(v);
    }

    Frame(Frame f) {
        this(f.pc, f.regs);
        lastValue = f.lastValue;
    }

    AVal get(int reg) {
        return regs.get(reg);
    }

    void set(int reg, AVal value) {
        lastValue = value;
        while (reg >= regs.size())
            regs.add(null);
        regs.set(reg, value);
    }

    AVal last() {
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