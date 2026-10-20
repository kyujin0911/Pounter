"""Generate an evaluation-only standalone trace copy; never writes detector source.
Usage: python3 scripts/native_stage_trace.py --source <edge_drawing.cpp> --output <new-dir>
Truth, target coordinates and ROIs are deliberately not inputs.
Optional --watch-point-hashes A [B ...] selects only pair logging, never detection.
"""
from pathlib import Path
import argparse,hashlib,json,re
p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--watch-point-hashes',type=int,nargs='+',default=[0]);a=p.parse_args();assert all(0<=h<2**64 for h in a.watch_point_hashes);O=a.output;O.mkdir(parents=True,exist_ok=False)
s=a.source.read_text();assert 'noPoints -= noPoints % 2;' in s
prefix=r'''
#include <iostream>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <sys/resource.h>
static bool traceOn=false,traceTruncated=false;
static const char* traceStage="closed-segment";
static size_t traceEvents=0,tracePoints=0,traceBytes=0;
static bool tracePermit(int n) {
 if(!traceOn)return false;
 if(traceEvents>=200000 || tracePoints+size_t(n)>2000000 || traceBytes>=64*1024*1024){traceTruncated=true;return false;}
 ++traceEvents;tracePoints+=n;return true;
}
static void traceWrite(const std::ostringstream& o){auto s=o.str();if(traceBytes+s.size()+1>64*1024*1024){traceTruncated=true;return;}traceBytes+=s.size()+1;std::cout<<s<<'\n';}
static void traceXY(std::ostream& o,const double*x,const double*y,int n){
 unsigned long long h=1469598103934665603ULL;
 o<<",\"points\":[";for(int i=0;i<n;i++){if(i)o<<",";o<<"["<<x[i]<<","<<y[i]<<"]";for(double v:{x[i],y[i]}){unsigned long long b;std::memcpy(&b,&v,8);h^=b;h*=1099511628211ULL;}}
 o<<"],\"pointHash\":\""<<h<<"\"";
}
'''
# memcpy declared by the original ownership header; prefix needs its own include.
prefix='#include <cstring>\n'+prefix
main=r'''
int main(int argc,char**argv){
 if(argc<4)return 2;
 int w=std::stoi(argv[2]),h=std::stoi(argv[3]);if(w<=0||h<=0||w>10000||h>10000)return 3;
 cv::Mat gray(h,w,CV_8UC1);std::ifstream f(argv[1],std::ios::binary);f.read((char*)gray.data,size_t(w)*h);if(f.gcount()!=size_t(w)*h)return 4;
 traceOn=argc>4;std::cout<<std::setprecision(17);
 auto d=cv::ximgproc::createEdgeDrawing();d->params.NFAValidation=true;d->params.PFmode=false;
 auto start=std::chrono::steady_clock::now();d->detectEdges(gray);cv::Mat shapes;d->detectEllipses(shapes);
 double ms=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-start).count();struct rusage r;getrusage(RUSAGE_SELF,&r);
 std::cout<<"{\"event\":\"raw\",\"millis\":"<<ms<<",\"maxRssKB\":"<<r.ru_maxrss<<",\"truncated\":"<<traceTruncated<<",\"events\":"<<traceEvents<<",\"pointsLogged\":"<<tracePoints<<",\"bytesLogged\":"<<traceBytes<<",\"shapes\":[";
 for(int i=0;i<shapes.rows;i++){if(i)std::cout<<",";auto v=shapes.at<cv::Vec6d>(i);std::cout<<"[";for(int j=0;j<6;j++){if(j)std::cout<<",";std::cout<<v[j];}std::cout<<"]";}std::cout<<"]}\n";
}
'''
(O/'plain.cpp').write_text(prefix+s+main)
# Definitions remain private and in the same class; wrappers expose no API.
for name,sig in [('CircleFit','double* x, double* y, int N, double* pxc, double* pyc, double* pr, double* pe'),('EllipseFit','double* x, double* y, int noPoints, EllipseEquation* pResult, int mode')]:
 definition=f'bool EdgeDrawingImpl::{name}({sig})';assert s.count(definition)==1;s=s.replace(definition,definition.replace(name,name+'Base'),1)
 decl=f'    static bool {name}Base({sig});';s=s.replace('    static bool CircleFit(',decl+'\n    static bool CircleFit(',1)
