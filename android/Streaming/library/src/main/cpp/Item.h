//
// Created by wlanjie on 2016/12/24.
//

#ifndef STREAMING_QUEUE_H
#define STREAMING_QUEUE_H


class Queue {
private:
    char *data;
    int size;
    int packet_type;
    int pts;
public:
    void set_data(char *data);
    void set_size(int size);
    void set_packet_type(int packet_type);
    void set_pts(int pts);

public:
    char* get_data();
    int get_size();
    int get_packet_type();
    int get_pts();
};


#endif //STREAMING_QUEUE_H
