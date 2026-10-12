#!/usr/bin/env python3
"""Looks for z-fighting in a pipe model: faces in one plane that look the same way and overlap,
over every combination of connected sides. Usage: check_pipe_overlaps.py [blockstate name]"""
import json,itertools,os,sys
A='src/main/resources/assets/logisticspipes/'
def load(path):
    V=[];F=[]
    for l in open(path):
        p=l.split()
        if not p: continue
        if p[0]=='v': V.append(tuple(float(x) for x in p[1:4]))
        elif p[0]=='f': F.append([V[int(c.split('/')[0])-1] for c in p[1:]])
    return F
def plane(f):
    a,b,c=f[:3]
    u=[b[i]-a[i] for i in range(3)]; w=[c[i]-a[i] for i in range(3)]
    n=(u[1]*w[2]-u[2]*w[1],u[2]*w[0]-u[0]*w[2],u[0]*w[1]-u[1]*w[0])
    l=sum(x*x for x in n)**.5
    if l<1e-9: return None
    n=tuple(round(x/l,3)+0.0 for x in n)
    return n, round(sum(n[i]*a[i] for i in range(3)),4)
def flat(f,n):
    drop=max(range(3),key=lambda i:abs(n[i]))
    return [tuple(p[i] for i in range(3) if i!=drop) for p in f]
def area(p):
    return sum(p[i][0]*p[(i+1)%len(p)][1]-p[(i+1)%len(p)][0]*p[i][1] for i in range(len(p)))/2
def clip(subject,clipper):
    if area(clipper)<0: clipper=clipper[::-1]
    out=subject
    for i in range(len(clipper)):
        a,b=clipper[i],clipper[(i+1)%len(clipper)]
        inp,out=out,[]
        def inside(p): return (b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0])>=-1e-9
        for j in range(len(inp)):
            p,q=inp[j],inp[(j+1)%len(inp)]
            if inside(p)!=inside(q):
                d1=(b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0]); d2=(b[0]-a[0])*(q[1]-a[1])-(b[1]-a[1])*(q[0]-a[0])
                t=d1/(d1-d2); x=(p[0]+t*(q[0]-p[0]),p[1]+t*(q[1]-p[1]))
                if inside(p): out.append(p); out.append(x)
                else: out.append(x)
            elif inside(p): out.append(p)
        if not out: return 0
    return abs(area(out))
name=sys.argv[1] if len(sys.argv)>1 else 'basic_logistics_pipe'
bs=json.load(open(A+'blockstates/'+name+'.json'))['multipart']
parts=[]
for e in bs:
    m=e['apply']['model'].split(':')[1]
    j=json.load(open(A+'models/'+m+'.json'))
    obj=A+j['model'].split(':')[1]
    parts.append((e['when'],os.path.basename(obj),load(obj)))
SIDES=['north','south','east','west','up','down']
bad={}
for bits in itertools.product(['true','false'],repeat=6):
    state=dict(zip(SIDES,bits))
    byplane={}
    for w,n,F in parts:
        # other properties only swap textures, their first value stands for all
        if all(state.setdefault(k, v)==v for k,v in w.items()):
            for f in F:
                pl=plane(f)
                if pl: byplane.setdefault(pl,[]).append((n,f))
    for pl,fs in byplane.items():
        for (n1,f1),(n2,f2) in itertools.combinations(fs,2):
            a=clip(flat(f1,pl[0]),flat(f2,pl[0]))
            if a>1e-6:
                k=tuple(sorted((n1,n2))); bad.setdefault(k,(0,pl,f1,f2)); bad[k]=(bad[k][0]+1,)+bad[k][1:]
print(len(bad),'overlapping pairs')
for k,v in sorted(bad.items(),key=lambda x:-x[1][0])[:int(sys.argv[2]) if len(sys.argv)>2 else 12]: print(k,v[0],v[1],[tuple(round(x,4) for x in p) for p in v[2]],[tuple(round(x,4) for x in p) for p in v[3]])
sys.exit(1 if bad else 0)