helper=r'''
void EdgeDrawingImpl::traceGeometry(std::ostream& o,bool ellipse,const EllipseEquation*eq,double x,double y,double r){
 double a=r,b=r,t=0;if(ellipse)t=ComputeEllipseCenterAndAxisLengths(const_cast<EllipseEquation*>(eq),&x,&y,&a,&b);
 o<<",\"ellipse\":"<<ellipse<<",\"geometry\":["<<x<<","<<y<<","<<a<<","<<b<<","<<t<<"]";
}
void EdgeDrawingImpl::traceArcList(EDArcs* list,const char* stage){
 for(int i=0;i<list->noArcs;i++){const auto&a=list->arcs[i];if(!tracePermit(a.noPixels))continue;std::ostringstream o;o<<std::setprecision(17)<<"{\"event\":\"arc\",\"stage\":\""<<stage<<"\",\"id\":"<<i<<",\"segment\":"<<a.segmentNo<<",\"turn\":"<<a.turn<<",\"endpoints\":["<<a.sx<<","<<a.sy<<","<<a.ex<<","<<a.ey<<"],\"coverage\":"<<a.coverRatio<<",\"angles\":["<<a.sTheta<<","<<a.eTheta<<"],\"circleError\":"<<a.circleFitError<<",\"circleProxy\":["<<a.xc<<","<<a.yc<<","<<a.r<<"]";
 if(a.isEllipse)o<<",\"ellipseError\":"<<a.ellipseFitError;traceGeometry(o,a.isEllipse,&a.eq,a.xc,a.yc,a.r);traceXY(o,a.x,a.y,a.noPixels);o<<"}";traceWrite(o);}
}
void EdgeDrawingImpl::traceCircleList(Circle*list,int n,const char*stage){
 for(int i=0;i<n;i++){const auto&c=list[i];if(!tracePermit(c.noPixels))continue;std::ostringstream o;o<<std::setprecision(17)<<"{\"event\":\"candidate\",\"stage\":\""<<stage<<"\",\"id\":"<<i<<",\"circleError\":"<<c.circleFitError;
 if(c.isEllipse)o<<",\"ellipseError\":"<<c.ellipseFitError;traceGeometry(o,c.isEllipse,&c.eq,c.xc,c.yc,c.r);traceXY(o,c.x,c.y,c.noPixels);o<<"}";traceWrite(o);}
}
bool EdgeDrawingImpl::CircleFit(double*x,double*y,int n,double*xc,double*yc,double*r,double*err){
 bool ok=CircleFitBase(x,y,n,xc,yc,r,err);
 if(tracePermit(n)){std::ostringstream o;o<<std::setprecision(17)<<"{\"event\":\"fit\",\"stage\":\""<<traceStage<<"\",\"method\":\"circle\",\"ok\":"<<ok;
 if(ok){o<<",\"error\":"<<*err;traceGeometry(o,false,nullptr,*xc,*yc,*r);}traceXY(o,x,y,n);o<<"}";traceWrite(o);}return ok;
}
bool EdgeDrawingImpl::EllipseFit(double*x,double*y,int n,EllipseEquation*eq,int mode){
 bool ok=EllipseFitBase(x,y,n,eq,mode);
 if(tracePermit(n)){std::ostringstream o;o<<std::setprecision(17)<<"{\"event\":\"fit\",\"stage\":\""<<traceStage<<"\",\"method\":\"ellipse\",\"ok\":"<<ok;
 if(ok){o<<",\"error\":"<<ComputeEllipseError(eq,x,y,n);traceGeometry(o,true,eq,0,0,0);}traceXY(o,x,y,n);o<<"}";traceWrite(o);}return ok;
}
'''
s=s.replace('    void GenerateCandidateCircles();','''    static void traceGeometry(std::ostream&,bool,const EllipseEquation*,double,double,double);
    static void traceArcList(EDArcs*,const char*);
    static void traceCircleList(Circle*,int,const char*);
    void GenerateCandidateCircles();''',1)
