//
// Created by wlanjie on 2016/12/24.
//

#include "Item.h"

void Queue::set_data(char *data) {
    this->data = data;
}

void Queue::set_size(int size) {
    this->size = size;
}

void Queue::set_packet_type(int packet_type) {
    this->packet_type = packet_type;
}

void Queue::set_pts(int pts) {
    this->pts = pts;
}

char *Queue::get_data() {
    return nullptr;
}

int Queue::get_size() {
    return this->size;
}

int Queue::get_packet_type() {
    return this->packet_type;
}

int Queue::get_pts() {
    return this->pts;
}















