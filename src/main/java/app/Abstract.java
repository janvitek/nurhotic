package app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.Op.Call;
import app.Op.Exit;
import app.Parser.Prog;

class Abstract extends Concrete {
    State[] astates;
    State init;
    boolean done = false;
    Set<Integer> pcs = new HashSet<Integer>();
    Set<Integer> seens = new HashSet<Integer>();

    Abstract(Prog p) {
        super(p);
        astates = new State[Op.code.length];
        for (int i = 0; i < astates.length; i++)
            astates[i] = new State(i);
        init = new State(14);
    }

    void seen(int pc) {
        seens.add(pc);
    }

    void toSee(int pc) {
        if (pc >= Op.code.length)
            return; // trying to add succ of last statmeent.
        if (!seens.contains(pc)) {
            pcs.add(pc);
            seens.add(pc);
        }
    }

    void reset() {
        seens = new HashSet<Integer>();
    }

    int nextToSee() {
        for (var i : pcs) {
            pcs.remove(i);
            return i;
        }
        return Op.code.length - 1;// last insturction
    }

    void analyze() {
        while (!done) {
            var prev = astates.clone();
            in = init;
            execute();
            done = equals(astates, prev);
            reset();
        }
    }

    boolean equals(State[] l, State[] r) {
        if (l.length != r.length)
            return false;
        for (int i = 0; i < l.length; i++)
            if (!l[i].equals(r[i]))
                return false;
        return true;
    }

    void mergeState(int pc, State st) {
        if (pc < astates.length) // there is a case where we try to merge the last statement
            astates[pc] = astates[pc].merge(st);
    }

    State builtin(Call c, List<Val> ps, State in) {
        var funName = c.funName;
        var targetRegister = c.targetRegister;
        if (funName.equals("get")) {
            var vec = ps.get(0);
            var idx = ps.get(1);
            var res = vec.getVal(idx);
            return in.set(targetRegister, res);
        } else if (funName.equals("set")) {
            var vec = ps.get(0);
            var idx = ps.get(1);
            var val = ps.get(2);
            var res = vec.set(idx, val);
            return in.set(targetRegister, res);
        } else if (funName.equals("c")) {
            var v = new Val(ps);
            return in.set(targetRegister, v);
        } else if (funName.equals("add")) {
            var n1 = ps.get(0);
            var n2 = ps.get(1);
            return in.set(targetRegister, n1.add(n2));
        } else if (funName.equals("sub")) {
            var n1 = ps.get(0);
            var n2 = ps.get(1);
            return in.set(targetRegister, n1.sub(n2));
        } else if (funName.equals("length")) {
            var v1 = ps.get(0);
            var len = v1.size();
            return in.set(targetRegister, len);
        } else
            throw new RuntimeException("Missin builtin " + funName);

    }

    class State implements IState {
        List<Val> values = new ArrayList<Val>();
        Val last = Val.bot;
        int pc = -1;

        State(int pc) {
            this.pc = pc;
        }

        State(State st) {
            for (var v : st.values)
                values.add(v);
            last = st.last;
            pc = st.pc;
        }

        public int pc() {
            return pc;
        }

        public int height() {
            throw new RuntimeException("height of an abstract stack unknown");
        }

        public State next(int[] pcs_) {
            for (var pc : pcs_) {
                mergeState(pc, this);
                toSee(pc);
            }
            return astates[nextToSee()];
        }

        public State pop(Val returnVal) {
            var nm = Op.code[pc()] instanceof Exit e ? e.funName : null;
            for (int i = 0; i < astates.length; i++)
                if (Op.get(i) instanceof Op.Call c && c.funName.equals(nm)) {
                    mergeState(i, new State(i).set(c.targetRegister, last));
                    toSee(i);
                }
            return astates[nextToSee()];
        }

        public State push(int entryPC, List<Val> args) {
            mergeState(entryPC, this);
            toSee(entryPC);
            return astates[nextToSee()];
        }

        public State merge(IState state) {
            var st = (State) state;
            var res = new State(pc);
            for (int i = 0; i < Math.max(values.size(), st.values.size()); i++)
                if (i >= values.size())
                    res.values.add(st.values.get(i));
                else if (i >= st.values.size())
                    res.values.add(values.get(i));
                else
                    res.values.add(Val.merge(values.get(i), st.values.get(i)));
            return res;
        }

        // Hmm... this operation modifies the current state without copying...
        // perhaps ok. but not pretty. What would be an alternative?
        public Val getRegister(int i) {
            while (i >= values.size())
                values.add(Val.bot);
            return values.get(i);
        }

        public State set(int i, Val v) {
            last = v;
            var res = clone();
            while (i >= res.values.size())
                res.values.add(Val.bot);
            if (v == null)
                throw new RuntimeException("values can't be null");
            res.values.set(i, v);
            return res;
        }

        public Val last() {
            return last;
        }

        public State clone() {
            return new State(this);
        }

        public boolean equals(Object o) {
            if (o instanceof State other) {
                if (values.size() != other.values.size())
                    return false;
                for (int i = 0; i < values.size(); i++)
                    if (!values.get(i).equals(other.values.get(i)))
                        return false;
                return true;
            }
            return false;
        }

        public String toString() {
            var res = "";
            for (int i = 0; i < values.size(); i++)
                res += i + "=" + values.get(i) + ",";
            if (res.length() > 0)
                res = res.substring(0, res.length() - 1);
            return "State(" + res + ")";
        }
    }

}
