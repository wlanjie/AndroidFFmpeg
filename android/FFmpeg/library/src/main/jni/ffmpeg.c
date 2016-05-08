#include "ffmpeg.h"

InputFile *input_file = NULL;
InputStream **input_streams = NULL;
int nb_input_streams = 0;
OutputFile *output_file = NULL;
OutputStream **output_streams = NULL;
int nb_output_streams = 0;

void *grow_array(void *array, int elem_size, int *size, int new_size) {
    if (new_size > INT_MAX / elem_size) {
        av_log(NULL, AV_LOG_ERROR, "Array to big.\n");
        return NULL;
    }
    if (*size < new_size) {
        uint8_t *tmp = av_realloc_array(array, (size_t) elem_size, (size_t) new_size);
        memset(tmp + *size * elem_size, 0, (size_t) ((new_size - *size) * elem_size));
        *size = new_size;
        return tmp;
    }
    return array;
}

double get_rotation(AVStream *st) {
    AVDictionaryEntry *rotate = av_dict_get(st->metadata, "rotate", NULL, 0);
    uint8_t *displaymatrix = av_stream_get_side_data(st, AV_PKT_DATA_DISPLAYMATRIX, NULL);
    double theta = 0;
    if (rotate && *rotate->value && strcmp(rotate->value, "0")) {
        char *tail;
        theta = av_strtod(rotate->value, &tail);
        if (*tail) {
            theta = 0;
        }
    }
    if (displaymatrix && !theta) {
        theta = -av_display_rotation_get((int32_t *) displaymatrix);
    }
    theta -= 360 * floor(theta / 360 + 0.9 / 360);
    if (fabs(theta - 90 * round(theta / 90)) > 2) {
        av_log(NULL, AV_LOG_WARNING, "Odd rotation angle.\n"
                "If you want to help, upload a sample "
                "of this file to ftp://upload.ffmpeg.org/incoming/ "
                "and contact the ffmpeg-devel mailing list. (ffmpeg-devel@ffmpeg.org)");
    }
    return theta;
}