pos=s.index('void EdgeDrawingImpl::detectEllipses(');s=s[:pos]+helper+s[pos:]
needle='    min_line_len = 6;';assert s.count(needle)==1
s=s.replace(needle,r'''
    traceStage="closed-segment";
    for(int i=0;i<segmentNos;i++){const auto&pts=segmentPoints[i];if(!tracePermit(pts.size()))continue;std::ostringstream o;o<<"{\"event\":\"segment\",\"id\":"<<i<<",\"points\":[";for(size_t j=0;j<pts.size();j++){if(j)o<<",";o<<"["<<pts[j].x<<","<<pts[j].y<<"]";}o<<"]}";traceWrite(o);}
'''+needle,1)
s=s.replace('    DetectArcs();    // Detect all arcs',r'''
    if(traceOn)for(int i=0;i<segmentNos;i++)for(int j=segmentStartLines[i];j<segmentStartLines[i+1];j++){
      const auto&l=lines[j];if(!tracePermit(0))continue;std::ostringstream o;o<<std::setprecision(17)<<"{\"event\":\"line\",\"id\":"<<j<<",\"segment\":"<<i<<",\"endpoints\":["<<l.sx<<","<<l.sy<<","<<l.ex<<","<<l.ey<<"],\"length\":"<<l.len<<",\"angle\":"<<info[j].angle<<",\"sign\":"<<info[j].sign<<"}";traceWrite(o);}
    traceStage="DetectArcs";
    DetectArcs();
    traceArcList(edarcs1,"DetectArcs");''',1)
for k in range(1,4):
 needle=f'    JoinArcs{k}();';assert s.count(needle)==1;s=s.replace(needle,f'    traceStage="JoinArcs{k}";\n'+needle+f'\n    traceArcList(edarcs{k+1},"JoinArcs{k}");',1)
for name in ['GenerateCandidateCircles','ValidateCircles','JoinCircles']:
 needle='    '+name+('params.NFAValidation' if name=='ValidateCircles' else '')+')' # explicit below
 needle='    '+name+'('+('params.NFAValidation' if name=='ValidateCircles' else '')+');'
 assert s.count(needle)==1
 before=''
 if name=='ValidateCircles':before='    traceCircleList(circles1,noCircles1,"pre-NFA");\n'
 after=''
 if name=='ValidateCircles':after='\n    traceCircleList(circles2,noCircles2,"validated");'
 if name=='JoinCircles':after='\n    traceCircleList(circles3,noCircles3,"joined");'
 s=s.replace(needle,before+f'    traceStage="{name}";\n'+needle+after,1)
needle='        bool isValid = !validate || nfa->checkValidationByNFA(noPeripheryPixels, aligned);';assert s.count(needle)==1
s=s.replace(needle,needle+r'''
        if(tracePermit(circle->noPixels)){std::ostringstream o;o<<std::setprecision(17)<<"{\"event\":\"nfa\",\"id\":"<<i<<",\"n\":"<<noPeripheryPixels<<",\"k\":"<<aligned<<",\"valid\":"<<isValid;traceGeometry(o,circle->isEllipse,&circle->eq,xc,yc,radius);traceXY(o,circle->x,circle->y,circle->noPixels);o<<"}";traceWrite(o);}
''',1)

