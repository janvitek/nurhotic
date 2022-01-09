package app;

import app.Parser.Prog;

class Dynamic extends Abstract {

    Dynamic(Prog p) {
        super(p);
    }

    void observe(int pc) {
        var op = Op.get(pc);
        var in = op.exec(astates[pc]);
        App.p(App.pad(pc + " : ", 6) + App.pad(op + " ", 14) + App.pad(in + " ", 40) + "<--dynamic");
    }
}