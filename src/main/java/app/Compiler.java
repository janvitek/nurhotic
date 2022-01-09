package app;

import java.util.ArrayList;
import java.util.List;

import app.Parser.Prog;

class Compiler {

    int compile(Prog p) {
        var ops = new ArrayList<Op>();
        for (var f : p.funs())
            compile(f.name(), f.args(), f.body(), ops);
        compile("main", new ArrayList<String>(), p.main(), ops);
        for (var op : ops)
            if (op instanceof Op.Call c)
                c.entryPc = findFun(c.funName, ops);
        Op.code = new Op[ops.size()];
        for (int i = 0; i < ops.size(); i++)
            Op.code[i] = ops.get(i);
        return findFun("main", ops);
    }

    int findFun(String name, List<Op> ops) {
        int i = 0;
        while (i < ops.size() && !(ops.get(i) instanceof Op.Entry e && e.funName.equals(name)))
            i++;
        return i;
    }

    void compile(String fname, List<String> params, List<Parser.Stmt> body, List<Op> ops) {
        var names = new ArrayList<String>();
        names.addAll(params);
        for (var b : body)
            addNames(b, names);
        ops.add(new Op.Entry(fname));
        addOps(body, ops, names);
        ops.add(new Op.Exit(fname));
    }

    void addOps(List<Parser.Stmt> b, List<Op> ops, List<String> names) {
        for (var s : b) {
            if (s instanceof Parser.Call o) {
                var vals = new ArrayList<Object>();
                for (var v : o.params)
                    vals.add(switch (v.kind()) {
                        case STR -> new AVal(v.asStr());
                        case NUM -> new AVal(v.asNum());
                        case ID -> names.indexOf(v.asId());
                        default -> null;
                    });
                ops.add(Op.mkCall(names.indexOf(o.t_var), o.f_name, vals));
            } else if (s instanceof Parser.If o) {
                var start = ops.size();
                ops.add(Op.Nop.it); // placeholder
                addOps(o.body, ops, names);
                ops.add(Op.Merge.it);
                var end = ops.size();
                ops.set(start, new Op.Branch(names.indexOf(o.guard.asId()), end)); // patch placeholder
            } else if (s instanceof Parser.While o) {
                var start = ops.size();
                ops.add(Op.Nop.it); // placeholder
                addOps(o.body, ops, names);
                ops.add(new Op.Jump(start));
                ops.add(Op.Merge.it);
                var end = ops.size();
                ops.set(start, new Op.Branch(names.indexOf(o.guard.asId()), end)); // patch placeholder
            } else
                throw new RuntimeException("Unreachable");
        }
    }

    private void addNames(Parser.Stmt s, List<String> names) {
        if (s instanceof Parser.Call o) {
            if (!names.contains(o.t_var))
                names.add(o.t_var);
        } else if (s instanceof Parser.If o)
            for (var b : o.body)
                addNames(b, names);
        else if (s instanceof Parser.While o)
            for (var b : o.body)
                addNames(b, names);
        else
            throw new RuntimeException("Unreachable");
    }

}