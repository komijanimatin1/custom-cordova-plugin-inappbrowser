/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.inappbrowser;

import android.app.Dialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.content.res.Configuration;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Oliver on 22/11/2013.
 */
public class InAppBrowserDialog extends Dialog {
    Context context;
    InAppBrowser inAppBrowser = null;
    private GestureDetector gestureDetector;

    public InAppBrowserDialog(Context context, int theme) {
        super(context, theme);
        this.context = context;
        
        // Initialize gesture detector to handle edge swipes
        gestureDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Detect left-to-right swipe (back gesture)
                if (e1 != null && e2 != null) {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    
                    // If it's a horizontal swipe from left edge (back gesture)
                    if (Math.abs(diffX) > Math.abs(diffY) && diffX > 100 && e1.getX() < 50) {
                        if (inAppBrowser != null && inAppBrowser.hardwareBack() && inAppBrowser.gesturesEnabled()) {
                            // Only allow back if both hardware back and gestures are enabled
                            if (inAppBrowser.canGoBack()) {
                                inAppBrowser.goBack();
                                return true;
                            } else {
                                inAppBrowser.closeDialog();
                                return true;
                            }
                        } else {
                            // Block back gesture if hardware back or gestures are disabled
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    public void setInAppBroswer(InAppBrowser browser) {
        this.inAppBrowser = browser;
    }

    @Override
    public void onBackPressed() {
        if (this.inAppBrowser == null) {
            this.dismiss();
        } else {
            // Check if hardware back is enabled
            if (this.inAppBrowser.hardwareBack()) {
                // Only allow back if hardware back is enabled
                if (this.inAppBrowser.canGoBack()) {
                    this.inAppBrowser.goBack();
                } else {
                    this.inAppBrowser.closeDialog();
                }
            } else {
                // If hardware back is disabled, do nothing
                // This prevents the dialog from closing
                return;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle hardware back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (this.inAppBrowser != null && (!this.inAppBrowser.hardwareBack() || !this.inAppBrowser.gesturesEnabled())) {
                // Block hardware back button if hardware back or gestures are disabled
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Handle touch events for gesture navigation
        if (inAppBrowser != null && inAppBrowser.gesturesEnabled() && gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Intercept touch events to handle gesture navigation
        if (inAppBrowser != null && inAppBrowser.gesturesEnabled() && gestureDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        // Handle configuration changes (like rotation)
    }
}
