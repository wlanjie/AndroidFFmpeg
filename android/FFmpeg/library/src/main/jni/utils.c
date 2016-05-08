//
// Created by wlanjie on 16/5/5.
//

#include "utils.h"

int check_file_exist(JNIEnv *env, const char *input) {
    if (access(input, 4) == -1) {
        if ((*env)->ExceptionCheck) {
            (*env)->ExceptionClear(env);
            jclass file_not_found_class = (*env)->FindClass(env, "java/io/FileNotFoundException");
            char error[255];
            snprintf(error, sizeof(error), "%s not found\n", input);
            (*env)->ThrowNew(env, file_not_found_class, error);
            (*env)->DeleteLocalRef(env, file_not_found_class);
        }
        return -1;
    }
    return 0;
}