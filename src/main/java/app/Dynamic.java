package app;

import app.Parser.Prog;

class Dynamic extends Abstract {

    Dynamic(Prog p) {
        super(p);
    }

    void observe(int pc, int nextPC) {
        var op = Op.get(pc);
        op.exec(astates[pc]);
        App.p(App.pad(pc + " : ", 6) + App.pad(op + " ", 14) + App.pad(astates[nextPC] + " ", 40) + "<--dynamic");
    }
}