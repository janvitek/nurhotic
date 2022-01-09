package app;

import java.util.ArrayList;
import java.util.List;

// The instruction set of our small bytecode language
class Op {

    static Op[] code; // All the instructions in the current program

    // Give a source of the form "tgt_reg = fname(vals_1,...)" either build a
    // call to a userdefined fun or to a builtin.
    static Call mkCall(int tgt_reg, String fname, List<Val> vals) {
        return Op.Builtin.isBuiltin(fname) ? new Op.Builtin(tgt_reg, fname, vals)
                : new Op.Call(tgt_reg, fname, vals);
    }

    static Op get(int pc) {
        return code[pc];
    }

    // the default exec function advance the pc
    State exec(State in) {
        return in.next(in.pc() + 1);
    }

    // Opcode for doing nothing
    static class Nop extends Op {
        static final Nop it = new Nop();

        public String toString() {
            return "nop";
        }
    }

    // Same as Nop, inserted by the compiler at control flow merges.
    static class Merge extends Op {
        static final Merge it = new Merge();

        public String toString() {
            return "merge";
        }
    }

    // Opcode for function calls and variable assignments
    static class Call extends Op {
        int targetRegister; // the register to write the result into
        String funName; // function's name
        List<Val> args; // argument lists
        int entryPc; // pc of the body

        Call(int tgt_reg, String fname, List<Val> args) {
            this.targetRegister = tgt_reg;
            this.funName = fname;
            this.args = args;
        }

        // Common behavior between userdef and builtin: marshall
        // the arguments and dereference variables
        State exec(State in) {
            var ps = new ArrayList<Val>();
            for (var o : args)
                ps.add(o instanceof Val.Id s ? in.getRegister(s.register) : o);
            return in.push(entryPc, ps);
        }

        boolean isbuiltin() {
            return false;
        }

        public String toString() {
            var str = "";
            for (var a : args)
                str += a + ",";
            return funName + "(" + str + ")";
        }
    }

    // Op code for function entry. This is a nop that deals with the merge of
    // inputs from callers. State at this pc is the merged state in static
    // analyses.
    static class Entry extends Op {
        String funName; // which function are we entering, handy for debugging

        Entry(String fun) {
            this.funName = fun;
        }

        public String toString() {
            return "enter_" + funName;
        }

    }

    // Opcode for function return. This is a nop that deals with returning
    // the value to the caller.
    static class Exit extends Op {
        String funName;

        Exit(String fname) {
            this.funName = fname;
        }

        // There are two cases to consider:
        // in.height() == 1: means we are returning from main and nothing should be done
        // otherwise: means we were called and have to return a value.
        State exec(State in) {
            if (in.height() == 1)
                return in;
            var last = in.last(); // return the last assignment made in this call
            var res = in.pop(last);
            return res.next(res.pc() + 1);
        }

        public String toString() {
            return "exit_" + funName;
        }
    }

    // Opcode for unconditional jumps; used to go to the top of a loop
    static class Jump extends Op {
        int targetPc;

        Jump(int pc) {
            targetPc = pc;
        }

        State exec(State in) {
            return in.next(targetPc);
        }

        public String toString() {
            return "jmp " + targetPc;
        }
    }

    // Opcode for a conditional branch
    static class Branch extends Op {
        int guardRegister; // which register holds the value to branch on
        int targetPc; // where to jump

        Branch(int guard, int falseBr) {
            this.guardRegister = guard;
            this.targetPc = falseBr;
        }

        // jump to targetPc if guardRegister is 0.
        State exec(State in) {
            var v = in.getRegister(guardRegister);
            var first = v.get(0);
            var next = first instanceof Val.Num n && n.value != 0 ? in.pc() + 1 : targetPc;
            return in.next(next);
        }

        public String toString() {
            return "if @" + guardRegister + " goto " + targetPc;
        }
    }

    static class Builtin extends Call {
        static String[] builtins = new String[] { "get", "c", "set", "add", "sub", "length" };

        static boolean isBuiltin(String funName) {
            var ok = false;
            for (var b : builtins)
                ok |= b.equals(funName);
            return ok;
        }

        Builtin(int tgt_reg, String fname, List<Val> args) {
            super(tgt_reg, fname, args);
        }

        boolean isbuiltin() {
            return true;
        }

        State exec(State in) {
            var ps = new ArrayList<Val>();
            for (var o : args)
                ps.add(o instanceof Val.Id s ? in.getRegister(s.register) : o);
            if (funName.equals("get")) {
                var vec = (Val.Vec) ps.get(0);
                var idx_vec = ps.get(1);
                var idx_val = idx_vec.get(0);
                if (idx_val instanceof Val.Num n) {
                    var res = vec.get(n.value);
                    return in.set(targetRegister, res).next(in.pc() + 1);
                }
            } else if (funName.equals("set")) {
                var vec = (Val.Vec) ps.get(0);
                var idx = ps.get(1);
                var val = ps.get(2);
                if (idx instanceof Val.Num n) {
                    var res = new Val.Vec(vec);
                    res.set(n.value, val.get(0));
                    return in.set(targetRegister, res).next(in.pc() + 1);
                }
            } else if (funName.equals("c")) {
                var v = new Val.Vec();
                for (var a : ps)
                    v = v.set(v.size(), a.get(0));
                return in.set(targetRegister, v).next(in.pc() + 1);
            } else if (funName.equals("add")) {
                var n1 = ps.get(0);
                var n2 = ps.get(1);
                return in.set(targetRegister, n1.add(n2)).next(in.pc() + 1);
            } else if (funName.equals("sub")) {
                var n1 = ps.get(0);
                var n2 = ps.get(1);
                return in.set(targetRegister, n1.sub(n2)).next(in.pc() + 1);
            } else if (funName.equals("length")) {
                var v1 = ps.get(0).asVec();
                var sz = new Val.Vec(new Val.Num(v1.size()));
                return in.set(targetRegister, sz).next(in.pc() + 1);
            }
            throw new RuntimeException("Missin builtin " + funName);
        }
    }
}