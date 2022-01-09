package app;

import java.util.ArrayList;
import java.util.List;

import app.Parser.Prog;

class Concrete {

    Prog program;
    State in;

    Concrete(Prog p) {
        program = p;
        in = new State(new Compiler().compile(program));
    }

    void execute() {
        State st_in = null;
        State st_out = in;
        while (!st_out.equals(st_in)) {
            st_in = st_out;
            var op = Op.get(st_in.pc());
            st_out = op.exec(st_in);
            App.p(App.pad(op + " ", 14) + st_out);
        }
        in = st_out;
    }

}

class State {
    List<Frame> stack = new ArrayList<>();

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
    int height() {
        return stack.size();
    }

    State pop(Val returnVal) {
        var res = new State(this);
        res.stack.remove(stack.size() - 1);
        var pc = res.pc();
        var op = Op.code[pc];
        if (op instanceof Op.Call c)
            return res.set(c.targetRegister, returnVal);
        throw new RuntimeException("Miscompilation");
    }

    State push(int entryPc, List<Val> args) {
        var res = new State(this);
        res.stack.add(new Frame(entryPc, args));
        return res;
    }

    State set(int reg, Val value) {
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

    State next(int pc) {
        var res = new State(this);
        res.top().next(pc);
        return res;
    }

    public String toString() {
        return "State(" + top() + (height() > 1 ? " ... " + (height() - 1) : "") + ")";
    }

    // return the last value assigned in the top frame, or null
    Val last() {
        return top().last();
    }

    // return the pc in the top most frame
    int pc() {
        return top().pc();
    }

    // return value of register i in topmost frame
    Val getRegister(int i) {
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

class Val {
    Val get(int i) {
        return this;
    }

    enum Kind {
        STR, NUM, BOTTOM
    }

    Val add(Val o) {
        return op("add", o);
    }

    Val sub(Val o) {
        return op("sub", o);
    }

    private Val op(String op, Val o) {
        var v1 = this.asVec();
        var v2 = o.asVec();
        var sz = Math.max(v1.size(), v2.size());
        var res = new Vec();
        res.type = Kind.NUM;
        for (int i = 0; i < sz; i++) {
            var idx1 = i % v1.size();
            var idx2 = i % v2.size();
            var n1 = ((Num) v1.get(idx1)).value;
            var n2 = ((Num) v2.get(idx2)).value;
            var val = op.equals("sub") ? n1 - n2 : (op.equals("add") ? n1 + n2 : -1);
            res = res.set(i, new Num(val));
        }
        return res;
    }

    Val.Vec asVec() {
        if (this instanceof Num || this instanceof Str)
            return new Vec(this);
        else if (this instanceof Vec v)
            return v;
        else
            throw new RuntimeException("Internal error");
    }

    static class Vec extends Val {
        Kind type = Kind.BOTTOM;
        List<Val> values = new ArrayList<Val>();

        Vec() {
        }

        Vec(Val v) {
            type = v instanceof Str ? Kind.STR : Kind.NUM;
            values.add(v);
        }

        Vec(Vec v) {
            type = v.type;
            for (var x : v.values)
                values.add(x);
        }

        int size() {
            return values.size();
        }

        Val get(int i) {
            return values.get(i);
        }

        Vec set(int i, Val vo) {
            var res = new Vec(this);
            var v = vo instanceof Vec vec ? vec.get(0) : vo;
            if (res.type == Kind.BOTTOM)
                res.type = v instanceof Num ? Kind.NUM : v instanceof Str ? Kind.STR : Kind.BOTTOM;
            while (i >= res.size())
                res.values.add(null);
            if ((v instanceof Str && res.type == Kind.STR) ||
                    (v instanceof Num && res.type == Kind.NUM)) {
                res.values.set(i, v);
                return res;
            }
            throw new RuntimeException("type error");
        }

        public String toString() {
            if (size() == 1)
                return values.get(0).toString();
            var str = "";
            for (var v : values)
                str += v + ",";
            str = str.length() > 0 ? str.substring(0, str.length() - 1) : str;
            return "c(" + str + ")";
        }
    }

    static class Str extends Val {
        String value;

        Str(String v) {
            value = v;
        }

        public String toString() {
            return "\"" + value + "\"";
        }
    }

    static class Num extends Val {
        int value;

        Num(int v) {
            value = v;
        }

        public String toString() {
            return "" + value;
        }
    }

    static class Id extends Val {
        int register;

        Id(int r) {
            register = r;
        }

        public String toString() {
            return "@" + register;
        }
    }
}