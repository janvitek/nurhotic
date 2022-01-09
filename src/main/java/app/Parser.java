package app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Stack;

import app.Parser.Tok.Kind;

public class Parser {

    static class Stmt {
        int lineNum;
        String source;

        Stmt(int lineNum, String source) {
            this.lineNum = lineNum;
            this.source = source;
        }
    }

    static record Fun(String name, List<String> args, List<Stmt> body) {

    }

    static record Prog(List<Fun> funs, List<Stmt> main) {
    }

    static class Call extends Stmt {
        final String t_var; // t_var <- f_name(params)
        final String f_name;
        final List<Val> params;

        Call(String tgt, String funname, List<Val> exps, int lineNum, String source) {
            super(lineNum, source);
            this.t_var = tgt;
            this.f_name = funname;
            this.params = exps;
        }
    }

    static class While extends Stmt {
        Val guard;
        List<Stmt> body;

        While(Val guard, List<Stmt> body, int lineNum, String source) {
            super(lineNum, source);
            this.guard = guard;
            this.body = body;
        }
    }

    static class If extends Stmt {
        Val guard;
        List<Stmt> body;

        If(Val guard, List<Stmt> body, int lineNum, String source) {
            super(lineNum, source);
            this.guard = guard;
            this.body = body;
        }
    }

    static enum Kind {
        STR, NUM, ID
    }

    static record Val(Kind k, String val) {
        int asNum() {
            if (k != Kind.NUM)
                throw new RuntimeException("Intenal error");
            return Integer.parseInt(val);
        }

        String asStr() {
            if (k != Kind.STR)
                throw new RuntimeException("Intenal error");
            return val;
        }

        String asId() {
            if (k != Kind.ID)
                throw new RuntimeException("Intenal error");
            return val;
        }

        Kind kind() {
            return k;
        }
    }

    Tokens in;
    Integer intValue = null;
    String stringValue = null;
    int[] pos2line;
    List<String> sourceLines;

    Parser(String file) {
        try {
            var scan = new Scanner(new File(file));
            var tokenize = new Tokenizer(scan.useDelimiter("\\Z").next());
            in = new Tokens(tokenize.tokenize());
            pos2line = tokenize.p_to_l;
            sourceLines = tokenize.lines;
            scan.close();
        } catch (IOException e) {
            throw new RuntimeException(e);// Not much else to do.
        }
    }

    Prog parse() {
        var funs = new ArrayList<Fun>();
        Fun f = null;
        while ((f = parseFun()) != null)
            funs.add(f);
        eat("prog");
        var main = parseStatements();
        return new Prog(funs, main);
    }

    Fun parseFun() {
        if (!tryEat("fun"))
            return null;
        var nm = id();
        eat("(");
        var args = new ArrayList<String>();
        while (tryId()) {
            args.add(stringValue);
            if (!tryEat(","))
                break;
        }
        eat(")");
        var stmts = parseStatements();
        return new Fun(nm, args, stmts);
    }

    Call parseCall() {
        var head = in.peek().p();
        if (!tryId())
            return null;
        var vnm = stringValue;
        eat("=");
        var fnm = id();
        eat("(");
        var vals = new ArrayList<Val>();
        Val v = null;
        while ((v = parseVal()) != null) {
            vals.add(v);
            if (!tryEat(","))
                break;
        }
        eat(")");
        return new Call(vnm, fnm, vals, pos2line[head], sourceLines.get(pos2line[head]));
    }

    Val parseVal() {
        if (tryNum())
            return new Val(Kind.NUM, stringValue);
        else if (tryStr())
            return new Val(Kind.STR, stringValue);
        else if (tryId())
            return new Val(Kind.ID, stringValue);
        return null;
    }

    If parseIf() {
        var head = in.peek().p();
        if (!tryEat("if"))
            return null;
        eat("(");
        var v = parseVal();
        eat(")");
        var stmts = parseStatements();
        return new If(v, stmts, pos2line[head], sourceLines.get(pos2line[head]));
    }

