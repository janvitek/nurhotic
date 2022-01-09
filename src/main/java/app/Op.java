package app;

import java.util.ArrayList;
import java.util.List;

import app.Abstract.AVal;
import app.Abstract.AVal.BOOL;

// The instruction set of our small bytecode language
class Op {

    static Op[] code; // All the instructions in the current program

    // Give a source of the form "tgt_reg = fname(vals_1,...)" either build a
    // call to a userdefined fun or to a builtin.
    static Call mkCall(int tgt_reg, String fname, List<Object> vals) {
        return Op.Builtin.isBuiltin(fname) ? new Op.Builtin(tgt_reg, fname, vals)
                : new Op.Call(tgt_reg, fname, vals);
    }

    static Op get(int pc) {
        return code[pc];
    }

    // the default exec function advance the pc
    IState exec(IState in) {
        return in.next(new int[] { in.pc() + 1 });
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
        List<Object> args; // argument lists
        int entryPc; // pc of the body

        Call(int tgt_reg, String fname, List<Object> args) {
            this.targetRegister = tgt_reg;
            this.funName = fname;
            this.args = args;
        }

        // Common behavior between userdef and builtin: marshall
        // the arguments and dereference variables
        IState exec(IState in) {
            var ps = new ArrayList<AVal>();
            for (var o : args)
                ps.add(o instanceof Integer r ? in.getRegister(r) : o instanceof AVal v ? v : null);
            return in.push(entryPc, ps);
        }

        boolean isbuiltin() {
            return false;
        }

        public String toString() {
            var str = "";
            for (var a : args)
                str += (a instanceof Integer ? "@" + a : a) + ",";
            str = str.length() > 0 ? str.substring(0, str.length() - 1) : str;
            return funName + "(" + str + ")";
        }
    }

    // Op code for function entry. This is a nop that deals with the merge of
    // inputs from callers. State at this pc is the merged State in static
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
        IState exec(IState in) {
            var last = in.last(); // return the last assignment made in this call
            var res = in.pop(last);
            return res.next(new int[] { res.pc() + 1 });
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

        IState exec(IState in) {
            return in.next(new int[] { targetPc });
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
        IState exec(IState in) {
            var v = in.getRegister(guardRegister);
            var eq = v.eq(new AVal(0));
            var next = eq == BOOL.N ? new int[] { in.pc() + 1 }
                    : eq == BOOL.Y ? new int[] { targetPc } : new int[] { in.pc() + 1, targetPc };
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

        Builtin(int tgt_reg, String fname, List<Object> args) {
            super(tgt_reg, fname, args);
        }

        boolean isbuiltin() {
            return true;
        }

        IState exec(IState in) {
            var ps = new ArrayList<AVal>();
            for (var o : args)
                ps.add(o instanceof Integer r ? in.getRegister(r) : (AVal) o);
            AVal res = null;
            if (funName.equals("get")) {
                var vec = ps.get(0);
                var idx = ps.get(1);
                res = vec.getVal(idx);
            } else if (funName.equals("set")) {
                var vec = ps.get(0);
                var idx = ps.get(1);
                var val = ps.get(2);
                res = vec.set(idx, val);
            } else if (funName.equals("c")) {
                res = new AVal(ps);
            } else if (funName.equals("add")) {
                var n1 = ps.get(0);
                var n2 = ps.get(1);
                res = n1.add(n2);
            } else if (funName.equals("sub")) {
                var n1 = ps.get(0);
                var n2 = ps.get(1);
                res = n1.sub(n2);
            } else if (funName.equals("length")) {
                var v1 = ps.get(0);
                res = v1.size();
            } else
                throw new RuntimeException("Missin builtin " + funName);
            return in.set(targetRegister, res).next(new int[] { in.pc() + 1 });
        }
    }
}