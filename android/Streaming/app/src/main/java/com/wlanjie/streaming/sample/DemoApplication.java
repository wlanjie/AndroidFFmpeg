package com.wlanjie.streaming.sample;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

/**
 * Created by wlanjie on 2016/12/2.
 */

public class DemoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();


        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);
    }
}