    While parseWhile() {
        var head = in.peek().p();
        if (!tryEat("while"))
            return null;
        eat("(");
        var v = parseVal();
        eat(")");
        var s = parseStatements();
        return new While(v, s, pos2line[head], sourceLines.get(pos2line[head]));
    }

    List<Stmt> parseStatements() {
        var stmts = new ArrayList<Stmt>();
        while (true) {
            if (tryEat("end"))
                break;
            var i = parseIf();
            if (i != null) {
                stmts.add(i);
                continue;
            }
            var w = parseWhile();
            if (w != null) {
                stmts.add(w);
                continue;
            }
            var c = parseCall();
            if (c != null) {
                stmts.add(c);
                continue;
            }
            break;
        }
        return stmts;
    }

    String id() {
        failIfNot(tryId());
        return stringValue;
    }

    Parser eat(String s) {
        failIfNot(tryEat(s));
        return this;
    }

    boolean tryNum() {
        return in.m().num().u();
    }

    boolean tryStr() {
        return in.m().str().u();
    }

    Parser failIfNot(boolean c) {
        if (!c)
            fail();
        return this;
    }

    boolean tryEat(String s) {
        return in.m().e(s).u();
    }

    // either eat an id or return false
    boolean tryId() {
        return in.m().id().u();
    }

    Parser failIfNull(Object o) {
        if (o == null)
            fail();
        return this;
    }

    String failMsg() {
        var msg = "Failed at pos " + in.position();
        for (int i = 0; i < 7; i++)
            if (in.hasNext())
                msg += " " + in.next().v();
        return msg;
    }

    void fail() {
        throw new ParseError(failMsg());
    }

    class ParseError extends Error {
        ParseError(String msg) {
            super(msg);
        }
    }

    record Tok(Kind k, String v, int p) {
        enum Kind {
            IDENT, NUM, STR, DELIM
        }

        public Kind k() {
            return k;
        }

        public String v() {
            return v;
        }

        public int p() {
            return p;
        }

    }

    class Tokens {
        private ArrayList<Tok> tokens;
        private int pos = 0;

        Tokens(ArrayList<Tok> tokens) {
            this.tokens = tokens;
        }

        Tok next() {
            return pos < tokens.size() ? tokens.get(pos++) : null;
        }

        Tok peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }

        int position() {
            return peek().p();
        }

        boolean hasNext() {
            return pos < tokens.size();
        }

        // Try to eat string e from the stream
        Tokens e(String s) {
            setBad(!hasNext() || (good() && !next().v().equals(s)));
            return this;
        }

        // Try to eat a number from the stream, store it in the parser
        Tokens num() {
            setBad(!hasNext());
            if (good() && peek().k().equals(Tok.Kind.NUM)) {
                try {
                    stringValue = next().v();
                    intValue = Integer.parseInt(stringValue);
                } catch (NumberFormatException e) {
                    intValue = null;
                    setBad(true);
                }
            } else
                setBad(true);
            return this;
        }

        // Try to eat an identifier from the stream, store it in the parser
        Tokens id() {
            setBad(!hasNext());
            var tok = next();
            if (good() && tok.k().equals(Tok.Kind.IDENT))
                stringValue = tok.v();
            else {
                setBad(true);
                stringValue = null;
            }
            return this;
        }

        Tokens str() {
            setBad(!hasNext());
            var tok = next();
            if (good() && tok.k().equals(Tok.Kind.STR))
                stringValue = tok.v();
            else {
                setBad(true);
                stringValue = null;
            }
            return this;
        }

        Stack<Integer> marks = new Stack<>();
        Stack<Boolean> goods = new Stack<>();

        // Add a mark, if the last mark is bad, this one is too
        Tokens m() {
            marks.push(pos);
            goods.push(goods.size() > 0 ? goods.peek() : true);
            return this;
        }

        // Is the current parse good?
        boolean good() {
            return goods.peek();
        }

        // Set the current parse !t unless it's already bad
        Tokens setBad(boolean t) {
            goods.push(goods.pop() && !t);
            return this;
        }

