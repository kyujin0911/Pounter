#pragma once
#include <cstddef>
#include <new>
#include <utility>

#ifdef POUNTER_PRODUCTION
namespace pounter_ed {
template<class T> T* array(std::size_t n) { return new T[n]; }
template<class T, class... A> T* object(A&&... a) { return new T(std::forward<A>(a)...); }
template<class T> void freeArray(T* p) { delete[] p; }
template<class T> void freeObject(T* p) { delete p; }
}
#else
// Evaluation-only deterministic allocation failure; no OS OOM or peak claim.
namespace pounter_ed {
inline thread_local int faultAt = -1;
inline thread_local int attempts = 0;
inline thread_local bool auditing = false;
inline thread_local int live = 0;
inline void checkpoint() { if (auditing && ++attempts == faultAt) throw std::bad_alloc(); }
template<class T> T* array(std::size_t n) { checkpoint(); auto p = new T[n]; ++live; return p; }
template<class T, class... A> T* object(A&&... a) { checkpoint(); auto p = new T(std::forward<A>(a)...); ++live; return p; }
template<class T> void freeArray(T* p) { if (p) { delete[] p; --live; } }
template<class T> void freeObject(T* p) { if (p) { delete p; --live; } }
}

#endif

#include <memory>
#include <cstring>
namespace pounter_ed {
template<class T> struct ArrayDeleter { void operator()(T* p) const { freeArray(p); } };
template<class T> struct ObjectDeleter { void operator()(T* p) const { freeObject(p); } };
template<class T> using ArrayOwner = std::unique_ptr<T[],ArrayDeleter<T>>;
template<class T> using ObjectOwner = std::unique_ptr<T,ObjectDeleter<T>>;
template<class T> ArrayOwner<T> ownedArray(std::size_t n) { return ArrayOwner<T>(array<T>(n)); }
template<class T, class... A> ObjectOwner<T> ownedObject(A&&... a) { return ObjectOwner<T>(object<T>(std::forward<A>(a)...)); }
struct MatrixDeleter {
    int rows;
    void operator()(double** p) const {
        if (!p) return;
        for(int i=0;i<rows;i++) freeArray(p[i]);
        freeArray(p);
    }
};
// Preserve the upstream double** arithmetic while owning every partially allocated row.
class MatrixOwner {
    std::unique_ptr<double*[],MatrixDeleter> data;
public:
    MatrixOwner(int rows,int columns):data(array<double*>(rows),MatrixDeleter{rows}) {
        for(int i=0;i<rows;i++) data[i]=nullptr;
        for(int i=0;i<rows;i++) {
            data[i]=array<double>(columns);
            std::memset(data[i],0,sizeof(double)*columns);
        }
    }
    operator double**() const { return data.get(); }
    double* operator[](int i) const { return data[i]; }
};
}
