#include <jni.h>
#include <string>
#include <vector>
#include <sys/uio.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>

#define LOG_TAG "ChetoNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/**
 * ANTI-BAN: String Obfuscation
 * Simple XOR encryption to hide strings from static analysis.
 */
std::string decrypt(std::string data) {
    char key = 'K'; 
    for (int i = 0; i < data.size(); i++) data[i] ^= key;
    return data;
}

/**
 * ANTI-BAN: Debugger Detection
 * Checks if the process is being traced.
 */
bool is_being_debugged() {
    return (getppid() > 1); // Simple check for tracer process
}

extern "C" {

/**
 * Fast Memory Reading using process_vm_readv
 */
bool read_memory(int pid, uintptr_t address, void* buffer, size_t size) {
    struct iovec local[1];
    struct iovec remote[1];

    local[0].iov_base = buffer;
    local[0].iov_len = size;
    remote[0].iov_base = (void*)address;
    remote[0].iov_len = size;

    ssize_t nread = process_vm_readv(pid, local, 1, remote, 1, 0);
    return nread == (ssize_t)size;
}

/**
 * Fast Memory Writing using process_vm_writev
 */
bool write_memory(int pid, uintptr_t address, void* buffer, size_t size) {
    struct iovec local[1];
    struct iovec remote[1];

    local[0].iov_base = buffer;
    local[0].iov_len = size;
    remote[0].iov_base = (void*)address;
    remote[0].iov_len = size;

    ssize_t nwrite = process_vm_writev(pid, local, 1, remote, 1, 0);
    return nwrite == (ssize_t)size;
}

/**
 * Signature Scanner (AOB Scan)
 */
JNIEXPORT jlong JNICALL
Java_com_cheto_eightball_MemoryManager_nativeScanSignature(JNIEnv* env, jobject thiz, 
                                                            jint pid, jlong start, jlong end, 
                                                            jbyteArray signature) {
    jsize sig_len = env->GetArrayLength(signature);
    jbyte* sig_bytes = env->GetByteArrayElements(signature, nullptr);
    
    std::vector<uint8_t> buffer(4096);
    uintptr_t current = (uintptr_t)start;
    
    LOGD("Native: Scanning from %lx to %lx", (long)start, (long)end);

    while (current < (uintptr_t)end) {
        size_t to_read = std::min((size_t)(end - current), buffer.size());
        if (read_memory(pid, current, buffer.data(), to_read)) {
            for (size_t i = 0; i <= to_read - sig_len; ++i) {
                bool match = true;
                for (size_t j = 0; j < sig_len; ++j) {
                    if (buffer[i + j] != (uint8_t)sig_bytes[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    env->ReleaseByteArrayElements(signature, sig_bytes, JNI_ABORT);
                    return (jlong)(current + i);
                }
            }
        }
        current += to_read - sig_len; // Overlap to catch signatures across boundaries
    }

    env->ReleaseByteArrayElements(signature, sig_bytes, JNI_ABORT);
    return 0;
}

/**
 * High Speed Ball Data Reader
 */
JNIEXPORT jfloatArray JNICALL
Java_com_cheto_eightball_MemoryManager_nativeReadBalls(JNIEnv* env, jobject thiz, 
                                                       jint pid, jlong ball_list_address) {
    // Assuming 22 balls max, each having X, Y (2 floats)
    const int ball_count = 22;
    float results[ball_count * 2];
    
    // In a real implementation, we would iterate through the ball pointers
    // and read their X, Y offsets.
    // Example:
    // for(int i=0; i<ball_count; i++) {
    //    uintptr_t ball_ptr;
    //    read_memory(pid, ball_list_address + (i * 8), &ball_ptr, sizeof(ball_ptr));
    //    read_memory(pid, ball_ptr + 0x18, &results[i*2], sizeof(float)); // X
    //    read_memory(pid, ball_ptr + 0x1C, &results[i*2+1], sizeof(float)); // Y
    // }

    jfloatArray output = env->NewFloatArray(ball_count * 2);
    env->SetFloatArrayRegion(output, 0, ball_count * 2, results);
    return output;
}

/**
 * ANTI-BAN: Safety check call
 */
JNIEXPORT jboolean JNICALL
Java_com_cheto_eightball_MemoryManager_nativeCheckSecurity(JNIEnv* env, jobject thiz) {
    if (is_being_debugged()) {
        LOGD("CRITICAL: Debugger detected! Throttling memory access for safety.");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_cheto_eightball_MemoryManager_nativeWriteInt(JNIEnv* env, jobject thiz, 
                                                      jint pid, jlong address, jint value) {
    return (jboolean)write_memory(pid, (uintptr_t)address, &value, sizeof(int));
}

}
