package app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.Val.BOOL;

class Val {
    enum BOOL { // Three value logic
        Y, N, M // Yes, No, Maybe
    };

    static Val bot = new Val();
    static Val top = new Val(Range.top, Type.top);
    private static Val anyInt = new Val(Range.mkScalar(), Type.mkInt());

    private Range r = Range.mk();
    private Type t = Type.mk();
    private Map<Integer, Val> values = new HashMap<Integer, Val>();
    private Integer ifScalarNum;
    private String ifScalarString;

    Val(int v) {
        r = Range.mkScalar();
        t = Type.mkInt();
        ifScalarNum = v;
    }

    Val(String s) {
        r = Range.mkScalar();
        t = Type.mkStr();
        ifScalarString = s;
    }

    Val(List<Val> vals) {
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

    private Val() {
    }

    private Val(Range r, Type t) {
        this.r = r;
        this.t = t;
    }

    static Val merge(Val l, Val r) {
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
        var res = new Val(ra, ty);
        res.ifScalarNum = si;
        res.ifScalarString = ss;
        for (var k : l.values.keySet())
            if (r.values.containsKey(k))
                res.values.put(k, Val.merge(l.values.get(k), r.values.get(k)));
        for (var k : l.values.keySet())
            if (!r.values.containsKey(k))
                res.values.put(k, new Val(Range.top, l.values.get(k).t));
        for (var k : r.values.keySet())
            if (!l.values.containsKey(k))
                res.values.put(k, new Val(Range.top, r.values.get(k).t));
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

    Val getVal(Val index) {
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
                return new Val(Range.mkScalar(), t);
        } else if (in == BOOL.N) {
            throw new RuntimeException("indexing error"); // perhaps return an error?
        } else { // BOOL.M
            return new Val(Range.mkScalar(), t);
        }
    }

    Integer asNum() {
        if (isScalar() != BOOL.Y || isNum() != BOOL.Y)
            throw new RuntimeException("check that the value is a num before calling asNum");
        return ifScalarNum;
    }

    Val set(Val index, Val val) {
        if (isBot() || index.isBot())
            return bot;
        if (index.isNum() == BOOL.N)
            return bot;
        if (index.isNum() == BOOL.M)
            return top;
        if (index.isScalar() != BOOL.Y)
            return new Val(Range.top, t);
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

    Val size() {
        if (isBot())
            return bot;
        if (isTop())
            return top;
        if (r.isBot())
            return bot;
        if (r.isTop())
            return anyInt;
        return new Val(r.size());
    }

    Val sub(Val r_) {
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
            return new Val(lv - rv);
        else
            return new Val(Range.mkScalar(), Type.mkInt());
    }

    Val add(Val r) {
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
            return new Val(lv + rv);
        else
            return new Val(Range.mkScalar(), Type.mkInt());
    }

    BOOL eq(Val r_) {
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

    public Val clone() {
        var res = new Val();
        res.r = r;
        res.t = t;
        for (var k : values.keySet())
            res.values.put(k, values.get(k));
        res.ifScalarNum = ifScalarNum;
        res.ifScalarString = ifScalarString;
        return res;
    }

    public boolean equals(Object other) {
        if (other instanceof Val o) {
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

class Range {
    static Range bot = new Range(0, -1);

    static Range top = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

    static Range mk() {
        return bot;
    }

    static Range mkScalar() {
        return new Range(0, 1);
    }

    int from, to;

    Range(int from, int to) {
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

class Type {
    private enum T {
        STR, NUM, BOT, TOP
    };

    private static Type bot = new Type(T.BOT);
    static Type top = new Type(T.TOP);
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