# Record actual short-circuit rejection for one provenance pair, without changing predicates.
pairhelper=r"""
static unsigned long long traceHash(const MyArc&a){unsigned long long h=1469598103934665603ULL;for(int i=0;i<a.noPixels;i++)for(double v:{a.x[i],a.y[i]}){unsigned long long b;std::memcpy(&b,&v,8);h^=b;h*=1099511628211ULL;}return h;}
static void tracePair(const MyArc&a,const MyArc&b,const char*why,double value=-1){
 if(!traceOn)return;auto x=traceHash(a),y=traceHash(b);const unsigned long long watch[]={WATCH_VALUES};bool xok=false,yok=false;for(auto h:watch){xok|=x==h;yok|=y==h;}
 if(!(xok&&yok)||!tracePermit(0))return;
 std::ostringstream o;o<<"{\"event\":\"join-pair\",\"stage\":\""<<traceStage<<"\",\"left\":\""<<x<<"\",\"right\":\""<<y<<"\",\"reason\":\""<<why<<"\",\"value\":"<<value<<"}";traceWrite(o);
}
"""
pairhelper=pairhelper.replace('WATCH_VALUES',','.join(str(h)+'ULL' for h in a.watch_point_hashes))
pos=s.index('void EdgeDrawingImpl::traceGeometry');s=s[:pos]+pairhelper+s[pos:]
for k in [1,2,3]:
 start=s.index(f'void EdgeDrawingImpl::JoinArcs{k}()');end=s.index('\nvoid EdgeDrawingImpl::',start+10);chunk=s[start:end]
 first=chunk.index('            for (int j = i + 1; j < noArcs; j++)')
 last=chunk.index('            // Try to join',first)
 loop=chunk[first:last]
 loop=loop.replace('            {\n','            {\n                tracePair(arcs[i],arcs[j],"consider");\n',1)
 loop=re.sub(r'if \(([^\n]+)\)\n(\s+)continue;',lambda m:'if ('+m[1]+')\n'+m[2]+'{ tracePair(arcs[i],arcs[j],"'+m[1]+'"); continue; }',loop)
 chunk=chunk[:first]+loop+chunk[last:];s=s[:start]+chunk+s[end:]


reghelper=r"""
static void traceRegistration(const MyArc&a,int id,int before,int after,const char*exit){
 if(!tracePermit(0))return;std::ostringstream o;o<<std::setprecision(17)<<"{\"event\":\"registration\",\"arc\":"<<id<<",\"pointHash\":\""<<traceHash(a)<<"\",\"coverage\":"<<a.coverRatio<<",\"circleError\":"<<a.circleFitError<<",\"ellipseError\":"<<(a.isEllipse?a.ellipseFitError:-1)<<",\"added\":"<<after-before<<",\"exit\":\""<<exit<<"\"}";traceWrite(o);
}
"""
pos=s.index('void EdgeDrawingImpl::traceGeometry');s=s[:pos]+reghelper+s[pos:]
start=s.index('void EdgeDrawingImpl::GenerateCandidateCircles()');end=s.index('\nvoid EdgeDrawingImpl::DetectArcs()',start);chunk=s[start:end]
chunk=chunk.replace('    {\n        if (arcs[i].isEllipse)','    {\n        const int traceBefore=noCircles1;\n        if (arcs[i].isEllipse)',1)
for condition,label in [('CANDIDATE_CIRCLE_RATIO1','coverage<C1'),('CANDIDATE_CIRCLE_RATIO2','coverage<C2')]:
 needle=f'if (arcs[i].coverRatio < {condition})\n                continue;'
 assert needle in chunk
 chunk=chunk.replace(needle,f'if (arcs[i].coverRatio < {condition})\n                {{ traceRegistration(arcs[i],i,traceBefore,noCircles1,"{label}"); continue; }}',1)
chunk=chunk.replace('                continue;\n            }','                traceRegistration(arcs[i],i,traceBefore,noCircles1,"circle-accepted");\n                continue;\n            }',1)
pos=chunk.rfind('    }\n}');chunk=chunk[:pos]+'        traceRegistration(arcs[i],i,traceBefore,noCircles1,"end");\n'+chunk[pos:];s=s[:start]+chunk+s[end:]
# The overlap value is only read at the same short-circuit rejection.
s=s.replace('"angles.overlap(arcs[j].sTheta, arcs[j].eTheta) >= 0.50"); continue;', '"angles.overlap(arcs[j].sTheta, arcs[j].eTheta) >= 0.50",angles.overlap(arcs[j].sTheta, arcs[j].eTheta)); continue;')

(O/'trace.cpp').write_text(prefix+s+main)
(O/'generation.json').write_text(json.dumps(dict(source=str(a.source),sourceSha256=hashlib.sha256(a.source.read_bytes()).hexdigest(),traceSha256=hashlib.sha256((O/'trace.cpp').read_bytes()).hexdigest(),plainSha256=hashlib.sha256((O/'plain.cpp').read_bytes()).hexdigest()),indent=2))
print('generated',O)