        // Unmark, and reset state if the parse is bad
        boolean u() {
            var oldpos = marks.pop();
            if (goods.pop())
                return true;
            pos = oldpos;
            return false;
        }
    }

    static class Tokenizer {
        int pos = 0;
        String ln;
        String read;
        int lineNum = 0;
        List<String> lines = new ArrayList<String>();
        int[] p_to_l;

        Tokenizer(String ln) {
            this.ln = ln;
            p_to_l = new int[ln.length()];
            var p = 0;
            var n = 0;
            while (true) {
                if (n == ln.length() || ln.charAt(n) == '\n') {
                    for (int i = p; i < n; i++)
                        p_to_l[i] = lines.size();
                    lines.add(ln.substring(p, n));
                    if (n == ln.length())
                        break;
                    p = n + 1;
                }
                n++;
            }
        }

        boolean spaces() {
            var start = pos;
            while (!eof() && Character.isWhitespace(cur())) {
                if (cur() == '\n')
                    lineNum++;
                pos++;
            }
            return start != pos;
        }

        boolean num() {
            var start = pos;
            int digits = 0;
            while (!eof() && (Character.isDigit(cur()) || cur() == '.')) {
                if (cur() == '.')
                    digits++;
                pos++;
            }
            if (digits > 1 || start == pos) {
                start = pos;
                return false;
            }
            read = ln.substring(start, pos);
            if (canPeek() && Character.isAlphabetic(cur())) {
                pos = start;
                return false;
            }
            return true;
        }

        boolean comment() {
            var start = pos;
            if (!eof() && cur() == '#') {
                pos++;
                while (!eof() && cur() != '\n') {
                    pos++;
                }
            }
            return start != pos;
        }

        boolean str() {
            var start = pos;
            if (!eof() && cur() == '"') {
                pos++;
                while (!eof() && cur() != '"')
                    pos++;
                if (pos < ln.length())
                    pos++;
            }
            if (start + 1 < pos - 1)
                read = ln.substring(start + 1, pos - 1);
            return start != pos;
        }

        boolean hasIdChar() {
            if (cur() == '\\') {
                var c = peek();
                return c == ':' || c == ' ';
            } else {
                var c = cur();
                return Character.isAlphabetic(c) || Character.isDigit(c) || c == '_' || c == '.';
            }
        }

        void eatIdChar() {
            pos += cur() == '\\' ? 2 : 1;
        }

        boolean id() {
            var start = pos;
            if (!eof() && hasIdChar() && !Character.isDigit(cur())) {
                eatIdChar();
                while (!eof() && hasIdChar())
                    eatIdChar();
            }
            read = ln.substring(start, pos);
            return start != pos;
        }

        boolean read(String key) {
            var start = pos;
            for (int i = 0; i < key.length(); i++) {
                if (pos == ln.length() || ln.charAt(pos++) != key.charAt(i)) {
                    pos = start;
                    return false;
                }
            }
            return true;
        }

        char next() {
            return ln.charAt(++pos);
        }

        char peek() {
            return ln.charAt(pos + 1);
        }

        char cur() {
            return ln.charAt(pos);
        }

        boolean canPeek() {
            return pos + 1 < ln.length();
        }

        boolean eof() {
            return pos >= ln.length();
        }

        String[] delims = new String[] { ":", ",", "=", "|", "(", ")", "[", "]", "{", "}", "?", "*", "-" };

        ArrayList<Tok> tokenize() {
            var tokens = new ArrayList<Tok>();
            A: while (!eof()) {
                comment();
                spaces();
                if (num())
                    tokens.add(new Tok(Tok.Kind.NUM, read, pos));
                else if (str())
                    tokens.add(new Tok(Tok.Kind.STR, read, pos));
                else if (id())
                    tokens.add(new Tok(Tok.Kind.IDENT, read, pos));
                else
                    for (String delim : delims)
                        if (read(delim)) {
                            tokens.add(new Tok(Tok.Kind.DELIM, delim, pos));
                            continue A;
                        }
            }
            return tokens;
        }
    }
}