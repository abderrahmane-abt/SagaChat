#include "integrity.h"
#include "memory_guard.h"

#include <openssl/sha.h>
#include <openssl/crypto.h>

#include <cstdio>
#include <cstring>
#include <fstream>
#include <sys/ptrace.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>

#include <android/log.h>

#define LOG_TAG "HXS_Integrity"

namespace hxs {

bool IntegrityGuard::is_debugger_attached() {
    // Method 1: Check /proc/self/status for TracerPid
    FILE* f = fopen("/proc/self/status", "r");
    if (!f) return false;

    char line[256];
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            int pid = atoi(line + 10);
            fclose(f);
            return pid != 0;
        }
    }
    fclose(f);
    return false;
}

bool IntegrityGuard::block_debugger() {
    // ptrace self-trace: if another debugger is already attached, this fails
    long ret = ptrace(PTRACE_TRACEME, 0, nullptr, nullptr);
    if (ret == -1) {
        return false; // debugger already attached
    }
    return true;
}

bool IntegrityGuard::is_frida_present() {
    // Method 1: Scan /proc/self/maps for frida signatures
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) return false;

    char line[512];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "frida") ||
            strstr(line, "gadget") ||
            strstr(line, "linjector")) {
            found = true;
            break;
        }
    }
    fclose(f);
    if (found) return true;

    // Method 2: Check for default Frida server port (27042)
    char path[64];
    snprintf(path, sizeof(path), "/proc/net/tcp");
    f = fopen(path, "r");
    if (!f) return false;

    while (fgets(line, sizeof(line), f)) {
        // Frida default port 27042 = 0x69A2
        if (strstr(line, "69A2") || strstr(line, "69a2")) {
            found = true;
            break;
        }
    }
    fclose(f);
    if (found) return true;

    // Method 3: Check /proc/self/fd for Frida named pipes
    DIR* dir = opendir("/proc/self/fd");
    if (!dir) return false;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        char fd_path[128];
        char link_target[256];
        snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%s", entry->d_name);
        ssize_t len = readlink(fd_path, link_target, sizeof(link_target) - 1);
        if (len > 0) {
            link_target[len] = '\0';
            if (strstr(link_target, "frida") || strstr(link_target, "linjector")) {
                closedir(dir);
                return true;
            }
        }
    }
    closedir(dir);

    return false;
}

bool IntegrityGuard::is_xposed_present() {
    // Check /proc/self/maps for Xposed/LSPosed libraries
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) return false;

    char line[512];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "xposed") ||
            strstr(line, "XposedBridge") ||
            strstr(line, "lspd") ||
            strstr(line, "LSPosed") ||
            strstr(line, "edxp") ||
            strstr(line, "riru")) {
            found = true;
            break;
        }
    }
    fclose(f);
    return found;
}

bool IntegrityGuard::verify_apk_signature(
    const uint8_t* expected_hash, size_t expected_len,
    const uint8_t* actual_hash, size_t actual_len
) {
    if (expected_len != 32 || actual_len != 32) return false;
    return CRYPTO_memcmp(expected_hash, actual_hash, 32) == 0;
}

bool IntegrityGuard::hash_file(const char* path, uint8_t* out_32) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;

    SHA256_CTX ctx;
    SHA256_Init(&ctx);

    uint8_t buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        SHA256_Update(&ctx, buf, n);
    }

    fclose(f);
    SHA256_Final(out_32, &ctx);
    return true;
}

IntegrityResult IntegrityGuard::run_all_checks(
    const uint8_t* expected_sig_hash,
    const uint8_t* actual_sig_hash
) {
    IntegrityResult result{};
    result.debugger_detected = is_debugger_attached();
    result.frida_detected = is_frida_present();
    result.xposed_detected = is_xposed_present();
    result.signature_valid = (expected_sig_hash && actual_sig_hash)
        ? verify_apk_signature(expected_sig_hash, 32, actual_sig_hash, 32)
        : false;
    result.libs_intact = true; // caller must verify individual .so hashes
    return result;
}

} // namespace hxs
