fun sum(v)
    x = length(v)
    g = sub(0,x)
    r = c(0)
    while(g)
        i = sub(x,1)
        t = get(v,i)
        r = add(t,r)
        x = sub(x,1)
        g = add(g,1)
    end
    r = c(r)
end
prog
  v = c(1,2,3)
  r = sum(v)
end
