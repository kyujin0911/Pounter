#!/usr/bin/env python3
from pathlib import Path
import re
import argparse
p=argparse.ArgumentParser(description='Emit an Android C++ regression using the actual ED sampler and validation call site; no unmarked double is read.')
p.add_argument('output',type=Path)
p.add_argument('--source',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/cpp/ed/edge_drawing.cpp')
a=p.parse_args()
s=a.source.read_text()
start=s.index('void EdgeDrawingImpl::ValidateCircles')
block=s[s.index('        if (circle->isEllipse)\n        {\n            ',start):s.index('        int pr = -1;',start)]
s=s.replace('private:', 'public:').replace('protected:', 'public:')
for name,nextname in [('ComputeEllipsePoints','ComputeEllipseError'),('ComputeCirclePoints','EllipseFit')]:
 start=s.index('void EdgeDrawingImpl::'+name+'(')
 end=s.index('\n}\n',start)+3
 chunk=s[start:end]
 chunk=re.sub(r'(py\[([^\n]+?)\] = [^;]+;)',r'\1 samplingMark(\2);',chunk)
 s=s[:start]+chunk+s[end:]
prefix='''#include <iostream>
#include <fstream>
#include <iomanip>
#include <chrono>
#include <vector>
#include <sys/resource.h>
static std::vector<int> samplingWriters;
static int samplingGeneration=0, samplingNever=0, samplingStale=0;
static bool samplingActive=false, samplingTrace=false;
static void samplingBegin(int n) { samplingWriters.assign(n,0); samplingGeneration=0; samplingNever=samplingStale=0; samplingActive=true; }
static void samplingMark(int i) { if(samplingActive) samplingWriters.at(i)=samplingGeneration; }
'''
main=r'''
int main(int argc,char** argv) {
 using D=cv::ximgproc::EdgeDrawingImpl;
 std::cout<<std::setprecision(17);
 if(argc==1) {
   double px[4096],py[4096];
   EllipseEquation eq{};eq.coeff[1]=1.0/900;eq.coeff[3]=1.0/225;eq.coeff[4]=-100.0/900;eq.coeff[5]=-100.0/225;eq.coeff[6]=2500.0/900+2500.0/225-1;
   samplingBegin(4096); int violations=0;
   const int sizes[]={11,12,15,9,8,101,7};
   for(int n:sizes) {
     ++samplingGeneration;int noPoints=n;Circle value{};Circle* circle=&value;circle->isEllipse=true;circle->eq=eq;double xc=50,yc=50,radius=30;
     CALLER
     int first=0,stale=0;for(int j=0;j<noPoints;j++)if(samplingWriters[j]!=samplingGeneration) { if(samplingWriters[j]==0)first++;else stale++; }
     violations+=first+stale;
     std::cout<<"{\"event\":\"regression\",\"requested\":"<<n<<",\"N\":"<<noPoints<<",\"never\":"<<first<<",\"stale\":"<<stale<<"}"<<std::endl;
   }
   ++samplingGeneration;int noPoints=0;D::ComputeCirclePoints(50,50,30,px,py,&noPoints);
   if(!D::EllipseFit(px,py,noPoints,&eq))return 3;
   ++samplingGeneration;noPoints=11;Circle value{};Circle* circle=&value;circle->isEllipse=true;circle->eq=eq;double xc=50,yc=50,radius=30;
   CALLER
   int stale=0;for(int j=0;j<noPoints;j++)if(samplingWriters[j]!=samplingGeneration)stale++;
   violations+=stale;std::cout<<"{\"event\":\"circle-to-ellipse\",\"N\":"<<noPoints<<",\"stale\":"<<stale<<"}"<<std::endl;
   return violations?2:0;
 }
}
'''.replace('CALLER',block.replace('ComputeEllipsePoints(', 'D::ComputeEllipsePoints(').replace('ComputeCirclePoints(', 'D::ComputeCirclePoints('))
a.output.write_text(prefix+s+main)
