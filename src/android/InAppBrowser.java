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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;
import android.provider.Browser;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.net.http.SslError;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.DownloadListener;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.ViewGroup;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaHttpAuthHandler;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.StringTokenizer;

@SuppressLint("SetJavaScriptEnabled")
public class InAppBrowser extends CordovaPlugin {

    private static final String NULL = "null";
    protected static final String LOG_TAG = "InAppBrowser";
    private static final String SELF = "_self";
    private static final String SYSTEM = "_system";
    private static final String EXIT_EVENT = "exit";
    private static final String LOCATION = "location";
    private static final String ZOOM = "zoom";
    private static final String HIDDEN = "hidden";
    private static final String LOAD_START_EVENT = "loadstart";
    private static final String LOAD_STOP_EVENT = "loadstop";
    private static final String LOAD_ERROR_EVENT = "loaderror";
    private static final String DOWNLOAD_EVENT = "download";
    private static final String MESSAGE_EVENT = "message";
    private static final String CLEAR_ALL_CACHE = "clearcache";
    private static final String CLEAR_SESSION_CACHE = "clearsessioncache";
    private static final String HARDWARE_BACK_BUTTON = "hardwareback";
    private static final String MEDIA_PLAYBACK_REQUIRES_USER_ACTION = "mediaPlaybackRequiresUserAction";
    private static final String SHOULD_PAUSE = "shouldPauseOnSuspend";
    private static final Boolean DEFAULT_HARDWARE_BACK = true;
    private static final String USER_WIDE_VIEW_PORT = "useWideViewPort";
    private static final String TOOLBAR_COLOR = "toolbarcolor";
    private static final String CLOSE_BUTTON_CAPTION = "closebuttoncaption";
    private static final String CLOSE_BUTTON_COLOR = "closebuttoncolor";
    private static final String LEFT_TO_RIGHT = "lefttoright";
    private static final String HIDE_NAVIGATION = "hidenavigationbuttons";
    private static final String NAVIGATION_COLOR = "navigationbuttoncolor";
    private static final String HIDE_URL = "hideurlbar";
    private static final String FOOTER = "footer";
    private static final String FOOTER_COLOR = "footercolor";
    private static final String FOOTER_TITLE = "footertitle";
    private static final String BEFORELOAD = "beforeload";
    private static final String FULLSCREEN = "fullscreen";
       private static final String INJECT_BUTTON = "injectbutton";
    private static final String INJECT_JS_CODE = "injectjscode";
    private static final String MENU_BUTTON = "menu";

    private static final int TOOLBAR_HEIGHT = 120;

    private static final List customizableOptions = Arrays.asList(CLOSE_BUTTON_CAPTION, TOOLBAR_COLOR, NAVIGATION_COLOR, CLOSE_BUTTON_COLOR, FOOTER_COLOR, FOOTER_TITLE);

    private InAppBrowserDialog dialog;
    private WebView inAppWebView;
    private EditText edittext;
    private CallbackContext callbackContext;
    private boolean showLocationBar = true;
    private boolean showZoomControls = true;
    private boolean openWindowHidden = false;
    private boolean clearAllCache = false;
    private boolean clearSessionCache = false;
    private boolean hadwareBackButton = true;
    private boolean mediaPlaybackRequiresUserGesture = false;
    private boolean shouldPauseInAppBrowser = false;
    private boolean useWideViewPort = true;
    private ValueCallback<Uri[]> mUploadCallback;
    private final static int FILECHOOSER_REQUESTCODE = 1;
    private String closeButtonCaption = "";
    private String closeButtonColor = "";
    private boolean leftToRight = false;
    private int toolbarColor = android.graphics.Color.LTGRAY;
    private boolean hideNavigationButtons = false;
    private String navigationButtonColor = "";
    private boolean hideUrlBar = false;
    private boolean showFooter = false;
    private String footerColor = "";
    private String footerTitle = "";
    private String beforeload = "";
    private boolean fullscreen = true;
       private boolean showInjectButton = false;
    private String injectJsCode = "";
    private boolean showMenuButton = false;
    private String[] allowedSchemes;
    private InAppBrowserClient currentClient;
    
    // Modal WebView for AI functionality
    private WebView modalWebView;
    private RelativeLayout modalContainer;
    private boolean isModalVisible = false;
    
