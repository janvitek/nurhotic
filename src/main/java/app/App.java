package app;

public class App {
    public static void main(String[] a_) {
        var m = new Parser("app.r");
        var p = m.parse();
        var c = new Concrete(p);
        p("Running concrete");
        c.execute(null);
        p("Done with " + c.in.last());
        var a = new Abstract(p);
        p("Running abstract");
        var st = a.analyze();
        p("Done with " + st.last());
        var d = new Dynamic(p);
        p("Running dynamic");
        d.execute(d);
        p("Done with " + d.astates[d.astates.length - 1].last());
    }

    public static void p(String s) {
        System.out.println(s);
    }

    static String pad(String s, int pad) {
        while (pad > s.length())
            s += " ";
        return s;
    }
}
