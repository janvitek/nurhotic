package app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.Op.Exit;
import app.Parser.Prog;

class Abstract extends Concrete {
    private State[] astates;
    private State init;
    private boolean done = false;
    private Set<Integer> pcs = new HashSet<Integer>();
    private Set<Integer> seens = new HashSet<Integer>();

    Abstract(Prog p) {
        super(p);
        astates = new State[Op.length()];
        for (int i = 0; i < astates.length; i++)
            astates[i] = new State(i);
        init = new State(mainEntryPC);
    }

    // add pc to the program counters that have to be processed
    private void toSee(int pc) {
        if (!seens.contains(pc)) {
            pcs.add(pc);
            seens.add(pc);
        }
    }

    // returns the next pc or the exit of main
    private int nextToSee() {
        for (var i : pcs) {
            pcs.remove(i);
            return i;
        }
        return mainExitPC;// last nop before the instruction
    }

    // analyze the program and return the last state
    IState analyze() {
        while (!done) {
            var prev = astates.clone();
            in = init;
            execute();
            done = equals(astates, prev);
            seens = new HashSet<Integer>(); // reset the seen
        }
        return astates[astates.length - 1];
    }

    boolean equals(State[] l, State[] r) {
        if (l.length != r.length)
            return false;
        for (int i = 0; i < l.length; i++)
            if (!l[i].equals(r[i]))
                return false;
        return true;
    }

    private void mergeState(int pc, State st) {
        astates[pc] = astates[pc].merge(st);
        toSee(pc);
    }

    // Abstract State - keeps the topmost frame
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

        public State next(int[] pcs_) {
            for (var pc : pcs_)
                mergeState(pc, this);
            return astates[nextToSee()];
        }

        public State pop(Val returnVal) {
            var nm = Op.get(pc()) instanceof Exit e ? e.funName : null;
            last = returnVal;
            for (int i = 0; i < astates.length; i++)
                if (Op.get(i) instanceof Op.Call c && c.funName.equals(nm))
                    mergeState(i + 1, new State(i + 1).set(c.targetRegister, last));
            return astates[nextToSee()];
        }

        public State push(int entryPC, List<Val> args) {
            mergeState(entryPC, this);
            return astates[nextToSee()];
        }

        // merges two States keeping the pc of the receiver
        State merge(IState state) {
            var st = (State) state;
            var res = new State(pc);
            res.last = Val.merge(last, st.last);
            for (int i = 0; i < Math.max(values.size(), st.values.size()); i++)
                res.values.add(i >= values.size() ? st.values.get(i)
                        : i >= st.values.size() ? values.get(i) : Val.merge(values.get(i), st.values.get(i)));
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
            var res = new State(this);
            while (i >= res.values.size())
                res.values.add(Val.bot);
            res.values.set(i, v);
            return res;
        }

        public Val last() {
            return last;
        }

        public boolean equals(Object o) {
            if (o instanceof State other && values.size() == other.values.size()) {
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
