package app;

public class App {
    public static void main(String[] a_) {
        var m = new Parser("app.r");
        var p = m.parse();
        var c = new Concrete(p);
        p("Running concrete");
        c.execute();
        p("Done with " + c.in.last());
        var a = new Abstract();
        p("Running abstract");
        a.analyze();
        p("Done with ");
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