    // Menu modal for three-dot menu
    private RelativeLayout menuModalContainer;
    private boolean isMenuModalVisible = false;

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action the action to execute.
     * @param args JSONArry of arguments for the plugin.
     * @param callbackContext the callbackContext used when calling back into JavaScript.
     * @return A PluginResult object with a status and message.
     */
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("open")) {
            this.callbackContext = callbackContext;
            final String url = args.getString(0);
            String t = args.optString(1);
            if (t == null || t.equals("") || t.equals(NULL)) {
                t = SELF;
            }
            final String target = t;
            final HashMap<String, String> features = parseFeature(args.optString(2));

            LOG.d(LOG_TAG, "target = " + target);

            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String result = "";
                    // SELF
                    if (SELF.equals(target)) {
                        LOG.d(LOG_TAG, "in self");
                        /* This code exists for compatibility between 3.x and 4.x versions of Cordova.
                         * Previously the Config class had a static method, isUrlWhitelisted(). That
                         * responsibility has been moved to the plugins, with an aggregating method in
                         * PluginManager.
                         */
                        Boolean shouldAllowNavigation = null;
                        if (url.startsWith("javascript:")) {
                            shouldAllowNavigation = true;
                        }
                        if (shouldAllowNavigation == null) {
                            try {
                                Method iuw = Config.class.getMethod("isUrlWhiteListed", String.class);
                                shouldAllowNavigation = (Boolean)iuw.invoke(null, url);
                            } catch (NoSuchMethodException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (IllegalAccessException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (InvocationTargetException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            }
                        }
                        if (shouldAllowNavigation == null) {
                            try {
                                Method gpm = webView.getClass().getMethod("getPluginManager");
                                PluginManager pm = (PluginManager)gpm.invoke(webView);
                                Method san = pm.getClass().getMethod("shouldAllowNavigation", String.class);
                                shouldAllowNavigation = (Boolean)san.invoke(pm, url);
                            } catch (NoSuchMethodException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (IllegalAccessException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            } catch (InvocationTargetException e) {
                                LOG.d(LOG_TAG, e.getLocalizedMessage());
                            }
                        }
                        // load in webview
                        if (Boolean.TRUE.equals(shouldAllowNavigation)) {
                            LOG.d(LOG_TAG, "loading in webview");
                            webView.loadUrl(url);
                        }
                        //Load the dialer
                        else if (url.startsWith(WebView.SCHEME_TEL))
                        {
                            try {
                                LOG.d(LOG_TAG, "loading in dialer");
                                Intent intent = new Intent(Intent.ACTION_DIAL);
                                intent.setData(Uri.parse(url));
                                cordova.getActivity().startActivity(intent);
                            } catch (android.content.ActivityNotFoundException e) {
                                LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
                            }
                        }
                        // load in InAppBrowser
                        else {
                            LOG.d(LOG_TAG, "loading in InAppBrowser");
                            result = showWebPage(url, features);
                        }
                    }
                    // SYSTEM
                    else if (SYSTEM.equals(target)) {
                        LOG.d(LOG_TAG, "in system");
                        result = openExternal(url);
                    }
                    // BLANK - or anything else
                    else {
                        LOG.d(LOG_TAG, "in blank");
                        result = showWebPage(url, features);
                    }

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                }
            });
        }
        else if (action.equals("close")) {
            closeDialog();
        }
        else if (action.equals("loadAfterBeforeload")) {
            if (beforeload == null) {
                LOG.e(LOG_TAG, "unexpected loadAfterBeforeload called without feature beforeload=yes");
            }
            final String url = args.getString(0);
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                        currentClient.waitForBeforeload = false;
                        inAppWebView.setWebViewClient(currentClient);
                    } else {
                        ((InAppBrowserClient)inAppWebView.getWebViewClient()).waitForBeforeload = false;
                    }
                    inAppWebView.loadUrl(url);

                }
            });
        }
        else if (action.equals("injectScriptCode")) {
            String jsWrapper = null;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(){prompt(JSON.stringify([eval(%%s)]), 'gap-iab://%s')})()", callbackContext.getCallbackId());
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectScriptFile")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('script'); c.src = %%s; c.onload = function() { prompt('', 'gap-iab://%s'); }; d.body.appendChild(c); })(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %s; d.body.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectStyleCode")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('style'); c.innerHTML = %%s; d.body.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %s; d.body.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectStyleFile")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %%s; d.head.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %s; d.head.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("show")) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (dialog != null && !cordova.getActivity().isFinishing()) {
                        dialog.show();
                    }
                }
            });
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            pluginResult.setKeepCallback(true);
            this.callbackContext.sendPluginResult(pluginResult);
        }
        else if (action.equals("hide")) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (dialog != null && !cordova.getActivity().isFinishing()) {
                        dialog.hide();
                    }
                }
            });
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            pluginResult.setKeepCallback(true);
            this.callbackContext.sendPluginResult(pluginResult);
        }
        else {
            return false;
        }
        return true;
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        if (isModalVisible) {
            hideModalWebView();
        }
        if (isMenuModalVisible) {
            hideMenuModal();
        }
        closeDialog();
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    public void onPause(boolean multitasking) {
        if (shouldPauseInAppBrowser) {
            inAppWebView.onPause();
        }
        if (isModalVisible && modalWebView != null) {
            modalWebView.onPause();
        }
        // Hide menu modal when app is paused
        if (isMenuModalVisible) {
            hideMenuModal();
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     */
    @Override
    public void onResume(boolean multitasking) {
        if (shouldPauseInAppBrowser) {
            inAppWebView.onResume();
        }
        if (isModalVisible && modalWebView != null) {
            modalWebView.onResume();
        }
    }

    /**
     * Called by AccelBroker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        if (isModalVisible) {
            hideModalWebView();
        }
        if (isMenuModalVisible) {
            hideMenuModal();
        }
        closeDialog();
    }

    /**
     * Inject an object (script or style) into the InAppBrowser WebView.
     *
     * This is a helper method for the inject{Script|Style}{Code|File} API calls, which
     * provides a consistent method for injecting JavaScript code into the document.
     *
     * If a wrapper string is supplied, then the source string will be JSON-encoded (adding
     * quotes) and wrapped using string formatting. (The wrapper string should have a single
     * '%s' marker)
     *
     * @param source      The source object (filename or script/style text) to inject into
     *                    the document.
     * @param jsWrapper   A JavaScript string to wrap the source string in, so that the object
     *                    is properly injected, or null if the source string is JavaScript text
     *                    which should be executed directly.
     */
    private void injectDeferredObject(String source, String jsWrapper) {
        if (inAppWebView!=null) {
            String scriptToInject;
            if (jsWrapper != null) {
                org.json.JSONArray jsonEsc = new org.json.JSONArray();
                jsonEsc.put(source);
                String jsonRepr = jsonEsc.toString();
                String jsonSourceString = jsonRepr.substring(1, jsonRepr.length()-1);
                scriptToInject = String.format(jsWrapper, jsonSourceString);
            } else {
                scriptToInject = source;
            }
            final String finalScriptToInject = scriptToInject;
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    inAppWebView.evaluateJavascript(finalScriptToInject, null);
                }
            });
        } else {
            LOG.d(LOG_TAG, "Can't inject code into the system browser");
        }
    }

    /**
     * Put the list of features into a hash map
     *
     * @param optString
     * @return
     */
    private HashMap<String, String> parseFeature(String optString) {
        if (optString.equals(NULL)) {
            return null;
        } else {
            HashMap<String, String> map = new HashMap<String, String>();
            StringTokenizer features = new StringTokenizer(optString, ",");
            StringTokenizer option;
            while(features.hasMoreElements()) {
                option = new StringTokenizer(features.nextToken(), "=");
                if (option.hasMoreElements()) {
                    String key = option.nextToken();
                    String value = option.nextToken();
                    if (!customizableOptions.contains(key)) {
                        value = value.equals("yes") || value.equals("no") ? value : "yes";
                    }
                    map.put(key, value);
                }
            }
            return map;
        }
    }

    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @return "" if ok, or error message.
     */
    public String openExternal(String url) {
        try {
            Intent intent = null;
            intent = new Intent(Intent.ACTION_VIEW);
            // Omitting the MIME type for file: URLs causes "No Activity found to handle Intent".
            // Adding the MIME type to http: URLs causes them to not be handled by the downloader.
            Uri uri = Uri.parse(url);
            if ("file".equals(uri.getScheme())) {
                intent.setDataAndType(uri, webView.getResourceApi().getMimeType(uri));
            } else {
                intent.setData(uri);
            }
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, cordova.getActivity().getPackageName());
            // CB-10795: Avoid circular loops by preventing it from opening in the current app
            this.openExternalExcludeCurrentApp(intent);
            return "";
            // not catching FileUriExposedException explicitly because buildtools<24 doesn't know about it
        } catch (java.lang.RuntimeException e) {
            LOG.d(LOG_TAG, "InAppBrowser: Error loading url "+url+":"+ e.toString());
            return e.toString();
        }
    }

    /**
     * Opens the intent, providing a chooser that excludes the current app to avoid
     * circular loops.
     */
    private void openExternalExcludeCurrentApp(Intent intent) {
        String currentPackage = cordova.getActivity().getPackageName();
        boolean hasCurrentPackage = false;

        PackageManager pm = cordova.getActivity().getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        ArrayList<Intent> targetIntents = new ArrayList<Intent>();

        for (ResolveInfo ri : activities) {
            if (!currentPackage.equals(ri.activityInfo.packageName)) {
                Intent targetIntent = (Intent)intent.clone();
                targetIntent.setPackage(ri.activityInfo.packageName);
                targetIntents.add(targetIntent);
            }
            else {
                hasCurrentPackage = true;
            }
        }

        // If the current app package isn't a target for this URL, then use
        // the normal launch behavior
        if (hasCurrentPackage == false || targetIntents.size() == 0) {
            this.cordova.getActivity().startActivity(intent);
        }
        // If there's only one possible intent, launch it directly
        else if (targetIntents.size() == 1) {
            this.cordova.getActivity().startActivity(targetIntents.get(0));
        }
        // Otherwise, show a custom chooser without the current app listed
        else if (targetIntents.size() > 0) {
            Intent chooser = Intent.createChooser(targetIntents.remove(targetIntents.size()-1), null);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray(new Parcelable[] {}));
            this.cordova.getActivity().startActivity(chooser);
        }
    }

    /**
     * Closes the dialog
     */
    public void closeDialog() {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Hide modal if it's visible
                if (isModalVisible) {
                    hideModalWebView();
                }
                
                // Hide menu modal if it's visible
                if (isMenuModalVisible) {
                    hideMenuModal();
                }
                
                final WebView childView = inAppWebView;
                // The JS protects against multiple calls, so this should happen only when
                // closeDialog() is called by other native code.
                if (childView == null) {
                    return;
                }

                childView.setWebViewClient(new WebViewClient() {
                    // NB: wait for about:blank before dismissing
                    public void onPageFinished(WebView view, String url) {
                        if (dialog != null && !cordova.getActivity().isFinishing()) {
                            dialog.dismiss();
                            dialog = null;
                        }
                    }
                });
                // NB: From SDK 19: "If you call methods on WebView from any thread
                // other than your app's UI thread, it can cause unexpected results."
                // http://developer.android.com/guide/webapps/migrating.html#Threads
                childView.loadUrl("about:blank");

                try {
                    JSONObject obj = new JSONObject();
                    obj.put("type", EXIT_EVENT);
                    sendUpdate(obj, false);
                } catch (JSONException ex) {
                    LOG.d(LOG_TAG, "Should never happen");
                }
            }
        });
    }

    /**
     * Checks to see if it is possible to go back one page in history, then does so.
     */
    public void goBack() {
        if (this.inAppWebView.canGoBack()) {
            this.inAppWebView.goBack();
        }
    }

    /**
     * Can the web browser go back?
     * @return boolean
     */
    public boolean canGoBack() {
        return this.inAppWebView.canGoBack();
    }

    /**
     * Show modal WebView for AI functionality
     */
    private void showModalWebView() {
        if (isModalVisible || dialog == null) {
            return;
        }

        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Create modal container
                modalContainer = new RelativeLayout(cordova.getActivity());
                modalContainer.setBackgroundColor(Color.parseColor("#80000000")); // Semi-transparent background
                
                // Set layout parameters for modal container (positioned absolutely over the main content)
                RelativeLayout.LayoutParams modalContainerParams = new RelativeLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                );
                modalContainer.setLayoutParams(modalContainerParams);
                
                // Create modal WebView container with specific dimensions
                RelativeLayout modalWebViewContainer = new RelativeLayout(cordova.getActivity());
                
                // Calculate 80% width and 60% height of screen (increased size)
                int screenWidth = cordova.getActivity().getResources().getDisplayMetrics().widthPixels;
                int screenHeight = cordova.getActivity().getResources().getDisplayMetrics().heightPixels;
                int modalWidth = (int) (screenWidth * 0.8); // 80% of screen width
                int modalHeight = (int) (screenHeight * 0.6); // 60% of screen height
                
                // Set layout parameters for modal WebView container
                RelativeLayout.LayoutParams modalWebViewContainerParams = new RelativeLayout.LayoutParams(
                    modalWidth,
                    modalHeight
                );
                // Center the modal
                modalWebViewContainerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                modalWebViewContainer.setLayoutParams(modalWebViewContainerParams);
                
                // Add rounded corners and background to modal container
                android.graphics.drawable.GradientDrawable modalBackground = new android.graphics.drawable.GradientDrawable();
                modalBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                modalBackground.setCornerRadius(dpToPixels(16)); // Rounded corners
                modalBackground.setColor(Color.WHITE); // White background
                modalWebViewContainer.setBackground(modalBackground);
                
                // Remove padding to eliminate white area around WebView
                modalWebViewContainer.setPadding(0, 0, 0, 0);
                
                // Create modal WebView
                modalWebView = new WebView(cordova.getActivity());
                modalWebView.setLayoutParams(new RelativeLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                ));
                
                // Configure modal WebView settings
                WebSettings modalSettings = modalWebView.getSettings();
                modalSettings.setJavaScriptEnabled(true);
                modalSettings.setJavaScriptCanOpenWindowsAutomatically(true);
                modalSettings.setBuiltInZoomControls(true);
                modalSettings.setDisplayZoomControls(false);
                modalSettings.setLoadWithOverviewMode(true);
                modalSettings.setUseWideViewPort(true);
                modalSettings.setDomStorageEnabled(true);
                
                // Set WebViewClient for modal
                modalWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                    }
                    
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        // Handle external links in modal
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            view.loadUrl(url);
                            return true;
                        }
                        return false;
                    }
                });
                
                // Add WebView to modal WebView container
                modalWebViewContainer.addView(modalWebView);
                
                // Add modal WebView container to modal container
                modalContainer.addView(modalWebViewContainer);
                
                // Add click listener to background to close modal when clicking outside
                modalContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideModalWebView();
                    }
                });
                
                // Prevent clicks on the modal WebView container from closing the modal
                modalWebViewContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Do nothing - prevent event from bubbling up to modalContainer
                    }
                });
                
                // Add modal container to the main dialog
                if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
                    View decorView = dialog.getWindow().getDecorView();
                    if (decorView instanceof ViewGroup) {
                        ((ViewGroup) decorView).addView(modalContainer);
                        
                        // Disable interaction with background elements when modal is open
                        if (inAppWebView != null) {
                            inAppWebView.setEnabled(false);
                        }
                        
                        // Disable footer buttons when modal is open
                        disableFooterInteraction();
                    }
                }
                
                // Load Google.com in modal WebView
                modalWebView.loadUrl("https://google.com");
                
                isModalVisible = true;
            }
        });
    }

    /**
     * Convert DIP units to Pixels (helper method for modal)
     */
    private int dpToPixels(int dipValue) {
        int value = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (float) dipValue,
            cordova.getActivity().getResources().getDisplayMetrics()
        );
        return value;
    }

    /**
     * Hide modal WebView
     */
    private void hideModalWebView() {
        if (!isModalVisible || modalContainer == null) {
            return;
        }

        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (modalContainer.getParent() != null) {
                    ((ViewGroup) modalContainer.getParent()).removeView(modalContainer);
                }
                modalContainer = null;
                modalWebView = null;
                isModalVisible = false;
                
                // Re-enable interaction with background elements when modal is closed
                if (inAppWebView != null) {
                    inAppWebView.setEnabled(true);
                }
                
                // Re-enable footer buttons when modal is closed
                enableFooterInteraction();
            }
        });
    }

    /**
     * Show menu modal for three-dot menu
     */
    private void showMenuModal() {
        if (isMenuModalVisible || dialog == null) {
            return;
        }

        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Create menu modal container
                menuModalContainer = new RelativeLayout(cordova.getActivity());
                menuModalContainer.setBackgroundColor(Color.parseColor("#80000000")); // Semi-transparent background
                
                // Set layout parameters for menu modal container
                RelativeLayout.LayoutParams menuModalContainerParams = new RelativeLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                );
                menuModalContainer.setLayoutParams(menuModalContainerParams);
                
                // Create menu container with specific dimensions
                LinearLayout menuContainer = new LinearLayout(cordova.getActivity());
                menuContainer.setOrientation(LinearLayout.VERTICAL);
                
                // Calculate menu size (positioned above footer)
                int screenWidth = cordova.getActivity().getResources().getDisplayMetrics().widthPixels;
                int menuWidth = (int) (screenWidth * 0.6); // 60% of screen width
                int menuHeight = LayoutParams.WRAP_CONTENT;
                
                // Set layout parameters for menu container
                RelativeLayout.LayoutParams menuContainerParams = new RelativeLayout.LayoutParams(
                    menuWidth,
                    menuHeight
                );
                // Position above footer (bottom-right area)
                menuContainerParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                menuContainerParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                menuContainerParams.setMargins(0, 0, dpToPixels(16), dpToPixels(TOOLBAR_HEIGHT + 32)); // Above footer
                menuContainer.setLayoutParams(menuContainerParams);
                
                // Add rounded corners and background to menu container
                android.graphics.drawable.GradientDrawable menuBackground = new android.graphics.drawable.GradientDrawable();
                menuBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                menuBackground.setCornerRadius(dpToPixels(12)); // Rounded corners
                menuBackground.setColor(Color.WHITE); // White background
                menuContainer.setBackground(menuBackground);
                
                // Add shadow effect
                menuContainer.setElevation(dpToPixels(8));
                
                // Add padding to menu container
                menuContainer.setPadding(dpToPixels(8), dpToPixels(8), dpToPixels(8), dpToPixels(8));
                
                // Create menu items - Forward and Refresh
                String[] menuItems = {"Forward", "Refresh"};
                for (String item : menuItems) {
                    TextView menuItem = new TextView(cordova.getActivity());
                    menuItem.setText(item);
                    menuItem.setTextColor(Color.BLACK);
                    menuItem.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    menuItem.setPadding(dpToPixels(16), dpToPixels(12), dpToPixels(16), dpToPixels(12));
                    menuItem.setGravity(Gravity.CENTER_VERTICAL);
                    
                    // Add click effect
                    menuItem.setBackgroundResource(android.R.drawable.list_selector_background);
                    
                    // Add click listener
                    menuItem.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Handle menu item click
                            String clickedItem = ((TextView) v).getText().toString();
                            handleMenuItemClick(clickedItem);
                            hideMenuModal();
                        }
                    });
                    
                    menuContainer.addView(menuItem);
                    
                    // Add separator (except for last item)
                    if (!item.equals(menuItems[menuItems.length - 1])) {
                        View separator = new View(cordova.getActivity());
                        separator.setBackgroundColor(Color.parseColor("#E0E0E0"));
                        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            1
                        );
                        separator.setLayoutParams(separatorParams);
                        menuContainer.addView(separator);
                    }
                }
                
                // Add menu container to modal container
                menuModalContainer.addView(menuContainer);
                
                // Add click listener to background to close menu
                menuModalContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideMenuModal();
                    }
                });
                
                // Add modal container to the main dialog
                if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
                    View decorView = dialog.getWindow().getDecorView();
                    if (decorView instanceof ViewGroup) {
                        ((ViewGroup) decorView).addView(menuModalContainer);
                    }
                }
                
                isMenuModalVisible = true;
            }
        });
    }

    /**
     * Handle menu item click
     */
    private void handleMenuItemClick(String item) {
        // You can implement specific actions for each menu item here
        LOG.d(LOG_TAG, "Menu item clicked: " + item);
        
        // Handle menu item actions
        switch (item) {
            case "Forward":
                // Navigate forward in WebView
                if (inAppWebView != null && inAppWebView.canGoForward()) {
                    inAppWebView.goForward();
                }
                break;
            case "Refresh":
                // Refresh the WebView
                if (inAppWebView != null) {
                    inAppWebView.reload();
                }
                break;
        }
    }

    /**
     * Hide menu modal
     */
    private void hideMenuModal() {
        if (!isMenuModalVisible || menuModalContainer == null) {
            return;
        }

        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (menuModalContainer.getParent() != null) {
                    ((ViewGroup) menuModalContainer.getParent()).removeView(menuModalContainer);
                }
                menuModalContainer = null;
                isMenuModalVisible = false;
            }
        });
    }

    /**
     * Disable footer interaction when modal is open
     */
    private void disableFooterInteraction() {
        if (dialog != null && dialog.getWindow() != null) {
            View decorView = dialog.getWindow().getDecorView();
            disableFooterInteractionRecursively(decorView, true);
        }
    }

    /**
     * Enable footer interaction when modal is closed
     */
    private void enableFooterInteraction() {
        if (dialog != null && dialog.getWindow() != null) {
            View decorView = dialog.getWindow().getDecorView();
            disableFooterInteractionRecursively(decorView, false);
        }
    }

    /**
     * Recursively disable/enable footer interaction
     */
    private void disableFooterInteractionRecursively(View view, boolean disable) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                disableFooterInteractionRecursively(viewGroup.getChildAt(i), disable);
            }
        } else if (view instanceof Button || view instanceof ImageButton || view instanceof TextView) {
            // Check if this is a footer button by looking at its text, ID, or content description
            String text = "";
            String contentDesc = "";
            
            if (view instanceof TextView) {
                text = ((TextView) view).getText().toString();
            }
            if (view.getContentDescription() != null) {
                contentDesc = view.getContentDescription().toString();
            }
            
            // Disable/enable footer buttons (AI button, menu button, close button)
            if (text.equals("AI") || text.equals("Close") || text.equals("Back") || 
                contentDesc.equals("Menu Button") || contentDesc.equals("Close Button")) {
                view.setEnabled(!disable);
                view.setClickable(!disable);
            }
        }
    }



    /**
     * Has the user set the hardware back button to go back
     * @return boolean
     */
    public boolean hardwareBack() {
        return hadwareBackButton;
    }

    /**
     * Checks to see if it is possible to go forward one page in history, then does so.
     */
    private void goForward() {
        if (this.inAppWebView.canGoForward()) {
            this.inAppWebView.goForward();
        }
    }

    /**
     * Navigate to the new page
     *
     * @param url to load
     */
    private void navigate(String url) {
        InputMethodManager imm = (InputMethodManager)this.cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(edittext.getWindowToken(), 0);

        if (!url.startsWith("http") && !url.startsWith("file:")) {
            this.inAppWebView.loadUrl("http://" + url);
        } else {
            this.inAppWebView.loadUrl(url);
        }
        this.inAppWebView.requestFocus();
    }


    /**
     * Should we show the location bar?
     *
     * @return boolean
     */
    private boolean getShowLocationBar() {
        return this.showLocationBar;
    }

    private InAppBrowser getInAppBrowser() {
        return this;
    }

    /**
     * Update close button text based on navigation state
     */
    private void updateCloseButtonText() {
        if (dialog != null && dialog.getWindow() != null) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Find close buttons in the dialog and update their text
                    View decorView = dialog.getWindow().getDecorView();
                    updateButtonTextRecursively(decorView);
                }
            });
        }
    }

    /**
     * Recursively find and update close button text
     */
    private void updateButtonTextRecursively(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                updateButtonTextRecursively(viewGroup.getChildAt(i));
            }
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            // Check if this is a close button by looking at its text or ID
            if (textView.getText().toString().equals("Back") || 
                textView.getText().toString().equals("Close") ||
                textView.getContentDescription() != null && 
                textView.getContentDescription().toString().equals("Close Button")) {
                
                // Only show "Back" if we can actually go back and we're not on the initial page
                String newText = "Close";
                if (inAppWebView != null && inAppWebView.canGoBack() && inAppWebView.getUrl() != null && !inAppWebView.getUrl().equals("about:blank")) {
                    newText = "Back";
                }
                textView.setText(newText);
            }
        }
    }

    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @param features jsonObject
     */
    public String showWebPage(final String url, HashMap<String, String> features) {
        // Determine if we should hide the location bar.
        showLocationBar = true;
        showZoomControls = true;
        openWindowHidden = false;
        mediaPlaybackRequiresUserGesture = false;

        if (features != null) {
            String show = features.get(LOCATION);
            if (show != null) {
                showLocationBar = show.equals("yes") ? true : false;
            }
            if(showLocationBar) {
                String hideNavigation = features.get(HIDE_NAVIGATION);
                String hideUrl = features.get(HIDE_URL);
                if(hideNavigation != null) hideNavigationButtons = hideNavigation.equals("yes") ? true : false;
                if(hideUrl != null) hideUrlBar = hideUrl.equals("yes") ? true : false;
            }
            String zoom = features.get(ZOOM);
            if (zoom != null) {
                showZoomControls = zoom.equals("yes") ? true : false;
            }
            String hidden = features.get(HIDDEN);
            if (hidden != null) {
                openWindowHidden = hidden.equals("yes") ? true : false;
            }
            String hardwareBack = features.get(HARDWARE_BACK_BUTTON);
            if (hardwareBack != null) {
                hadwareBackButton = hardwareBack.equals("yes") ? true : false;
            } else {
                hadwareBackButton = DEFAULT_HARDWARE_BACK;
            }
            String mediaPlayback = features.get(MEDIA_PLAYBACK_REQUIRES_USER_ACTION);
            if (mediaPlayback != null) {
                mediaPlaybackRequiresUserGesture = mediaPlayback.equals("yes") ? true : false;
            }
            String cache = features.get(CLEAR_ALL_CACHE);
            if (cache != null) {
                clearAllCache = cache.equals("yes") ? true : false;
            } else {
                cache = features.get(CLEAR_SESSION_CACHE);
                if (cache != null) {
                    clearSessionCache = cache.equals("yes") ? true : false;
                }
            }
            String shouldPause = features.get(SHOULD_PAUSE);
            if (shouldPause != null) {
                shouldPauseInAppBrowser = shouldPause.equals("yes") ? true : false;
            }
            String wideViewPort = features.get(USER_WIDE_VIEW_PORT);
            if (wideViewPort != null ) {
                useWideViewPort = wideViewPort.equals("yes") ? true : false;
            }
            String closeButtonCaptionSet = features.get(CLOSE_BUTTON_CAPTION);
            if (closeButtonCaptionSet != null) {
                closeButtonCaption = closeButtonCaptionSet;
            }
            String closeButtonColorSet = features.get(CLOSE_BUTTON_COLOR);
            if (closeButtonColorSet != null) {
                closeButtonColor = closeButtonColorSet;
            }
            String leftToRightSet = features.get(LEFT_TO_RIGHT);
            leftToRight = leftToRightSet != null && leftToRightSet.equals("yes");

            String toolbarColorSet = features.get(TOOLBAR_COLOR);
            if (toolbarColorSet != null) {
                toolbarColor = android.graphics.Color.parseColor(toolbarColorSet);
            }
            String navigationButtonColorSet = features.get(NAVIGATION_COLOR);
            if (navigationButtonColorSet != null) {
                navigationButtonColor = navigationButtonColorSet;
            }
            String showFooterSet = features.get(FOOTER);
            if (showFooterSet != null) {
                showFooter = showFooterSet.equals("yes") ? true : false;
            }
            String footerColorSet = features.get(FOOTER_COLOR);
            if (footerColorSet != null) {
                footerColor = footerColorSet;
            }
              String footerTitleSet = features.get(FOOTER_TITLE);
              if (footerTitleSet != null) {
                footerTitle = footerTitleSet;
               }
            if (features.get(BEFORELOAD) != null) {
                beforeload = features.get(BEFORELOAD);
            }
            String fullscreenSet = features.get(FULLSCREEN);
            if (fullscreenSet != null) {
                fullscreen = fullscreenSet.equals("yes") ? true : false;
            }
               String injectButtonSet = features.get(INJECT_BUTTON);
            if (injectButtonSet != null) {
                showInjectButton = injectButtonSet.equals("yes") ? true : false;
            }
            String injectJsCodeSet = features.get(INJECT_JS_CODE);
            if (injectJsCodeSet != null) {
                injectJsCode = injectJsCodeSet;
            }
            String menuButtonSet = features.get(MENU_BUTTON);
            if (menuButtonSet != null) {
                showMenuButton = menuButtonSet.equals("yes") ? true : false;
            }
        }

        final CordovaWebView thatWebView = this.webView;

        // Create dialog in new thread
        Runnable runnable = new Runnable() {
            /**
             * Convert our DIP units to Pixels
             *
             * @return int
             */
            private int dpToPixels(int dipValue) {
                int value = (int) TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP,
                        (float) dipValue,
                        cordova.getActivity().getResources().getDisplayMetrics()
                );

                return value;
            }

            private View createCloseButton(int id) {
                View _close;
                Resources activityRes = cordova.getActivity().getResources();

                // Determine button text based on whether we can go back
                // Initially show "Close", then check if we can go back after page loads
                String buttonText = "Close";

                if (closeButtonCaption != "") {
                    // Use TextView for text
                    TextView close = new TextView(cordova.getActivity());
                    close.setText(buttonText);
                    close.setTextSize(20);
                    if (closeButtonColor != "") close.setTextColor(android.graphics.Color.parseColor(closeButtonColor));
                    close.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    close.setPadding(this.dpToPixels(16), this.dpToPixels(12), this.dpToPixels(16), this.dpToPixels(12));
                    
                    // Add gray background with border radius
                    android.graphics.drawable.GradientDrawable closeButtonShape = new android.graphics.drawable.GradientDrawable();
                    closeButtonShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    closeButtonShape.setCornerRadius(this.dpToPixels(8));
                    closeButtonShape.setColor(Color.parseColor("#E0E0E0")); // Light gray background
                    close.setBackground(closeButtonShape);
                    
                    // Add click effect (darker on press)
                    close.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    // Darker color on press
                                    closeButtonShape.setColor(Color.parseColor("#BDBDBD"));
                                    break;
                                case android.view.MotionEvent.ACTION_UP:
                                case android.view.MotionEvent.ACTION_CANCEL:
                                    // Original color on release
                                    closeButtonShape.setColor(Color.parseColor("#E0E0E0"));
                                    break;
                            }
                            return false; // Let the click listener handle the click
                        }
                    });
                    
                    _close = close;
                }
                else {
                    ImageButton close = new ImageButton(cordova.getActivity());
                    int closeResId = activityRes.getIdentifier("ic_arrow_right", "drawable", cordova.getActivity().getPackageName());
                    Drawable closeIcon = activityRes.getDrawable(closeResId);
                    if (closeButtonColor != "") close.setColorFilter(android.graphics.Color.parseColor(closeButtonColor));
                    close.setImageDrawable(closeIcon);
                    close.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    close.getAdjustViewBounds();

                       // Add padding
                    close.setPadding(this.dpToPixels(16), this.dpToPixels(12), this.dpToPixels(16), this.dpToPixels(12));
                    
                    // Add gray background with border radius
                    android.graphics.drawable.GradientDrawable closeButtonShape = new android.graphics.drawable.GradientDrawable();
                    closeButtonShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    closeButtonShape.setCornerRadius(this.dpToPixels(8));
                    closeButtonShape.setColor(Color.parseColor("#E0E0E0")); // Light gray background
                    close.setBackground(closeButtonShape);
                    
                    // Add click effect (darker on press)
                    close.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    // Darker color on press
                                    closeButtonShape.setColor(Color.parseColor("#BDBDBD"));
                                    break;
                                case android.view.MotionEvent.ACTION_UP:
                                case android.view.MotionEvent.ACTION_CANCEL:
                                    // Original color on release
                                    closeButtonShape.setColor(Color.parseColor("#E0E0E0"));
                                    break;
                            }
                            return false; // Let the click listener handle the click
                        }
                    });

                    _close = close;
                }

                RelativeLayout.LayoutParams closeLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                if (leftToRight) closeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                else closeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                _close.setLayoutParams(closeLayoutParams);
               

                _close.setContentDescription("Close Button");
                _close.setId(Integer.valueOf(id));
                _close.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Check if we can go back in the WebView and we're not on the initial page
                        if (inAppWebView != null && inAppWebView.canGoBack() && inAppWebView.getUrl() != null && !inAppWebView.getUrl().equals("about:blank")) {
                            // If we can go back, go back instead of closing
                            inAppWebView.goBack();
                            // Update button text after navigation
                            updateCloseButtonText();
                        } else {
                            // If we can't go back (we're on the first page), close the dialog
                            closeDialog();
                        }
                    }
                });

                return _close;
            }

            @SuppressLint("NewApi")
            public void run() {

                // CB-6702 InAppBrowser hangs when opening more than one instance
                if (dialog != null) {
                    dialog.dismiss();
                };

                                // Let's create the main dialog
                dialog = new InAppBrowserDialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar_Fullscreen);
                dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                
                // Set soft fullscreen mode - full screen but keep system UI visible
                if (fullscreen) {
                    // Remove fullscreen flags to keep system UI visible
                    dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    
                    // Set flags for proper layout
                    dialog.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                    );
                    
                    // For Android 5.0+ (API 21+), set system UI visibility to keep bars visible
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        dialog.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        );
                    }
                }
                dialog.setCancelable(true);
                dialog.setInAppBroswer(getInAppBrowser());

                // Main container layout
                LinearLayout main = new LinearLayout(cordova.getActivity());
                main.setOrientation(LinearLayout.VERTICAL);
                main.setBackgroundColor(Color.parseColor("#F0F0F0")); // Light gray background like the green area in reference

                // Toolbar layout
                RelativeLayout toolbar = new RelativeLayout(cordova.getActivity());
                //Please, no more black!
                toolbar.setBackgroundColor(toolbarColor);
                toolbar.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, this.dpToPixels(TOOLBAR_HEIGHT)));
                toolbar.setPadding(this.dpToPixels(2), this.dpToPixels(2), this.dpToPixels(2), this.dpToPixels(2));
                if (leftToRight) {
                    toolbar.setHorizontalGravity(Gravity.LEFT);
                } else {
                    toolbar.setHorizontalGravity(Gravity.RIGHT);
                }
                toolbar.setVerticalGravity(Gravity.TOP);

                // Action Button Container layout
                RelativeLayout actionButtonContainer = new RelativeLayout(cordova.getActivity());
                RelativeLayout.LayoutParams actionButtonLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                if (leftToRight) actionButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                else actionButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                actionButtonContainer.setLayoutParams(actionButtonLayoutParams);
                actionButtonContainer.setHorizontalGravity(Gravity.LEFT);
                actionButtonContainer.setVerticalGravity(Gravity.CENTER_VERTICAL);
                actionButtonContainer.setId(leftToRight ? Integer.valueOf(5) : Integer.valueOf(1));

                // Back button
                ImageButton back = new ImageButton(cordova.getActivity());
                RelativeLayout.LayoutParams backLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                backLayoutParams.addRule(RelativeLayout.ALIGN_LEFT);
                back.setLayoutParams(backLayoutParams);
                back.setContentDescription("Back Button");
                back.setId(Integer.valueOf(2));
                Resources activityRes = cordova.getActivity().getResources();
                int backResId = activityRes.getIdentifier("ic_action_previous_item", "drawable", cordova.getActivity().getPackageName());
                Drawable backIcon = activityRes.getDrawable(backResId);
                if (navigationButtonColor != "") back.setColorFilter(android.graphics.Color.parseColor(navigationButtonColor));
                back.setBackground(null);
                back.setImageDrawable(backIcon);
                back.setScaleType(ImageView.ScaleType.FIT_CENTER);
                back.setPadding(0, this.dpToPixels(10), 0, this.dpToPixels(10));
                back.getAdjustViewBounds();

                back.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        goBack();
                    }
                });

                // Forward button
                ImageButton forward = new ImageButton(cordova.getActivity());
                RelativeLayout.LayoutParams forwardLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                forwardLayoutParams.addRule(RelativeLayout.RIGHT_OF, 2);
                forward.setLayoutParams(forwardLayoutParams);
                forward.setContentDescription("Forward Button");
                forward.setId(Integer.valueOf(3));
                int fwdResId = activityRes.getIdentifier("ic_action_next_item", "drawable", cordova.getActivity().getPackageName());
                Drawable fwdIcon = activityRes.getDrawable(fwdResId);
                if (navigationButtonColor != "") forward.setColorFilter(android.graphics.Color.parseColor(navigationButtonColor));
                forward.setBackground(null);
                forward.setImageDrawable(fwdIcon);
                forward.setScaleType(ImageView.ScaleType.FIT_CENTER);
                forward.setPadding(0, this.dpToPixels(10), 0, this.dpToPixels(10));
                forward.getAdjustViewBounds();

                forward.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        goForward();
                    }
                });

                // Edit Text Box
                edittext = new EditText(cordova.getActivity());
                RelativeLayout.LayoutParams textLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                textLayoutParams.addRule(RelativeLayout.RIGHT_OF, 1);
                textLayoutParams.addRule(RelativeLayout.LEFT_OF, 5);
                edittext.setLayoutParams(textLayoutParams);
                edittext.setId(Integer.valueOf(4));
                edittext.setSingleLine(true);
                edittext.setText(url);
                edittext.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                edittext.setImeOptions(EditorInfo.IME_ACTION_GO);
                edittext.setInputType(InputType.TYPE_NULL); // Will not except input... Makes the text NON-EDITABLE
                edittext.setOnKeyListener(new View.OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        // If the event is a key-down event on the "enter" button
                        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                            navigate(edittext.getText().toString());
                            return true;
                        }
                        return false;
                    }
                });


                // Header Close/Done button
                int closeButtonId = leftToRight ? 1 : 5;
                View close = createCloseButton(closeButtonId);
                toolbar.addView(close);

                // Footer
                RelativeLayout footer = new RelativeLayout(cordova.getActivity());
                int _footerColor;
                if(footerColor != "") {
                    _footerColor = Color.parseColor(footerColor);
                } else {
                    _footerColor = android.graphics.Color.LTGRAY;
                }
                footer.setBackgroundColor(_footerColor);
                LinearLayout.LayoutParams footerLayout = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, this.dpToPixels(TOOLBAR_HEIGHT));
                footer.setLayoutParams(footerLayout);
                footer.setPadding(this.dpToPixels(16), this.dpToPixels(16), this.dpToPixels(16), this.dpToPixels(16));

                // Create horizontal layout for footer content (like flex-direction: row)
                LinearLayout footerContent = new LinearLayout(cordova.getActivity());
                footerContent.setOrientation(LinearLayout.HORIZONTAL);
                footerContent.setGravity(Gravity.CENTER_VERTICAL);
                footerContent.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

                // Add inject button if enabled
                if (showInjectButton) {
                    Button injectButton = new Button(cordova.getActivity());
                    injectButton.setText("AI");
                    injectButton.setTextColor(Color.WHITE);
                    
                    injectButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    injectButton.setAllCaps(false);

                        // Set AI button color (#AB4CFF)
                    injectButton.setBackgroundColor(Color.parseColor("#AB4CFF"));
                    
                    // Add padding
                    injectButton.setPadding(this.dpToPixels(16), this.dpToPixels(12), this.dpToPixels(16), this.dpToPixels(12));
                    
                    // Add border radius (using shape drawable)
                    android.graphics.drawable.GradientDrawable aiButtonShape = new android.graphics.drawable.GradientDrawable();
                    aiButtonShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    aiButtonShape.setCornerRadius(this.dpToPixels(8));
                    aiButtonShape.setColor(Color.parseColor("#AB4CFF"));
                    injectButton.setBackground(aiButtonShape);
                    
                    // Add click effect (darker on press)
                    injectButton.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    // Darker color on press
                                    aiButtonShape.setColor(Color.parseColor("#8A3FD1"));
                                    break;
                                case android.view.MotionEvent.ACTION_UP:
                                case android.view.MotionEvent.ACTION_CANCEL:
                                    // Original color on release
                                    aiButtonShape.setColor(Color.parseColor("#AB4CFF"));
                                    break;
                            }
                            return false; // Let the click listener handle the click
                        }
                    });

                    LinearLayout.LayoutParams buttonLayout = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    );
                    buttonLayout.weight = 1; // Like flex: 1
                    buttonLayout.gravity = Gravity.CENTER;
                    injectButton.setLayoutParams(buttonLayout);

                    // Add click listener to inject JavaScript
                    injectButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Toggle modal WebView
                            if (isModalVisible) {
                                hideModalWebView();
                            } else {
                                showModalWebView();
                            }
                        }
                    });

                    footerContent.addView(injectButton);
                }
                
                // Add three-dot menu button (only if menu=yes) - now independent of inject button
                if (showMenuButton) {
                    // Add space before menu button if AI button is also present
                    if (showInjectButton) {
                        View spacer = new View(cordova.getActivity());
                        LinearLayout.LayoutParams spacerLayout = new LinearLayout.LayoutParams(
                            this.dpToPixels(8), // 8dp width for spacing
                            LayoutParams.WRAP_CONTENT
                        );
                        spacer.setLayoutParams(spacerLayout);
                        footerContent.addView(spacer);
                    }
                    
                    ImageButton menuButton = new ImageButton(cordova.getActivity());
                    menuButton.setContentDescription("Menu Button");
                    
                    // Get the three-dot icon from drawable resources
                    Resources menuActivityRes = cordova.getActivity().getResources();
                    int menuIconResId = menuActivityRes.getIdentifier("more_vert_24dp_000000_fill0_wght400_grad0_opsz24", "drawable", cordova.getActivity().getPackageName());
                    if (menuIconResId == 0) {
                        // Fallback to system icon if custom icon not found
                        menuIconResId = android.R.drawable.ic_menu_more;
                    }
                    Drawable menuIcon = menuActivityRes.getDrawable(menuIconResId);
                    menuButton.setImageDrawable(menuIcon);
                    menuButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    menuButton.getAdjustViewBounds();
                    
                    // Set button color
                    menuButton.setColorFilter(Color.WHITE);
                    
                    // Add padding (reduced for smaller width)
                    menuButton.setPadding(this.dpToPixels(12), this.dpToPixels(12), this.dpToPixels(12), this.dpToPixels(12));
                    
                    // Add gray background with border radius
                    android.graphics.drawable.GradientDrawable menuButtonShape = new android.graphics.drawable.GradientDrawable();
                    menuButtonShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    menuButtonShape.setCornerRadius(this.dpToPixels(8));
                    menuButtonShape.setColor(Color.parseColor("#666666")); // Dark gray background
                    menuButton.setBackground(menuButtonShape);
                    
                    // Add click effect (darker on press)
                    menuButton.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, android.view.MotionEvent event) {
                            switch (event.getAction()) {
                                case android.view.MotionEvent.ACTION_DOWN:
                                    // Darker color on press
                                    menuButtonShape.setColor(Color.parseColor("#444444"));
                                    break;
                                case android.view.MotionEvent.ACTION_UP:
                                case android.view.MotionEvent.ACTION_CANCEL:
                                    // Original color on release
                                    menuButtonShape.setColor(Color.parseColor("#666666"));
                                    break;
                            }
                            return false; // Let the click listener handle the click
                        }
                    });

                    LinearLayout.LayoutParams menuButtonLayout = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    );
                    menuButtonLayout.weight = 0; // No weight - fixed width
                    menuButtonLayout.gravity = Gravity.CENTER;
                    menuButton.setLayoutParams(menuButtonLayout);

                    // Add click listener to show menu modal
                    menuButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Toggle menu modal
                            if (isMenuModalVisible) {
                                hideMenuModal();
                            } else {
                                showMenuModal();
                            }
                        }
                    });

                    footerContent.addView(menuButton);
                }

                // Add title in center
                if (!footerTitle.isEmpty()) {
                    TextView footerText = new TextView(cordova.getActivity());
                    footerText.setText(footerTitle);
                    footerText.setTextColor(Color.BLACK);
                    footerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                    footerText.setGravity(Gravity.CENTER);

                    LinearLayout.LayoutParams textLayout = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    );
                    textLayout.weight = 2; // Give more space to title (like flex: 2)
                    textLayout.gravity = Gravity.CENTER;
                    footerText.setLayoutParams(textLayout);

                    footerContent.addView(footerText);
                }

                // Add close button with back/close functionality
                View footerClose = createCloseButton(7);
                LinearLayout.LayoutParams closeButtonLayout = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                );
                closeButtonLayout.weight = 1; // Like flex: 1
                closeButtonLayout.gravity = Gravity.CENTER;
                footerClose.setLayoutParams(closeButtonLayout);
                footerContent.addView(footerClose);

                // Add the horizontal content to footer
                footer.addView(footerContent);


                // WebView
               inAppWebView = new WebView(cordova.getActivity());
                inAppWebView.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                inAppWebView.setId(Integer.valueOf(6));
                // File Chooser Implemented ChromeClient
                inAppWebView.setWebChromeClient(new InAppChromeClient(thatWebView) {
                    public boolean onShowFileChooser (WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams)
                    {
                        LOG.d(LOG_TAG, "File Chooser 5.0+");
                        // If callback exists, finish it.
                        if(mUploadCallback != null) {
                            mUploadCallback.onReceiveValue(null);
                        }
                        mUploadCallback = filePathCallback;

                        // Create File Chooser Intent
                        Intent content = new Intent(Intent.ACTION_GET_CONTENT);
                        content.addCategory(Intent.CATEGORY_OPENABLE);
                        content.setType("*/*");

                        // Run cordova startActivityForResult
                        cordova.startActivityForResult(InAppBrowser.this, Intent.createChooser(content, "Select File"), FILECHOOSER_REQUESTCODE);
                        return true;
                    }
                });
                currentClient = new InAppBrowserClient(thatWebView, edittext, beforeload);
                inAppWebView.setWebViewClient(currentClient);
                WebSettings settings = inAppWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setJavaScriptCanOpenWindowsAutomatically(true);
                settings.setBuiltInZoomControls(showZoomControls);
                settings.setPluginState(android.webkit.WebSettings.PluginState.ON);
                
                // download event
                
                inAppWebView.setDownloadListener(
                    new DownloadListener(){
                        public void onDownloadStart(
                                String url, String userAgent, String contentDisposition, String mimetype, long contentLength
                        ){
                            try{
                                JSONObject succObj = new JSONObject();
                                succObj.put("type", DOWNLOAD_EVENT);
                                succObj.put("url",url);
                                succObj.put("userAgent",userAgent);
                                succObj.put("contentDisposition",contentDisposition);
                                succObj.put("mimetype",mimetype);
                                succObj.put("contentLength",contentLength);
                                sendUpdate(succObj, true);
                            }
                            catch(Exception e){
                                LOG.e(LOG_TAG,e.getMessage());
                            }
                        }
                    }
                );        

                // Add postMessage interface
                class JsObject {
                    @JavascriptInterface
                    public void postMessage(String data) {
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("type", MESSAGE_EVENT);
                            obj.put("data", new JSONObject(data));
                            sendUpdate(obj, true);
                        } catch (JSONException ex) {
                            LOG.e(LOG_TAG, "data object passed to postMessage has caused a JSON error.");
                        }
                    }
                }

                settings.setMediaPlaybackRequiresUserGesture(mediaPlaybackRequiresUserGesture);
                inAppWebView.addJavascriptInterface(new JsObject(), "cordova_iab");

                String overrideUserAgent = preferences.getString("OverrideUserAgent", null);
                String appendUserAgent = preferences.getString("AppendUserAgent", null);

                if (overrideUserAgent != null) {
                    settings.setUserAgentString(overrideUserAgent);
                }
                if (appendUserAgent != null) {
                    settings.setUserAgentString(settings.getUserAgentString() + " " + appendUserAgent);
                }

                //Toggle whether this is enabled or not!
                Bundle appSettings = cordova.getActivity().getIntent().getExtras();
                boolean enableDatabase = appSettings == null ? true : appSettings.getBoolean("InAppBrowserStorageEnabled", true);
                if (enableDatabase) {
                    String databasePath = cordova.getActivity().getApplicationContext().getDir("inAppBrowserDB", Context.MODE_PRIVATE).getPath();
                    settings.setDatabasePath(databasePath);
                    settings.setDatabaseEnabled(true);
                }
                settings.setDomStorageEnabled(true);

                if (clearAllCache) {
                    CookieManager.getInstance().removeAllCookie();
                } else if (clearSessionCache) {
                    CookieManager.getInstance().removeSessionCookie();
                }

                // Enable Thirdparty Cookies
                CookieManager.getInstance().setAcceptThirdPartyCookies(inAppWebView,true);
                inAppWebView.setId(Integer.valueOf(6));
                inAppWebView.getSettings().setLoadWithOverviewMode(true);
                inAppWebView.getSettings().setUseWideViewPort(useWideViewPort);
                // Multiple Windows set to true to mitigate Chromium security bug.
                //  See: https://bugs.chromium.org/p/chromium/issues/detail?id=1083819
                inAppWebView.getSettings().setSupportMultipleWindows(true);
                inAppWebView.requestFocus();
                inAppWebView.requestFocusFromTouch();

                // Add the back and forward buttons to our action button container layout
                actionButtonContainer.addView(back);
                actionButtonContainer.addView(forward);

                // Add the views to our toolbar if they haven't been disabled
                if (!hideNavigationButtons) toolbar.addView(actionButtonContainer);
                if (!hideUrlBar) toolbar.addView(edittext);

                // Don't add the toolbar if its been disabled
                if (getShowLocationBar()) {
                    // Add our toolbar to our main view/layout
                    main.addView(toolbar);
                }

                // Add our webview to our main view/layout with margin and border radius
                RelativeLayout webViewLayout = new RelativeLayout(cordova.getActivity());
                
                // Create a container for the WebView with border radius
                RelativeLayout webViewContainer = new RelativeLayout(cordova.getActivity());
                
                // Create background drawable with border radius for the container
                android.graphics.drawable.GradientDrawable containerBackground = new android.graphics.drawable.GradientDrawable();
                containerBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                containerBackground.setCornerRadius(this.dpToPixels(20)); // Border radius
                containerBackground.setColor(Color.WHITE); // White background
                webViewContainer.setBackground(containerBackground);
                
                // Enable clipping to make the WebView content respect the border radius
                webViewContainer.setClipToOutline(true);
                
                // Set layout parameters for the container with margin to create spacing
                RelativeLayout.LayoutParams containerParams = new RelativeLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                );
                containerParams.setMargins(this.dpToPixels(16), this.dpToPixels(16), this.dpToPixels(16), this.dpToPixels(16));
                webViewContainer.setLayoutParams(containerParams);
                
                // Set layout parameters for the WebView to fill the container
                RelativeLayout.LayoutParams webViewParams = new RelativeLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                );
                inAppWebView.setLayoutParams(webViewParams);
                
                // Add the WebView to the container
                webViewContainer.addView(inAppWebView);
                
                // Add the container to the webViewLayout
                webViewLayout.addView(webViewContainer);
                
                LinearLayout.LayoutParams webViewLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
                webViewLayoutParams.weight = 1; // This makes the webViewLayout take remaining space
                // Add top margin to avoid camera punch - increased for better clearance
                webViewLayoutParams.setMargins(0, this.dpToPixels(48), 0, 0);
                webViewLayout.setLayoutParams(webViewLayoutParams);
                main.addView(webViewLayout);
                
                // Load the URL after the WebView is properly added to the layout
                inAppWebView.loadUrl(url);

                // Don't add the footer unless it's been enabled
                if (showFooter) {
                    main.addView(footer);
                }

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(dialog.getWindow().getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;

                if (dialog != null) {
                    dialog.setContentView(main);
                    dialog.show();
                    dialog.getWindow().setAttributes(lp);
                    
                    // Ensure soft fullscreen is maintained after showing
                    if (fullscreen) {
                        dialog.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        );
                    }
                }
                // the goal of openhidden is to load the url and not display it
                // Show() needs to be called to cause the URL to be loaded
                if (openWindowHidden && dialog != null) {
                    dialog.hide();
                }
            }
        };
        this.cordova.getActivity().runOnUiThread(runnable);
        return "";
    }

    /**
     * Create a new plugin success result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     */
    private void sendUpdate(JSONObject obj, boolean keepCallback) {
        sendUpdate(obj, keepCallback, PluginResult.Status.OK);
    }

    /**
     * Create a new plugin result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     * @param status the status code to return to the JavaScript environment
     */
    private void sendUpdate(JSONObject obj, boolean keepCallback, PluginResult.Status status) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(status, obj);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
            if (!keepCallback) {
                callbackContext = null;
            }
        }
    }

    /**
     * Receive File Data from File Chooser
     *
     * @param requestCode the requested code from chromeclient
     * @param resultCode the result code returned from android system
     * @param intent the data from android file chooser
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        LOG.d(LOG_TAG, "onActivityResult");
        // If RequestCode or Callback is Invalid
        if(requestCode != FILECHOOSER_REQUESTCODE || mUploadCallback == null) {
            super.onActivityResult(requestCode, resultCode, intent);
            return;
        }
        mUploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
        mUploadCallback = null;
    }

    /**
     * The webview client receives notifications about appView
     */
    public class InAppBrowserClient extends WebViewClient {
        EditText edittext;
        CordovaWebView webView;
        String beforeload;
        boolean waitForBeforeload;

        /**
         * Constructor.
         *
         * @param webView
         * @param mEditText
         */
        public InAppBrowserClient(CordovaWebView webView, EditText mEditText, String beforeload) {
            this.webView = webView;
            this.edittext = mEditText;
            this.beforeload = beforeload;
            this.waitForBeforeload = beforeload != null;
        }

        /**
         * Override the URL that should be loaded
         *
         * Legacy (deprecated in API 24)
         * For Android 6 and below.
         *
         * @param webView
         * @param url
         */
        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String url) {
            return shouldOverrideUrlLoading(url, null);
        }

        /**
         * Override the URL that should be loaded
         *
         * New (added in API 24)
         * For Android 7 and above.
         *
         * @param webView
         * @param request
         */
        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
            return shouldOverrideUrlLoading(request.getUrl().toString(), request.getMethod());
        }

        /**
         * Override the URL that should be loaded
         *
         * This handles a small subset of all the URIs that would be encountered.
         *
         * @param url
         * @param method
         */
        public boolean shouldOverrideUrlLoading(String url, String method) {
            boolean override = false;
            boolean useBeforeload = false;
            String errorMessage = null;

            if (beforeload.equals("yes") && method == null) {
                useBeforeload = true;
            } else if(beforeload.equals("yes")
                    //TODO handle POST requests then this condition can be removed:
                    && !method.equals("POST"))
            {
                useBeforeload = true;
            } else if(beforeload.equals("get") && (method == null || method.equals("GET"))) {
                useBeforeload = true;
            } else if(beforeload.equals("post") && (method == null || method.equals("POST"))) {
                //TODO handle POST requests
                errorMessage = "beforeload doesn't yet support POST requests";
            }

            // On first URL change, initiate JS callback. Only after the beforeload event, continue.
            if (useBeforeload && this.waitForBeforeload) {
                if(sendBeforeLoad(url, method)) {
                    return true;
                }
            }

            if(errorMessage != null) {
                try {
                    LOG.e(LOG_TAG, errorMessage);
                    JSONObject obj = new JSONObject();
                    obj.put("type", LOAD_ERROR_EVENT);
                    obj.put("url", url);
                    obj.put("code", -1);
                    obj.put("message", errorMessage);
                    sendUpdate(obj, true, PluginResult.Status.ERROR);
                } catch(Exception e) {
                    LOG.e(LOG_TAG, "Error sending loaderror for " + url + ": " + e.toString());
                }
            }

            if (url.startsWith(WebView.SCHEME_TEL)) {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse(url));
                    cordova.getActivity().startActivity(intent);
                    override = true;
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
                }
            } else if (url.startsWith("geo:") || url.startsWith(WebView.SCHEME_MAILTO) || url.startsWith("market:") || url.startsWith("intent:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    cordova.getActivity().startActivity(intent);
                    override = true;
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(LOG_TAG, "Error with " + url + ": " + e.toString());
                }
            }
            // If sms:5551212?body=This is the message
            else if (url.startsWith("sms:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);

                    // Get address
                    String address = null;
                    int parmIndex = url.indexOf('?');
                    if (parmIndex == -1) {
                        address = url.substring(4);
                    } else {
                        address = url.substring(4, parmIndex);

                        // If body, then set sms body
                        Uri uri = Uri.parse(url);
                        String query = uri.getQuery();
                        if (query != null) {
                            if (query.startsWith("body=")) {
                                intent.putExtra("sms_body", query.substring(5));
                            }
                        }
                    }
                    intent.setData(Uri.parse("sms:" + address));
                    intent.putExtra("address", address);
                    intent.setType("vnd.android-dir/mms-sms");
                    cordova.getActivity().startActivity(intent);
                    override = true;
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(LOG_TAG, "Error sending sms " + url + ":" + e.toString());
                }
            }
            // Test for whitelisted custom scheme names like mycoolapp:// or twitteroauthresponse:// (Twitter Oauth Response)
            else if (!url.startsWith("http:") && !url.startsWith("https:") && url.matches("^[A-Za-z0-9+.-]*://.*?$")) {
                if (allowedSchemes == null) {
                    String allowed = preferences.getString("AllowedSchemes", null);
                    if(allowed != null) {
                        allowedSchemes = allowed.split(",");
                    }
                }
                if (allowedSchemes != null) {
                    for (String scheme : allowedSchemes) {
                        if (url.startsWith(scheme)) {
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("type", "customscheme");
                                obj.put("url", url);
                                sendUpdate(obj, true);
                                override = true;
                            } catch (JSONException ex) {
                                LOG.e(LOG_TAG, "Custom Scheme URI passed in has caused a JSON error.");
                            }
                        }
                    }
                }
            }

            if (useBeforeload) {
                this.waitForBeforeload = true;
            }
            return override;
        }

        private boolean sendBeforeLoad(String url, String method) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", BEFORELOAD);
                obj.put("url", url);
                if(method != null) {
                    obj.put("method", method);
                }
                sendUpdate(obj, true);
                return true;
            } catch (JSONException ex) {
                LOG.e(LOG_TAG, "URI passed in has caused a JSON error.");
            }
            return false;
        }

        /**
         * New (added in API 21)
         * For Android 5.0 and above.
         *
         * @param view
         * @param request
         */
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return shouldInterceptRequest(request.getUrl().toString(), super.shouldInterceptRequest(view, request), request.getMethod());
        }

        public WebResourceResponse shouldInterceptRequest(String url, WebResourceResponse response, String method) {
            return response;
        }

        /*
         * onPageStarted fires the LOAD_START_EVENT
         *
         * @param view
         * @param url
         * @param favicon
         */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            String newloc = "";
            if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:")) {
                newloc = url;
            }
            else
            {
                // Assume that everything is HTTP at this point, because if we don't specify,
                // it really should be.  Complain loudly about this!!!
                LOG.e(LOG_TAG, "Possible Uncaught/Unknown URI");
                newloc = "http://" + url;
            }

            // Update the UI if we haven't already
            if (!newloc.equals(edittext.getText().toString())) {
                edittext.setText(newloc);
            }

            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_START_EVENT);
                obj.put("url", newloc);
                sendUpdate(obj, true);
            } catch (JSONException ex) {
                LOG.e(LOG_TAG, "URI passed in has caused a JSON error.");
            }
        }

        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            // Set the namespace for postMessage()
            injectDeferredObject("window.webkit={messageHandlers:{cordova_iab:cordova_iab}}", null);

            // CB-10395 InAppBrowser's WebView not storing cookies reliable to local device storage
            CookieManager.getInstance().flush();

            // https://issues.apache.org/jira/browse/CB-11248
            view.clearFocus();
            view.requestFocus();

            // Update close button text based on navigation state
            updateCloseButtonText();

            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_STOP_EVENT);
                obj.put("url", url);

                sendUpdate(obj, true);
            } catch (JSONException ex) {
                LOG.d(LOG_TAG, "Should never happen");
            }
        }

        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);

            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_ERROR_EVENT);
                obj.put("url", failingUrl);
                obj.put("code", errorCode);
                obj.put("message", description);

                sendUpdate(obj, true, PluginResult.Status.ERROR);
            } catch (JSONException ex) {
                LOG.d(LOG_TAG, "Should never happen");
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            super.onReceivedSslError(view, handler, error);
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", LOAD_ERROR_EVENT);
                obj.put("url", error.getUrl());
                obj.put("code", 0);
                obj.put("sslerror", error.getPrimaryError());
                String message;
                switch (error.getPrimaryError()) {
                case SslError.SSL_DATE_INVALID:
                    message = "The date of the certificate is invalid";
                    break;
                case SslError.SSL_EXPIRED:
                    message = "The certificate has expired";
                    break;
                case SslError.SSL_IDMISMATCH:
                    message = "Hostname mismatch";
                    break;
                default:
                case SslError.SSL_INVALID:
                    message = "A generic error occurred";
                    break;
                case SslError.SSL_NOTYETVALID:
                    message = "The certificate is not yet valid";
                    break;
                case SslError.SSL_UNTRUSTED:
                    message = "The certificate authority is not trusted";
                    break;
                }
                obj.put("message", message);

                sendUpdate(obj, true, PluginResult.Status.ERROR);
            } catch (JSONException ex) {
                LOG.d(LOG_TAG, "Should never happen");
            }
            handler.cancel();
        }

        /**
         * On received http auth request.
         */
        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

            // Check if there is some plugin which can resolve this auth challenge
            PluginManager pluginManager = null;
            try {
                Method gpm = webView.getClass().getMethod("getPluginManager");
                pluginManager = (PluginManager)gpm.invoke(webView);
            } catch (NoSuchMethodException e) {
                LOG.d(LOG_TAG, e.getLocalizedMessage());
            } catch (IllegalAccessException e) {
                LOG.d(LOG_TAG, e.getLocalizedMessage());
            } catch (InvocationTargetException e) {
                LOG.d(LOG_TAG, e.getLocalizedMessage());
            }

            if (pluginManager == null) {
                try {
                    Field pmf = webView.getClass().getField("pluginManager");
                    pluginManager = (PluginManager)pmf.get(webView);
                } catch (NoSuchFieldException e) {
                    LOG.d(LOG_TAG, e.getLocalizedMessage());
                } catch (IllegalAccessException e) {
                    LOG.d(LOG_TAG, e.getLocalizedMessage());
                }
            }

            if (pluginManager != null && pluginManager.onReceivedHttpAuthRequest(webView, new CordovaHttpAuthHandler(handler), host, realm)) {
                return;
            }

            // By default handle 401 like we'd normally do!
            super.onReceivedHttpAuthRequest(view, handler, host, realm);
        }
    }
}
