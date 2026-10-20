#include "ownership_test.hpp"
#include "precomp.hpp"
#include <jni.h>
#include <vector>
#include <stdexcept>
#include <new>
// No native handle leaves this call. Mat/Ptr are destroyed on success and exceptions.
extern "C" JNIEXPORT jdoubleArray JNICALL Java_io_github_kyujin0911_pounter_data_detector_EdgeDrawingNative_detect(JNIEnv* env, jclass, jbyteArray pixels, jint width, jint height) {
    try {
        if (!pixels || width < 8 || height < 8 || (int64_t)width*height > 4000000 || env->GetArrayLength(pixels) != (int64_t)width*height)
            throw std::invalid_argument("Invalid grayscale pixels");
        cv::Mat gray(height,width,CV_8UC1);
        env->GetByteArrayRegion(pixels,0,width*height,reinterpret_cast<jbyte*>(gray.data));
        if (env->ExceptionCheck()) return nullptr;
        auto detector=cv::ximgproc::createEdgeDrawing();
        detector->params.NFAValidation=true;detector->params.PFmode=false;
        detector->detectEdges(gray);
        cv::Mat shapes;detector->detectEllipses(shapes);
        std::vector<double> values;
        for (int i=0;i<shapes.rows;i++) {
            cv::Vec6d s=shapes.at<cv::Vec6d>(i);
            for(int j=0;j<6;j++) values.push_back(s[j]);
        }
        jdoubleArray result=env->NewDoubleArray(values.size());
        if (result) env->SetDoubleArrayRegion(result,0,values.size(),values.data());
        return result;
    } catch (const std::bad_alloc& failure) {
        env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"),failure.what());return nullptr;
    } catch (const std::invalid_argument& failure) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),failure.what());return nullptr;
    } catch (const std::exception& failure) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),failure.what());return nullptr;
    } catch (...) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),"Unknown native failure");return nullptr;
    }
}
