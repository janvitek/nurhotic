package app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.Abstract.AVal.BOOL;
import app.Op.Call;
import app.Op.Exit;
import app.Parser.Prog;

class Abstract extends Concrete {
    AState[] astates;
    AState init;
    boolean done = false;
    Set<Integer> pcs = new HashSet<Integer>();
    Set<Integer> seens = new HashSet<Integer>();

    Abstract(Prog p) {
        super(p);
        astates = new AState[Op.code.length];
        for (int i = 0; i < astates.length; i++)
            astates[i] = new AState(i);
        init = new AState(14);
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

    boolean equals(AState[] l, AState[] r) {
        if (l.length != r.length)
            return false;
        for (int i = 0; i < l.length; i++)
            if (!l[i].equals(r[i]))
                return false;
        return true;
    }

    void mergeState(int pc, AState st) {
        if (pc < astates.length) // there is a case where we try to merge the last statement
            astates[pc] = astates[pc].merge(st);
    }

    AState builtin(Call c, List<AVal> ps, AState in) {
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
            var v = new AVal(ps);
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

    class AState implements IState {
        List<AVal> values = new ArrayList<AVal>();
        AVal last = AVal.bot;
        int pc = -1;

        AState(int pc) {
            this.pc = pc;
        }

        AState(AState st) {
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

        public AState next(int[] pcs_) {
            for (var pc : pcs_) {
                mergeState(pc, this);
                toSee(pc);
            }
            return astates[nextToSee()];
        }

        public AState pop(AVal returnVal) {
            var nm = Op.code[pc()] instanceof Exit e ? e.funName : null;
            for (int i = 0; i < astates.length; i++)
                if (Op.get(i) instanceof Op.Call c && c.funName.equals(nm)) {
                    mergeState(i, new AState(i).set(c.targetRegister, last));
                    toSee(i);
                }
            return astates[nextToSee()];
        }

        public AState push(int entryPC, List<AVal> args) {
            mergeState(entryPC, this);
            toSee(entryPC);
            return astates[nextToSee()];
        }

        public AState merge(IState state) {
            var st = (AState) state;
            var res = new AState(pc);
            for (int i = 0; i < Math.max(values.size(), st.values.size()); i++)
                if (i >= values.size())
                    res.values.add(st.values.get(i));
                else if (i >= st.values.size())
                    res.values.add(values.get(i));
                else
                    res.values.add(AVal.merge(values.get(i), st.values.get(i)));
            return res;
        }

        // Hmm... this operation modifies the current state without copying...
        // perhaps ok. but not pretty. What would be an alternative?
        public AVal getRegister(int i) {
            while (i >= values.size())
                values.add(AVal.bot);
            return values.get(i);
        }

        public AState set(int i, AVal v) {
            last = v;
            var res = clone();
            while (i >= res.values.size())
                res.values.add(AVal.bot);
            if (v == null)
                throw new RuntimeException("values can't be null");
            res.values.set(i, v);
            return res;
        }

        public AVal last() {
            return last;
        }

        public AState clone() {
            return new AState(this);
        }

        public boolean equals(Object o) {
            if (o instanceof AState other) {
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

    static class AVal {
        enum BOOL { // Three value logic
            Y, N, M // Yes, No, Maybe
        };

        private static AVal bot = new AVal();
        private static AVal top = new AVal(Range.top, Type.top);
        private static AVal anyInt = new AVal(Range.mkScalar(), Type.mkInt());

        private Range r = Range.mk();
        private Type t = Type.mk();
        private Map<Integer, AVal> values = new HashMap<Integer, AVal>();
        private Integer ifScalarNum;
        private String ifScalarString;

        AVal(int v) {
            r = Range.mkScalar();
            t = Type.mkInt();
            ifScalarNum = v;
        }

        AVal(String s) {
            r = Range.mkScalar();
            t = Type.mkStr();
            ifScalarString = s;
        }

        AVal(List<AVal> vals) {
            if (vals.size() == 0)
                throw new RuntimeException("arrays can't be zero length");
            var v = vals.get(0);
            t = v.t;
            r = new Range(0, vals.size());
            if (isScalar() == BOOL.Y) {
                ifScalarNum = v.ifScalarNum;
            } else
                for (int i = 0; i < vals.size(); i++)
                    values.put(i, vals.get(i));
        }

        private AVal() {
        }

        private AVal(Range r, Type t) {
            this.r = r;
            this.t = t;
        }

        static AVal merge(AVal l, AVal r) {
            if (l.isBot())
                return r;
            if (r.isBot())
                return l;
            if (l.isTop() || r.isTop())
                return top;
            var ty = Type.merge(l.t, r.t);
            var ra = Range.merge(l.r, r.r);
            var si = l.ifScalarNum != null && r.ifScalarNum != null && l.ifScalarNum.equals(r.ifScalarNum)
                    ? l.ifScalarNum
                    : null;
            var ss = l.ifScalarString != null && r.ifScalarString != null && l.ifScalarString.equals(r.ifScalarString)
                    ? l.ifScalarString
                    : null;
            var res = new AVal(ra, ty);
            res.ifScalarNum = si;
            res.ifScalarString = ss;
            for (var k : l.values.keySet())
                if (r.values.containsKey(k))
                    res.values.put(k, AVal.merge(l.values.get(k), r.values.get(k)));
            for (var k : l.values.keySet())
                if (!r.values.containsKey(k))
                    res.values.put(k, new AVal(Range.top, l.values.get(k).t));
            for (var k : r.values.keySet())
                if (!l.values.containsKey(k))
                    res.values.put(k, new AVal(Range.top, r.values.get(k).t));
            return res;
        }

        BOOL isScalar() {
            return r.isScalar();
        }

        boolean isConcrete() {
            var notC = isBot() || isTop() || r.isTop() || r.isBot() || t.isTop() || t.isBot();
            if (!notC && isScalar() == BOOL.N) {
                for (int i = 0; i < r.to; i++)
                    if (!values.containsKey(i) || !values.get(i).isConcrete())
                        return false;
                return true;
            } else if (!notC && isScalar() == BOOL.Y) {
                return ifScalarNum != null || ifScalarString != null;
            } else
                return false;
        }

        AVal getVal(AVal index) {
            if (isBot() || index.isBot())
                return bot;
            if (isTop() || index.isTop())
                return top;
            if (index.isNum() == BOOL.N)
                return bot;
            if (index.isNum() == BOOL.M)
                return top;
            if (index.isScalar() == BOOL.N)
                return bot;
            if (index.isScalar() == BOOL.M)
                return top;
            var idx = index.asNum();
            var in = idx == null ? BOOL.M : r.in(idx);
            if (in == BOOL.Y) {
                if (isScalar() == BOOL.Y) // idx==0
                    return this;
                if (values.containsKey(idx))
                    return values.get(idx);
                else
                    return new AVal(Range.mkScalar(), t);
            } else if (in == BOOL.N) {
                throw new RuntimeException("indexing error"); // perhaps return an error?
            } else { // BOOL.M
                return new AVal(Range.mkScalar(), t);
            }
        }

        Integer asNum() {
            if (isScalar() != BOOL.Y || isNum() != BOOL.Y)
                throw new RuntimeException("check that the value is a num before calling asNum");
            return ifScalarNum;
        }

        AVal set(AVal index, AVal val) {
            if (isBot() || index.isBot())
                return bot;
            if (index.isNum() == BOOL.N)
                return bot;
            if (index.isNum() == BOOL.M)
                return top;
            if (index.isScalar() != BOOL.Y)
                return new AVal(Range.top, t);
            var idx = index.asNum();
            var in = idx == null ? BOOL.M : r.in(idx);
            if (in == BOOL.Y) {
                var res = clone();
                res.values.put(idx, val);
                return res;
            } else if (in == BOOL.N)
                throw new RuntimeException("array out of bounds");
            else // BOOL.M
                return top;
        }

        AVal size() {
            if (isBot())
                return bot;
            if (isTop())
                return top;
            if (r.isBot())
                return bot;
            if (r.isTop())
                return anyInt;
            return new AVal(r.size());
        }

        AVal sub(AVal r_) {
            var l_ = this;
            if (l_.isTop() || r_.isTop())
                return top;
            if (l_.isBot() || r_.isBot())
                return bot;
            if (l_.isNum() == BOOL.N || r_.isNum() == BOOL.N)
                return bot;
            if (l_.isNum() == BOOL.M || r_.isNum() == BOOL.M)
                return top;
            if (l_.isScalar() == BOOL.N || r_.isScalar() == BOOL.N)
                return bot;
            if (l_.isScalar() == BOOL.M || r_.isScalar() == BOOL.M)
                return anyInt;
            var lv = l_.asNum();
            var rv = r_.asNum();
            if (lv != null && rv != null)
                return new AVal(lv - rv);
            else
                return new AVal(Range.mkScalar(), Type.mkInt());
        }

        AVal add(AVal r) {
            var l = this;
            if (l.isTop() || r.isTop())
                return top;
            if (l.isBot() || r.isBot())
                return bot;
            if (l.isNum() == BOOL.N || r.isNum() == BOOL.N)
                return bot;
            if (l.isNum() == BOOL.M || r.isNum() == BOOL.M)
                return top;
            if (l.isScalar() == BOOL.N || r.isScalar() == BOOL.N)
                return bot;
            if (l.isScalar() == BOOL.M || r.isScalar() == BOOL.M)
                return anyInt;
            var lv = l.asNum();
            var rv = r.asNum();
            if (lv != null && rv != null)
                return new AVal(lv + rv);
            else
                return new AVal(Range.mkScalar(), Type.mkInt());
        }

        BOOL eq(AVal r_) {
            var l_ = this;
            if (l_.isTop() || r_.isTop())
                return BOOL.M;
            if (l_.isBot() || r_.isBot())
                return BOOL.N;
            if (l_.isNum() == BOOL.N || r_.isNum() == BOOL.N)
                return BOOL.N;
            if (l_.isNum() == BOOL.M || r_.isNum() == BOOL.M)
                return BOOL.M;
            if (l_.isScalar() == BOOL.N || r.isScalar() == BOOL.N)
                return BOOL.N;
            if (l_.isScalar() == BOOL.M || r.isScalar() == BOOL.M)
                return BOOL.M;
            var lv = l_.asNum();
            var rv = r_.asNum();
            if (lv != null && rv != null)
                return lv == rv ? BOOL.Y : BOOL.N;
            else
                return BOOL.M;
        }

        BOOL isNum() {
            return t.isNum();
        }

        BOOL isStr() {
            return t.isStr();
        }

        boolean isTop() {
            return equals(top);
        }

        boolean isBot() {
            return equals(bot);
        }

        public AVal clone() {
            var res = new AVal();
            res.r = r;
            res.t = t;
            for (var k : values.keySet())
                res.values.put(k, values.get(k));
            res.ifScalarNum = ifScalarNum;
            res.ifScalarString = ifScalarString;
            return res;
        }

        public boolean equals(Object other) {
            if (other instanceof AVal o) {
                var res = r.equals(o.r) &&
                        t.equals(o.t);
                res &= ifScalarNum == null ? o.ifScalarNum == null
                        : o.ifScalarNum != null && ifScalarNum.equals(o.ifScalarNum);
                res &= ifScalarString == null ? o.ifScalarString == null
                        : o.ifScalarString != null && ifScalarString.equals(o.ifScalarString);
                res &= values.size() == o.values.size();
                if (res)
                    for (int i = 0; i < values.size(); i++)
                        res &= values.get(i).equals(o.values.get(i));
                return res;
            } else
                return false;
        }

        public String toString() {
            if (isTop())
                return "T";
            if (isBot())
                return "_";
            if (isScalar() == BOOL.Y && t.isNum() == BOOL.Y && ifScalarNum != null)
                return "" + ifScalarNum;
            else if (isScalar() == BOOL.Y && t.isStr() == BOOL.Y && ifScalarString != null)
                return ifScalarString;
            if (isScalar() == BOOL.Y && t.isNum() == BOOL.Y && ifScalarNum == null)
                return "I";
            else if (isScalar() == BOOL.Y && t.isStr() == BOOL.Y && ifScalarString == null)
                return "S";
            else {
                var res = "c(";
                for (int i = 0; i < r.to; i++) {
                    res += values.get(i);
                    if (i < r.to - 1)
                        res += ",";
                }
                return res + ")";
            }
        }
    }

    static class Range {
        static Range bot = new Range(0, -1);

        static Range top = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

        static Range mk() {
            return bot;
        }

        static Range mkScalar() {
            return new Range(0, 1);
        }

        private int from, to;

        private Range(int from, int to) {
            this.from = from;
            this.to = to;
        }

        BOOL in(int i) {
            if (to == Integer.MAX_VALUE)
                return BOOL.M;
            if (isBot())
                return BOOL.N;
            return i >= from && i < to ? BOOL.Y : BOOL.N;
        }

        Range set(int from, int to) {
            return new Range(from, to);
        }

        static Range merge(Range l, Range r) {
            return new Range(Math.min(l.from, r.from), Math.max(l.to, r.to));
        }

        int size() {
            if (isBot() || isTop())
                throw new RuntimeException("can't call that on this");
            return to - from;
        }

        BOOL isScalar() {
            if (from == 0 && to == 1)
                return BOOL.Y;
            if (this.equals(bot))
                return BOOL.N;
            if (to == Integer.MAX_VALUE)
                return BOOL.M;
            else
                return BOOL.N;
        }

        public boolean isTop() {
            return equals(top);
        }

        public boolean isBot() {
            return equals(bot);
        }

        public boolean leq(Range r) {
            return equals(merge(this, r));
        }

        public boolean equals(Object o) {
            return o instanceof Range r && from == r.from && to == r.to;
        }

        public String toString() {
            return equals(top) ? "[T]" : equals(bot) ? "[_]" : "[" + from + "," + to + "]";
        }
    }

    static class Type {
        private enum T {
            STR, NUM, BOT, TOP
        };

        private static Type bot = new Type(T.BOT);
        private static Type top = new Type(T.TOP);
        private static Type int_ = new Type(T.NUM);
        private static Type str_ = new Type(T.STR);

        static Type mk() {
            return bot;
        }

        static Type mkInt() {
            return int_;
        }

        static Type mkStr() {
            return str_;
        }

        private T type;

        private Type(T type) {
            this.type = type;
        }

        BOOL isNum() {
            return switch (type) {
                case BOT -> BOOL.N;
                case TOP -> BOOL.M;
                case STR -> BOOL.N;
                default -> BOOL.Y;
            };
        }

        BOOL isStr() {
            return switch (type) {
                case BOT -> BOOL.N;
                case TOP -> BOOL.M;
                case STR -> BOOL.Y;
                default -> BOOL.N;
            };
        }

        static Type merge(Type l, Type r) {
            return switch (l.type) {
                case BOT -> r;
                case TOP -> r;
                default -> l.type == r.type ? r : top;
            };
        }

        public boolean equals(Object o) {
            return o instanceof Type t && type == t.type;
        }

        public boolean leq(Type t) {
            return this.equals(merge(this, t));
        }

        boolean isBot() {
            return type == T.BOT;
        }

        boolean isTop() {
            return type == T.TOP;
        }

        public String toString() {
            return switch (type) {
                case BOT -> "_";
                case TOP -> "T";
                case NUM -> "I";
                case STR -> "S";
            };
        }
    }
}
