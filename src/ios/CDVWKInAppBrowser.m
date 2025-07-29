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

#import "CDVWKInAppBrowser.h"
#import <Cordova/NSDictionary+CordovaPreferences.h>

@implementation UIColor (HexColor)

+ (UIColor *)colorWithHexString:(NSString *)hexString {
    unsigned rgbValue = 0;
    NSScanner *scanner = [NSScanner scannerWithString:hexString];
    [scanner setScanLocation:1]; // bypass '#' character
    [scanner scanHexInt:&rgbValue];
    return [UIColor colorWithRed:((rgbValue & 0xFF0000) >> 16)/255.0
                           green:((rgbValue & 0xFF00) >> 8)/255.0
                            blue:(rgbValue & 0xFF)/255.0 alpha:1.0];
}

@end
#import <Cordova/CDVWebViewProcessPoolFactory.h>
#import <Cordova/CDVPluginResult.h>

#define    kInAppBrowserTargetSelf @"_self"
#define    kInAppBrowserTargetSystem @"_system"
#define    kInAppBrowserTargetBlank @"_blank"

#define    kInAppBrowserToolbarBarPositionBottom @"bottom"
#define    kInAppBrowserToolbarBarPositionTop @"top"

#define    IAB_BRIDGE_NAME @"cordova_iab"

#define    TOOLBAR_HEIGHT 120.0
#define    LOCATIONBAR_HEIGHT 21.0
#define    FOOTER_HEIGHT ((TOOLBAR_HEIGHT) + (LOCATIONBAR_HEIGHT))

#pragma mark CDVWKInAppBrowser

@implementation CDVWKInAppBrowser

static CDVWKInAppBrowser* instance = nil;

+ (id) getInstance{
    return instance;
}

- (void)pluginInitialize
{
    instance = self;
    _callbackIdPattern = nil;
    _beforeload = @"";
    _waitForBeforeload = NO;
}

- (void)onReset
{
    [self close:nil];
}

- (void)close:(CDVInvokedUrlCommand*)command
{
    if (self.inAppBrowserViewController == nil) {
        NSLog(@"IAB.close() called but it was already closed.");
        return;
    }
    
    // Things are cleaned up in browserExit.
    [self.inAppBrowserViewController close];
}

- (BOOL) isSystemUrl:(NSURL*)url
{
    if ([[url host] isEqualToString:@"itunes.apple.com"]) {
        return YES;
    }
    
    return NO;
}

- (void)open:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult;
    
    NSString* url = [command argumentAtIndex:0];
    NSString* target = [command argumentAtIndex:1 withDefault:kInAppBrowserTargetSelf];
    NSString* options = [command argumentAtIndex:2 withDefault:@"" andClass:[NSString class]];
    
    self.callbackId = command.callbackId;
    
    if (url != nil) {
        NSURL* baseUrl = [self.webViewEngine URL];
        NSURL* absoluteUrl = [[NSURL URLWithString:url relativeToURL:baseUrl] absoluteURL];
        
        if ([self isSystemUrl:absoluteUrl]) {
            target = kInAppBrowserTargetSystem;
        }
        
        if ([target isEqualToString:kInAppBrowserTargetSelf]) {
            [self openInCordovaWebView:absoluteUrl withOptions:options];
        } else if ([target isEqualToString:kInAppBrowserTargetSystem]) {
            [self openInSystem:absoluteUrl];
        } else { // _blank or anything else
            [self openInInAppBrowser:absoluteUrl withOptions:options];
        }
        
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"incorrect number of arguments"];
    }
    
    [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)openInInAppBrowser:(NSURL*)url withOptions:(NSString*)options
{
    CDVInAppBrowserOptions* browserOptions = [CDVInAppBrowserOptions parseOptions:options];
    
    WKWebsiteDataStore* dataStore = [WKWebsiteDataStore defaultDataStore];
    if (browserOptions.cleardata) {
        
        NSDate* dateFrom = [NSDate dateWithTimeIntervalSince1970:0];
        [dataStore removeDataOfTypes:[WKWebsiteDataStore allWebsiteDataTypes] modifiedSince:dateFrom completionHandler:^{
            NSLog(@"Removed all WKWebView data");
            self.inAppBrowserViewController.webView.configuration.processPool = [[WKProcessPool alloc] init]; // create new process pool to flush all data
        }];
    }
    
    if (browserOptions.clearcache) {
        // Deletes all cookies
        WKHTTPCookieStore* cookieStore = dataStore.httpCookieStore;
        [cookieStore getAllCookies:^(NSArray* cookies) {
            NSHTTPCookie* cookie;
            for(cookie in cookies){
                [cookieStore deleteCookie:cookie completionHandler:nil];
            }
        }];
    }
    
    if (browserOptions.clearsessioncache) {
        // Deletes session cookies
        WKHTTPCookieStore* cookieStore = dataStore.httpCookieStore;
        [cookieStore getAllCookies:^(NSArray* cookies) {
            NSHTTPCookie* cookie;
            for(cookie in cookies){
                if(cookie.sessionOnly){
                    [cookieStore deleteCookie:cookie completionHandler:nil];
                }
            }
        }];
    }

    if (self.inAppBrowserViewController == nil) {
        self.inAppBrowserViewController = [[CDVWKInAppBrowserViewController alloc] initWithBrowserOptions: browserOptions andSettings:self.commandDelegate.settings];
        self.inAppBrowserViewController.navigationDelegate = self;
        
        if ([self.viewController conformsToProtocol:@protocol(CDVScreenOrientationDelegate)]) {
            self.inAppBrowserViewController.orientationDelegate = (UIViewController <CDVScreenOrientationDelegate>*)self.viewController;
        }
    }
    
    [self.inAppBrowserViewController showLocationBar:browserOptions.location];
    [self.inAppBrowserViewController showToolBar:browserOptions.toolbar :browserOptions.toolbarposition];
    if (browserOptions.closebuttoncaption != nil || browserOptions.closebuttoncolor != nil) {
        [self.inAppBrowserViewController.closeButton setTitle:browserOptions.closebuttoncaption forState:UIControlStateNormal];
        if (browserOptions.closebuttoncolor != nil) {
            [self.inAppBrowserViewController.closeButton setTitleColor:[UIColor colorWithHexString:browserOptions.closebuttoncolor] forState:UIControlStateNormal];
        }
    }

    if (browserOptions.footer) {
        self.inAppBrowserViewController.toolbar.hidden = NO;
        self.inAppBrowserViewController.AIButton.hidden = !browserOptions.injectbutton;
        self.inAppBrowserViewController.menuButton.hidden = !browserOptions.menu;
        if (browserOptions.footertitle != nil) {
            self.inAppBrowserViewController.footerTitleLabel.text = browserOptions.footertitle;
        }
    } else {
        self.inAppBrowserViewController.toolbar.hidden = YES;
    }
    // Set Presentation Style
    UIModalPresentationStyle presentationStyle = UIModalPresentationFullScreen; // default
    if (browserOptions.presentationstyle != nil) {
        if ([[browserOptions.presentationstyle lowercaseString] isEqualToString:@"pagesheet"]) {
            presentationStyle = UIModalPresentationPageSheet;
        } else if ([[browserOptions.presentationstyle lowercaseString] isEqualToString:@"formsheet"]) {
            presentationStyle = UIModalPresentationFormSheet;
        }
    }
    self.inAppBrowserViewController.modalPresentationStyle = presentationStyle;
    
    // Set Transition Style
    UIModalTransitionStyle transitionStyle = UIModalTransitionStyleCoverVertical; // default
    if (browserOptions.transitionstyle != nil) {
        if ([[browserOptions.transitionstyle lowercaseString] isEqualToString:@"fliphorizontal"]) {
            transitionStyle = UIModalTransitionStyleFlipHorizontal;
        } else if ([[browserOptions.transitionstyle lowercaseString] isEqualToString:@"crossdissolve"]) {
            transitionStyle = UIModalTransitionStyleCrossDissolve;
        }
    }
    self.inAppBrowserViewController.modalTransitionStyle = transitionStyle;
    
    //prevent webView from bouncing
    if (browserOptions.disallowoverscroll) {
        if ([self.inAppBrowserViewController.webView respondsToSelector:@selector(scrollView)]) {
            ((UIScrollView*)[self.inAppBrowserViewController.webView scrollView]).bounces = NO;
        } else {
            for (id subview in self.inAppBrowserViewController.webView.subviews) {
                if ([[subview class] isSubclassOfClass:[UIScrollView class]]) {
                    ((UIScrollView*)subview).bounces = NO;
                }
            }
        }
    }
    
    // use of beforeload event
    if([browserOptions.beforeload isKindOfClass:[NSString class]]){
        _beforeload = browserOptions.beforeload;
    }else{
        _beforeload = @"yes";
    }
    _waitForBeforeload = ![_beforeload isEqualToString:@""];
    
    [self.inAppBrowserViewController navigateTo:url];
    if (!browserOptions.hidden) {
        [self show:nil withNoAnimate:browserOptions.hidden];
    }
}

- (void)show:(CDVInvokedUrlCommand*)command{
    [self show:command withNoAnimate:NO];
}

- (void)show:(CDVInvokedUrlCommand*)command withNoAnimate:(BOOL)noAnimate
{
    BOOL initHidden = NO;
    if(command == nil && noAnimate == YES){
        initHidden = YES;
    }
    
    if (self.inAppBrowserViewController == nil) {
        NSLog(@"Tried to show IAB after it was closed.");
        return;
    }
    
    __block CDVInAppBrowserNavigationController* nav = [[CDVInAppBrowserNavigationController alloc]
                                                        initWithRootViewController:self.inAppBrowserViewController];
    nav.orientationDelegate = self.inAppBrowserViewController;
    nav.navigationBarHidden = YES;
    nav.modalPresentationStyle = self.inAppBrowserViewController.modalPresentationStyle;
    nav.presentationController.delegate = self.inAppBrowserViewController;
    
    __weak CDVWKInAppBrowser* weakSelf = self;
    
    // Run later to avoid the "took a long time" log message.
    dispatch_async(dispatch_get_main_queue(), ^{
        if (weakSelf.inAppBrowserViewController != nil) {
            float osVersion = [[[UIDevice currentDevice] systemVersion] floatValue];
            __strong __typeof(weakSelf) strongSelf = weakSelf;
            if (!strongSelf->tmpWindow) {
                if (@available(iOS 13.0, *)) {
                    UIWindowScene *scene = strongSelf.viewController.view.window.windowScene;
                    if (scene) {
                        strongSelf->tmpWindow = [[UIWindow alloc] initWithWindowScene:scene];
                    }
                }

                if (!strongSelf->tmpWindow) {
                    CGRect frame = [[UIScreen mainScreen] bounds];
                    if(initHidden && osVersion < 11){
                       frame.origin.x = -10000;
                    }
                    strongSelf->tmpWindow = [[UIWindow alloc] initWithFrame:frame];
                }
            }
            UIViewController *tmpController = [[UIViewController alloc] init];
            [strongSelf->tmpWindow setRootViewController:tmpController];
            [strongSelf->tmpWindow setWindowLevel:UIWindowLevelNormal];

            if(!initHidden || osVersion < 11){
                [self->tmpWindow makeKeyAndVisible];
            }
            [tmpController presentViewController:nav animated:!noAnimate completion:nil];
        }
    });
}

- (void)hide:(CDVInvokedUrlCommand*)command
{
    // Set tmpWindow to hidden to make main webview responsive to touch again
    // https://stackoverflow.com/questions/4544489/how-to-remove-a-uiwindow
    self->tmpWindow.hidden = YES;
    self->tmpWindow = nil;

    if (self.inAppBrowserViewController == nil) {
        NSLog(@"Tried to hide IAB after it was closed.");
        return;
        
        
    }
    
    // Run later to avoid the "took a long time" log message.
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.inAppBrowserViewController != nil) {
            [self.inAppBrowserViewController.presentingViewController dismissViewControllerAnimated:YES completion:nil];
        }
    });
}

- (void)openInCordovaWebView:(NSURL*)url withOptions:(NSString*)options
{
    NSURLRequest* request = [NSURLRequest requestWithURL:url];
    // the webview engine itself will filter for this according to <allow-navigation> policy
    // in config.xml
    [self.webViewEngine loadRequest:request];
}

- (void)openInSystem:(NSURL*)url
{
    [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:^(BOOL success) {
        if (!success) {
            [[NSNotificationCenter defaultCenter] postNotification:[NSNotification notificationWithName:CDVPluginHandleOpenURLNotification object:url]];
        }
    }];
}

- (void)loadAfterBeforeload:(CDVInvokedUrlCommand*)command
{
    NSString* urlStr = [command argumentAtIndex:0];

    if ([_beforeload isEqualToString:@""]) {
        NSLog(@"unexpected loadAfterBeforeload called without feature beforeload=get|post");
    }
    if (self.inAppBrowserViewController == nil) {
        NSLog(@"Tried to invoke loadAfterBeforeload on IAB after it was closed.");
        return;
    }
    if (urlStr == nil) {
        NSLog(@"loadAfterBeforeload called with nil argument, ignoring.");
        return;
    }

    NSURL* url = [NSURL URLWithString:urlStr];
    //_beforeload = @"";
    _waitForBeforeload = NO;
    [self.inAppBrowserViewController navigateTo:url];
}

- (void)sendEvent:(NSString*)event withData:(id)data
{
    if (self.callbackId != nil) {
        NSMutableDictionary* message = [NSMutableDictionary dictionary];
        [message setObject:event forKey:@"type"];
        if (data != nil) {
            [message setObject:data forKey:@"data"];
        }

        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}

// This is a helper method for the inject{Script|Style}{Code|File} API calls, which
// provides a consistent method for injecting JavaScript code into the document.
//
// If a wrapper string is supplied, then the source string will be JSON-encoded (adding
// quotes) and wrapped using string formatting. (The wrapper string should have a single
// '%@' marker).
//
// If no wrapper is supplied, then the source string is executed directly.

- (void)injectDeferredObject:(NSString*)source withWrapper:(NSString*)jsWrapper
{
    // Ensure a message handler bridge is created to communicate with the CDVWKInAppBrowserViewController
    [self evaluateJavaScript: [NSString stringWithFormat:@"(function(w){if(!w._cdvMessageHandler) {w._cdvMessageHandler = function(id,d){w.webkit.messageHandlers.%@.postMessage({d:d, id:id});}}})(window)", IAB_BRIDGE_NAME]];
    
    if (jsWrapper != nil) {
        NSData* jsonData = [NSJSONSerialization dataWithJSONObject:@[source] options:0 error:nil];
        NSString* sourceArrayString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        if (sourceArrayString) {
            NSString* sourceString = [sourceArrayString substringWithRange:NSMakeRange(1, [sourceArrayString length] - 2)];
            NSString* jsToInject = [NSString stringWithFormat:jsWrapper, sourceString];
            [self evaluateJavaScript:jsToInject];
        }
    } else {
        [self evaluateJavaScript:source];
    }
}


//Synchronus helper for javascript evaluation
- (void)evaluateJavaScript:(NSString *)script {
    __block NSString* _script = script;
    [self.inAppBrowserViewController.webView evaluateJavaScript:script completionHandler:^(id result, NSError *error) {
        if (error == nil) {
            if (result != nil) {
                NSLog(@"%@", result);
            }
        } else {
            NSLog(@"evaluateJavaScript error : %@ : %@", error.localizedDescription, _script);
        }
    }];
}

- (void)injectScriptCode:(CDVInvokedUrlCommand*)command
{
    NSString* jsWrapper = nil;
    
    if ((command.callbackId != nil) && ![command.callbackId isEqualToString:@"INVALID"]) {
        jsWrapper = [NSString stringWithFormat:@"_cdvMessageHandler('%@',JSON.stringify([eval(%%@)]));", command.callbackId];
    }
    [self injectDeferredObject:[command argumentAtIndex:0] withWrapper:jsWrapper];
}

- (void)injectScriptFile:(CDVInvokedUrlCommand*)command
{
    NSString* jsWrapper;
    
    if ((command.callbackId != nil) && ![command.callbackId isEqualToString:@"INVALID"]) {
        jsWrapper = [NSString stringWithFormat:@"(function(d) { var c = d.createElement('script'); c.src = %%@; c.onload = function() { _cdvMessageHandler('%@'); }; d.body.appendChild(c); })(document)", command.callbackId];
    } else {
        jsWrapper = @"(function(d) { var c = d.createElement('script'); c.src = %@; d.body.appendChild(c); })(document)";
    }
    [self injectDeferredObject:[command argumentAtIndex:0] withWrapper:jsWrapper];
}

- (void)injectStyleCode:(CDVInvokedUrlCommand*)command
{
    NSString* jsWrapper;
    
    if ((command.callbackId != nil) && ![command.callbackId isEqualToString:@"INVALID"]) {
        jsWrapper = [NSString stringWithFormat:@"(function(d) { var c = d.createElement('style'); c.innerHTML = %%@; c.onload = function() { _cdvMessageHandler('%@'); }; d.body.appendChild(c); })(document)", command.callbackId];
    } else {
        jsWrapper = @"(function(d) { var c = d.createElement('style'); c.innerHTML = %@; d.body.appendChild(c); })(document)";
    }
    [self injectDeferredObject:[command argumentAtIndex:0] withWrapper:jsWrapper];
}

- (void)injectStyleFile:(CDVInvokedUrlCommand*)command
{
    NSString* jsWrapper;
    
    if ((command.callbackId != nil) && ![command.callbackId isEqualToString:@"INVALID"]) {
        jsWrapper = [NSString stringWithFormat:@"(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %%@; c.onload = function() { _cdvMessageHandler('%@'); }; d.body.appendChild(c); })(document)", command.callbackId];
    } else {
        jsWrapper = @"(function(d) { var c = d.createElement('link'); c.rel='stylesheet', c.type='text/css'; c.href = %@; d.body.appendChild(c); })(document)";
    }
    [self injectDeferredObject:[command argumentAtIndex:0] withWrapper:jsWrapper];
}

- (BOOL)isValidCallbackId:(NSString *)callbackId
{
    NSError *err = nil;
    // Initialize on first use
    if (self.callbackIdPattern == nil) {
        self.callbackIdPattern = [NSRegularExpression regularExpressionWithPattern:@"^InAppBrowser[0-9]{1,10}$" options:0 error:&err];
        if (err != nil) {
            // Couldn't initialize Regex; No is safer than Yes.
            return NO;
        }
    }
    if ([self.callbackIdPattern firstMatchInString:callbackId options:0 range:NSMakeRange(0, [callbackId length])]) {
        return YES;
    }
    return NO;
}

/**
 * The message handler bridge provided for the InAppBrowser is capable of executing any oustanding callback belonging
 * to the InAppBrowser plugin. Care has been taken that other callbacks cannot be triggered, and that no
 * other code execution is possible.
 */
- (void)webView:(WKWebView *)theWebView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
    
    NSURL* url = navigationAction.request.URL;
    NSURL* mainDocumentURL = navigationAction.request.mainDocumentURL;
    BOOL isTopLevelNavigation = [url isEqual:mainDocumentURL];
    BOOL shouldStart = YES;
    BOOL useBeforeLoad = NO;
    NSString* httpMethod = navigationAction.request.HTTPMethod;
    NSString* errorMessage = nil;
    
    if([_beforeload isEqualToString:@"post"]){
        //TODO handle POST requests by preserving POST data then remove this condition
        errorMessage = @"beforeload doesn't yet support POST requests";
    }
    else if(isTopLevelNavigation && (
           [_beforeload isEqualToString:@"yes"]
       || ([_beforeload isEqualToString:@"get"] && [httpMethod isEqualToString:@"GET"])
    // TODO comment in when POST requests are handled
    // || ([_beforeload isEqualToString:@"post"] && [httpMethod isEqualToString:@"POST"])
    )){
        useBeforeLoad = YES;
    }

    // When beforeload, on first URL change, initiate JS callback. Only after the beforeload event, continue.
    if (_waitForBeforeload && useBeforeLoad) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsDictionary:@{@"type":@"beforeload", @"url":[url absoluteString]}];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
        decisionHandler(WKNavigationActionPolicyCancel);
        return;
    }
    
    if(errorMessage != nil){
        NSLog(@"%@", errorMessage);
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                      messageAsDictionary:@{@"type":@"loaderror", @"url":[url absoluteString], @"code": @"-1", @"message": errorMessage}];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
    
    //if is an app store, tel, sms, mailto or geo link, let the system handle it, otherwise it fails to load it
    NSArray * allowedSchemes = @[@"itms-appss", @"itms-apps", @"tel", @"sms", @"mailto", @"geo"];
    if ([allowedSchemes containsObject:[url scheme]]) {
        [theWebView stopLoading];
        [self openInSystem:url];
        shouldStart = NO;
    }
    else if ((self.callbackId != nil) && isTopLevelNavigation) {
        // Send a loadstart event for each top-level navigation (includes redirects).
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsDictionary:@{@"type":@"loadstart", @"url":[url absoluteString]}];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }

    if (useBeforeLoad) {
        _waitForBeforeload = YES;
    }
    
    if(shouldStart){
        // Fix GH-417 & GH-424: Handle non-default target attribute
        // Based on https://stackoverflow.com/a/25713070/777265
        if (!navigationAction.targetFrame){
            [theWebView loadRequest:navigationAction.request];
            decisionHandler(WKNavigationActionPolicyCancel);
        }else{
            decisionHandler(WKNavigationActionPolicyAllow);
        }
    }else{
        decisionHandler(WKNavigationActionPolicyCancel);
    }
}

#pragma mark WKScriptMessageHandler delegate
- (void)userContentController:(nonnull WKUserContentController *)userContentController didReceiveScriptMessage:(nonnull WKScriptMessage *)message {
    
    CDVPluginResult* pluginResult = nil;
    
    if([message.body isKindOfClass:[NSDictionary class]]){
        NSDictionary* messageContent = (NSDictionary*) message.body;
        NSString* scriptCallbackId = messageContent[@"id"];
        
        if([messageContent objectForKey:@"d"]){
            NSString* scriptResult = messageContent[@"d"];
            NSError* __autoreleasing error = nil;
            NSData* decodedResult = [NSJSONSerialization JSONObjectWithData:[scriptResult dataUsingEncoding:NSUTF8StringEncoding] options:kNilOptions error:&error];
            if ((error == nil) && [decodedResult isKindOfClass:[NSArray class]]) {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:(NSArray*)decodedResult];
            } else {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_JSON_EXCEPTION];
            }
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:@[]];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:scriptCallbackId];
    }else if(self.callbackId != nil){
        // Send a message event
        NSString* messageContent = (NSString*) message.body;
        NSError* __autoreleasing error = nil;
        NSData* decodedResult = [NSJSONSerialization JSONObjectWithData:[messageContent dataUsingEncoding:NSUTF8StringEncoding] options:kNilOptions error:&error];
        if (error == nil) {
            NSMutableDictionary* dResult = [NSMutableDictionary new];
            [dResult setValue:@"message" forKey:@"type"];
            [dResult setObject:decodedResult forKey:@"data"];
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:dResult];
            [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
        }
    }
}

- (void)didStartProvisionalNavigation:(WKWebView*)theWebView
{
    NSLog(@"didStartProvisionalNavigation");
//    self.inAppBrowserViewController.currentURL = theWebView.URL;
}

- (void)didFinishNavigation:(WKWebView*)theWebView
{
    if (self.inAppBrowserViewController.footerTitleLabel != nil && self.inAppBrowserViewController.browserOptions.footertitle != nil) {
        self.inAppBrowserViewController.footerTitleLabel.text = self.inAppBrowserViewController.browserOptions.footertitle;
    }

    if (self.callbackId != nil) {
        NSString* url = [theWebView.URL absoluteString];
        if(url == nil){
            if(self.inAppBrowserViewController.currentURL != nil){
                url = [self.inAppBrowserViewController.currentURL absoluteString];
            }else{
                url = @"";
            }
        }
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsDictionary:@{@"type":@"loadstop", @"url":url}];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}

- (void)webView:(WKWebView*)theWebView didFailNavigation:(NSError*)error
{
    // Check if this is the modal WebView
    if (theWebView == self.inAppBrowserViewController.modalWebView) {
        // Handle modal WebView navigation error
        NSLog(@"Modal WebView navigation error: %@", [error localizedDescription]);
        return;
    }
    
    if (self.callbackId != nil) {
        NSString* url = [theWebView.URL absoluteString];
        if(url == nil){
            if(self.inAppBrowserViewController.currentURL != nil){
                url = [self.inAppBrowserViewController.currentURL absoluteString];
            }else{
                url = @"";
            }
        }
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                      messageAsDictionary:@{@"type":@"loaderror", @"url":url, @"code": [NSNumber numberWithInteger:error.code], @"message": error.localizedDescription}];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}

- (void)browserExit
{
    if (self.callbackId != nil) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsDictionary:@{@"type":@"exit"}];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
        self.callbackId = nil;
    }
    
    [self.inAppBrowserViewController.configuration.userContentController removeScriptMessageHandlerForName:IAB_BRIDGE_NAME];
    self.inAppBrowserViewController.configuration = nil;
    
    [self.inAppBrowserViewController.webView stopLoading];
    [self.inAppBrowserViewController.webView removeFromSuperview];
    [self.inAppBrowserViewController.webView setUIDelegate:nil];
    [self.inAppBrowserViewController.webView setNavigationDelegate:nil];
    self.inAppBrowserViewController.webView = nil;
    
    // Set navigationDelegate to nil to ensure no callbacks are received from it.
    self.inAppBrowserViewController.navigationDelegate = nil;
    self.inAppBrowserViewController = nil;

    // Set tmpWindow to hidden to make main webview responsive to touch again
    // Based on https://stackoverflow.com/questions/4544489/how-to-remove-a-uiwindow
    self->tmpWindow.hidden = YES;
    self->tmpWindow = nil;
}

@end //CDVWKInAppBrowser

#pragma mark CDVWKInAppBrowserViewController

@implementation CDVWKInAppBrowserViewController

@synthesize currentURL, browserOptions;

CGFloat lastReducedStatusBarHeight = 0.0;
BOOL isExiting = FALSE;

- (id)initWithBrowserOptions: (CDVInAppBrowserOptions*) browserOptions andSettings:(NSDictionary *)settings
{
    self = [super init];
    if (self != nil) {
        self.browserOptions = browserOptions;
        _settings = settings;
        self.webViewUIDelegate = [[CDVWKInAppBrowserUIDelegate alloc] initWithTitle:[[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleDisplayName"]];
        [self.webViewUIDelegate setViewController:self];
        
        [self createViews];
    }
    
    return self;
}

-(void)dealloc {
    //NSLog(@"dealloc");
    
    // Clean up modal WebView
    if (self.isModalVisible) {
        [self hideModalWebView];
    }
    
    // Clean up menu
    if (self.isMenuVisible) {
        [self hideMenu];
    }
}

- (void)createViews
{
    // We create the views in code for primarily for ease of upgrades and not requiring an external .xib to be included
    
    // Set main view background to light gray (#F0F0F0) like Android version
    self.view.backgroundColor = [UIColor colorWithRed:240.0/255.0 green:240.0/255.0 blue:240.0/255.0 alpha:1.0];
    
    CGRect webViewBounds = self.view.bounds;
    BOOL toolbarIsAtBottom = ![browserOptions.toolbarposition isEqualToString:kInAppBrowserToolbarBarPositionTop];
    
    // Add top margin to avoid camera punch area (24pt equivalent)
    CGFloat topMargin = 24.0;
    webViewBounds.origin.y += topMargin;
    webViewBounds.size.height -= topMargin;
    
    // Calculate proper height reduction based on footer vs toolbar
    if (browserOptions.footer) {
        webViewBounds.size.height -= 120.0; // Updated footer height to match Android (120pt)
    } else if (browserOptions.location) {
        webViewBounds.size.height -= FOOTER_HEIGHT;
    } else {
        webViewBounds.size.height -= TOOLBAR_HEIGHT;
    }
    
    // Apply 16pt margins to WebView bounds to create spacing from screen edges
    webViewBounds.origin.x += 16.0;
    webViewBounds.origin.y += 16.0;
    webViewBounds.size.width -= 32.0; // 16pt on each side
    webViewBounds.size.height -= 32.0; // 16pt on top and bottom
    
    WKUserContentController* userContentController = [[WKUserContentController alloc] init];
    
    WKWebViewConfiguration* configuration = [[WKWebViewConfiguration alloc] init];
    
    NSString *userAgent = configuration.applicationNameForUserAgent;
    if (
        [self settingForKey:@"OverrideUserAgent"] == nil &&
        [self settingForKey:@"AppendUserAgent"] != nil
        ) {
        userAgent = [NSString stringWithFormat:@"%@ %@", userAgent, [self settingForKey:@"AppendUserAgent"]];
    }
    configuration.applicationNameForUserAgent = userAgent;
    configuration.userContentController = userContentController;
#if __has_include(<Cordova/CDVWebViewProcessPoolFactory.h>)
    configuration.processPool = [[CDVWebViewProcessPoolFactory sharedFactory] sharedProcessPool];
#elif __has_include("CDVWKProcessPoolFactory.h")
    configuration.processPool = [[CDVWKProcessPoolFactory sharedFactory] sharedProcessPool];
#endif
    [configuration.userContentController addScriptMessageHandler:self name:IAB_BRIDGE_NAME];
    
    //WKWebView options
    configuration.allowsInlineMediaPlayback = browserOptions.allowinlinemediaplayback;
    configuration.ignoresViewportScaleLimits = browserOptions.enableviewportscale;
    if(browserOptions.mediaplaybackrequiresuseraction == YES){
        configuration.mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeAll;
    }else{
        configuration.mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone;
    }
    
    if (@available(iOS 13.0, *)) {
        NSString *contentMode = [self settingForKey:@"PreferredContentMode"];
        if ([contentMode isEqual: @"mobile"]) {
            configuration.defaultWebpagePreferences.preferredContentMode = WKContentModeMobile;
        } else if ([contentMode  isEqual: @"desktop"]) {
            configuration.defaultWebpagePreferences.preferredContentMode = WKContentModeDesktop;
        }
        
    }
    

    self.webView = [[WKWebView alloc] initWithFrame:webViewBounds configuration:configuration];

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 160400
    // With the introduction of iOS 16.4 the webview is no longer inspectable by default.
    // We'll honor that change for release builds, but will still allow inspection on debug builds by default.
    // We also introduce an override option, so consumers can influence this decision in their own build.
    if (@available(iOS 16.4, *)) {
#ifdef DEBUG
        BOOL allowWebviewInspectionDefault = YES;
#else
        BOOL allowWebviewInspectionDefault = NO;
#endif
        self.webView.inspectable = [_settings cordovaBoolSettingForKey:@"InspectableWebview" defaultValue:allowWebviewInspectionDefault];
    }
#endif

    // Create a container view for the WebView with rounded corners
    UIView *webViewContainer = [[UIView alloc] initWithFrame:webViewBounds];
    webViewContainer.backgroundColor = [UIColor whiteColor];
    webViewContainer.layer.cornerRadius = 20.0; // 20pt border radius to match Android
    webViewContainer.layer.masksToBounds = YES; // Clip WebView content to rounded corners
    webViewContainer.clipsToBounds = YES;
    
    // Add the WebView to the container
    [webViewContainer addSubview:self.webView];
    
    // Set WebView to fill the container
    self.webView.frame = CGRectMake(0, 0, webViewContainer.frame.size.width, webViewContainer.frame.size.height);
    self.webView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    
    // Add the container to the main view
    [self.view addSubview:webViewContainer];
    [self.view sendSubviewToBack:webViewContainer];
    
    
    self.webView.navigationDelegate = self;
    self.webView.UIDelegate = self.webViewUIDelegate;
    self.webView.backgroundColor = [UIColor whiteColor];
    if ([self settingForKey:@"OverrideUserAgent"] != nil) {
        self.webView.customUserAgent = [self settingForKey:@"OverrideUserAgent"];
    }
    
    self.webView.clearsContextBeforeDrawing = YES;
    self.webView.clipsToBounds = YES;
    self.webView.contentMode = UIViewContentModeScaleToFill;
    self.webView.multipleTouchEnabled = YES;
    self.webView.opaque = YES;
    self.webView.userInteractionEnabled = YES;
    if (@available(iOS 11.0, *)) {
        self.webView.scrollView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentAutomatic;
    } else {
        // This is deprecated but needed for iOS 10 and below
        #pragma clang diagnostic push
        #pragma clang diagnostic ignored "-Wdeprecated-declarations"
        self.automaticallyAdjustsScrollViewInsets = YES;
        #pragma clang diagnostic pop
    }
    [self.webView setAutoresizingMask:UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth];
    self.webView.allowsLinkPreview = NO;
    self.webView.allowsBackForwardNavigationGestures = NO;
    
    [self.webView.scrollView setContentInsetAdjustmentBehavior:UIScrollViewContentInsetAdjustmentNever];
    
    if (@available(iOS 13.0, *)) {
        self.spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleMedium];
    } else {
        // This is deprecated but needed for iOS 12 and below
        #pragma clang diagnostic push
        #pragma clang diagnostic ignored "-Wdeprecated-declarations"
        self.spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleGray];
        #pragma clang diagnostic pop
    }
    self.spinner.alpha = 1.000;
    self.spinner.autoresizesSubviews = YES;
    self.spinner.autoresizingMask = (UIViewAutoresizingFlexibleLeftMargin | UIViewAutoresizingFlexibleTopMargin | UIViewAutoresizingFlexibleBottomMargin | UIViewAutoresizingFlexibleRightMargin);
    self.spinner.clearsContextBeforeDrawing = NO;
    self.spinner.clipsToBounds = NO;
    self.spinner.contentMode = UIViewContentModeScaleToFill;
    self.spinner.frame = CGRectMake(CGRectGetMidX(self.webView.frame), CGRectGetMidY(self.webView.frame), 20.0, 20.0);
    self.spinner.hidden = NO;
    self.spinner.hidesWhenStopped = YES;
    self.spinner.multipleTouchEnabled = NO;
    self.spinner.opaque = NO;
    self.spinner.userInteractionEnabled = NO;
    [self.spinner stopAnimating];
    
    float toolbarY = toolbarIsAtBottom ? self.view.bounds.size.height - TOOLBAR_HEIGHT : 0.0;
    CGRect toolbarFrame = CGRectMake(0.0, toolbarY, self.view.bounds.size.width, TOOLBAR_HEIGHT);

    self.toolbar = [[UIToolbar alloc] initWithFrame:toolbarFrame];
    self.toolbar.barTintColor = [UIColor colorWithHexString:@"#F2F2F2"];
    self.toolbar.autoresizingMask = toolbarIsAtBottom ? (UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleTopMargin) : UIViewAutoresizingFlexibleWidth;
    
    CGFloat labelInset = 5.0;
    float locationBarY = toolbarIsAtBottom ? self.view.bounds.size.height - FOOTER_HEIGHT : self.view.bounds.size.height - LOCATIONBAR_HEIGHT;
    
    self.addressLabel = [[UILabel alloc] initWithFrame:CGRectMake(labelInset, locationBarY, self.view.bounds.size.width - labelInset, LOCATIONBAR_HEIGHT)];
    self.addressLabel.adjustsFontSizeToFitWidth = NO;
    self.addressLabel.alpha = 1.000;
    self.addressLabel.autoresizesSubviews = YES;
    self.addressLabel.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleRightMargin | UIViewAutoresizingFlexibleTopMargin;
    self.addressLabel.backgroundColor = [UIColor clearColor];
    self.addressLabel.baselineAdjustment = UIBaselineAdjustmentAlignCenters;
    self.addressLabel.clearsContextBeforeDrawing = YES;
    self.addressLabel.clipsToBounds = YES;
    self.addressLabel.contentMode = UIViewContentModeScaleToFill;
    self.addressLabel.enabled = YES;
    self.addressLabel.hidden = NO;
    self.addressLabel.lineBreakMode = NSLineBreakByTruncatingTail;
    
    if ([self.addressLabel respondsToSelector:NSSelectorFromString(@"setMinimumScaleFactor:")]) {
        [self.addressLabel setValue:@(10.0/[UIFont labelFontSize]) forKey:@"minimumScaleFactor"];
    } else if ([self.addressLabel respondsToSelector:NSSelectorFromString(@"setMinimumFontSize:")]) {
        [self.addressLabel setValue:@(10.0) forKey:@"minimumFontSize"];
    }
    
    self.addressLabel.multipleTouchEnabled = NO;
    self.addressLabel.numberOfLines = 1;
    self.addressLabel.opaque = NO;
    self.addressLabel.shadowOffset = CGSizeMake(0.0, -1.0);
    self.addressLabel.text = NSLocalizedString(@"Loading...", nil);
    self.addressLabel.textAlignment = NSTextAlignmentLeft;
    self.addressLabel.textColor = [UIColor colorWithWhite:1.000 alpha:1.000];
    self.addressLabel.userInteractionEnabled = NO;
    
    self.AIButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.AIButton.backgroundColor = [UIColor colorWithHexString:@"#AB4CFF"];
    [self.AIButton setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    self.AIButton.layer.cornerRadius = 8.0f;
    self.AIButton.layer.masksToBounds = YES;
    self.AIButton.contentEdgeInsets = UIEdgeInsetsMake(12, 16, 12, 16);
    self.AIButton.titleLabel.font = [UIFont systemFontOfSize:14.0]; // Match Android text size
    [self.AIButton setTitle:@"AI" forState:UIControlStateNormal];
    [self.AIButton addTarget:self action:@selector(injectScript) forControlEvents:UIControlEventTouchUpInside];
    [self.AIButton addTarget:self action:@selector(buttonTouchDown:) forControlEvents:UIControlEventTouchDown];
    [self.AIButton addTarget:self action:@selector(buttonTouchUp:) forControlEvents:UIControlEventTouchUpInside | UIControlEventTouchUpOutside];
    [self.toolbar addSubview:self.AIButton];

    // Create three-dot menu button
    self.menuButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.menuButton.backgroundColor = [UIColor colorWithHexString:@"#E0E0E0"];
    [self.menuButton setTitleColor:[UIColor blackColor] forState:UIControlStateNormal];
    self.menuButton.layer.cornerRadius = 8.0f;
    self.menuButton.layer.masksToBounds = YES;
    self.menuButton.contentEdgeInsets = UIEdgeInsetsMake(12, 12, 12, 12); // Smaller padding than AI button
    self.menuButton.titleLabel.font = [UIFont systemFontOfSize:14.0];
    [self.menuButton setTitle:@"⋮" forState:UIControlStateNormal]; // Three dots
    [self.menuButton addTarget:self action:@selector(showMenu) forControlEvents:UIControlEventTouchUpInside];
    [self.menuButton addTarget:self action:@selector(buttonTouchDown:) forControlEvents:UIControlEventTouchDown];
    [self.menuButton addTarget:self action:@selector(buttonTouchUp:) forControlEvents:UIControlEventTouchUpInside | UIControlEventTouchUpOutside];
    [self.toolbar addSubview:self.menuButton];

    self.footerTitleLabel = [[UILabel alloc] init];
    self.footerTitleLabel.textAlignment = NSTextAlignmentCenter;
    self.footerTitleLabel.textColor = [UIColor blackColor];
    self.footerTitleLabel.font = [UIFont systemFontOfSize:28.0]; // Match Android text size
    [self.toolbar addSubview:self.footerTitleLabel];

    self.closeButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.closeButton.backgroundColor = [UIColor colorWithHexString:@"#E0E0E0"];
    [self.closeButton setTitleColor:[UIColor blackColor] forState:UIControlStateNormal];
    self.closeButton.layer.cornerRadius = 8.0f;
    self.closeButton.layer.masksToBounds = YES;
    self.closeButton.contentEdgeInsets = UIEdgeInsetsMake(12, 16, 12, 16);
    self.closeButton.titleLabel.font = [UIFont systemFontOfSize:14.0]; // Match Android text size
    [self.closeButton setTitle:@"Close" forState:UIControlStateNormal];
    [self.closeButton addTarget:self action:@selector(closeOrGoBack) forControlEvents:UIControlEventTouchUpInside];
    [self.closeButton addTarget:self action:@selector(buttonTouchDown:) forControlEvents:UIControlEventTouchDown];
    [self.closeButton addTarget:self action:@selector(buttonTouchUp:) forControlEvents:UIControlEventTouchUpInside | UIControlEventTouchUpOutside];
    [self.toolbar addSubview:self.closeButton];
    
    // Initialize close button title
    [self updateCloseButtonTitle];
    
    [self.view addSubview:self.toolbar];
    [self.view addSubview:self.addressLabel];
    [self.view addSubview:self.spinner];
}

- (id)settingForKey:(NSString*)key
{
    return [_settings objectForKey:[key lowercaseString]];
}

- (void) setWebViewFrame : (CGRect) frame {
    NSLog(@"Setting the WebView's frame to %@", NSStringFromCGRect(frame));
    
    // Apply 16pt margins to the frame for the container
    CGRect containerFrame = frame;
    containerFrame.origin.x += 16.0;
    containerFrame.origin.y += 16.0;
    containerFrame.size.width -= 32.0;
    containerFrame.size.height -= 32.0;
    
    // Find the WebView container and update its frame
    UIView *webViewContainer = nil;
    for (UIView *subview in self.view.subviews) {
        if (subview != self.toolbar && subview != self.addressLabel && subview != self.spinner) {
            webViewContainer = subview;
            break;
        }
    }
    
    if (webViewContainer) {
        webViewContainer.frame = containerFrame;
    }
}

- (void)setCloseButtonTitle:(NSString*)title : (NSString*) colorString : (int) buttonIndex
{
    // Update the close button title and color for the footer implementation
    if (title != nil) {
        [self.closeButton setTitle:title forState:UIControlStateNormal];
    }
    
    if (colorString != nil) {
        [self.closeButton setTitleColor:[self colorFromHexString:colorString] forState:UIControlStateNormal];
    }
}

- (void)showLocationBar:(BOOL)show
{
    CGRect locationbarFrame = self.addressLabel.frame;
    
    BOOL toolbarVisible = !self.toolbar.hidden;
    
    // prevent double show/hide
    if (show == !(self.addressLabel.hidden)) {
        return;
    }
    
    if (show) {
        self.addressLabel.hidden = NO;
        
        if (toolbarVisible) {
            // toolBar at the bottom, leave as is
            // put locationBar on top of the toolBar
            
            CGRect webViewBounds = self.view.bounds;
            if (browserOptions.footer) {
                webViewBounds.size.height -= 120.0; // Updated footer height
            } else {
                webViewBounds.size.height -= FOOTER_HEIGHT;
            }
            [self setWebViewFrame:webViewBounds];
            
            locationbarFrame.origin.y = webViewBounds.size.height;
            self.addressLabel.frame = locationbarFrame;
        } else {
            // no toolBar, so put locationBar at the bottom
            
            CGRect webViewBounds = self.view.bounds;
            webViewBounds.size.height -= LOCATIONBAR_HEIGHT;
            [self setWebViewFrame:webViewBounds];
            
            locationbarFrame.origin.y = webViewBounds.size.height;
            self.addressLabel.frame = locationbarFrame;
        }
    } else {
        self.addressLabel.hidden = YES;
        
        if (toolbarVisible) {
            // locationBar is on top of toolBar, hide locationBar
            
            // webView take up whole height less toolBar height
            CGRect webViewBounds = self.view.bounds;
            if (browserOptions.footer) {
                webViewBounds.size.height -= 120.0; // Updated footer height
            } else {
                webViewBounds.size.height -= TOOLBAR_HEIGHT;
            }
            [self setWebViewFrame:webViewBounds];
        } else {
            // no toolBar, expand webView to screen dimensions
            [self setWebViewFrame:self.view.bounds];
        }
    }
}

- (void)showToolBar:(BOOL)show : (NSString *) toolbarPosition
{
    CGRect toolbarFrame = self.toolbar.frame;
    CGRect locationbarFrame = self.addressLabel.frame;
    
    BOOL locationbarVisible = !self.addressLabel.hidden;
    
    // prevent double show/hide
    if (show == !(self.toolbar.hidden)) {
        return;
    }
    
    if (show) {
        self.toolbar.hidden = NO;
        CGRect webViewBounds = self.view.bounds;
        
        if (locationbarVisible) {
            // locationBar at the bottom, move locationBar up
            // put toolBar at the bottom
            if (browserOptions.footer) {
                webViewBounds.size.height -= 120.0; // Updated footer height
            } else {
                webViewBounds.size.height -= FOOTER_HEIGHT;
            }
            locationbarFrame.origin.y = webViewBounds.size.height;
            self.addressLabel.frame = locationbarFrame;
            self.toolbar.frame = toolbarFrame;
        } else {
            // no locationBar, so put toolBar at the bottom
            CGRect webViewBounds = self.view.bounds;
            if (browserOptions.footer) {
                webViewBounds.size.height -= 120.0; // Updated footer height
            } else {
                webViewBounds.size.height -= TOOLBAR_HEIGHT;
            }
            self.toolbar.frame = toolbarFrame;
        }
        
        if ([toolbarPosition isEqualToString:kInAppBrowserToolbarBarPositionTop]) {
            toolbarFrame.origin.y = 0;
            webViewBounds.origin.y += toolbarFrame.size.height;
            [self setWebViewFrame:webViewBounds];
        } else {
            toolbarFrame.origin.y = (webViewBounds.size.height + LOCATIONBAR_HEIGHT);
        }
        [self setWebViewFrame:webViewBounds];
        
    } else {
        self.toolbar.hidden = YES;
        
        if (locationbarVisible) {
            // locationBar is on top of toolBar, hide toolBar
            // put locationBar at the bottom
            
            // webView take up whole height less locationBar height
            CGRect webViewBounds = self.view.bounds;
            webViewBounds.size.height -= LOCATIONBAR_HEIGHT;
            [self setWebViewFrame:webViewBounds];
            
            // move locationBar down
            locationbarFrame.origin.y = webViewBounds.size.height;
            self.addressLabel.frame = locationbarFrame;
        } else {
            // no locationBar, expand webView to screen dimensions
            [self setWebViewFrame:self.view.bounds];
        }
    }
}

- (void)viewDidLoad
{
    [super viewDidLoad];
}

- (void)viewDidDisappear:(BOOL)animated
{
    [super viewDidDisappear:animated];
    
    // Handle browser exit
    if (isExiting && (self.navigationDelegate != nil) && [self.navigationDelegate respondsToSelector:@selector(browserExit)]) {
        [self.navigationDelegate browserExit];
        isExiting = FALSE;
    }
    
    // Ensure modal is hidden when view disappears
    if (self.isModalVisible) {
        [self hideModalWebView];
    }
}

- (UIStatusBarStyle)preferredStatusBarStyle
{
    NSString* statusBarStylePreference = [self settingForKey:@"InAppBrowserStatusBarStyle"];
    if (statusBarStylePreference && [statusBarStylePreference isEqualToString:@"lightcontent"]) {
        return UIStatusBarStyleLightContent;
    } else if (statusBarStylePreference && [statusBarStylePreference isEqualToString:@"darkcontent"]) {
        if (@available(iOS 13.0, *)) {
            return UIStatusBarStyleDarkContent;
        } else {
            return UIStatusBarStyleDefault;
        }
    } else {
        return UIStatusBarStyleDefault;
    }
}

- (BOOL)prefersStatusBarHidden {
    return NO;
}

- (void)close
{
    self.currentURL = nil;
    
    // Hide modal if it's visible
    if (self.isModalVisible) {
        [self hideModalWebView];
    }
    
    // Hide menu if it's visible
    if (self.isMenuVisible) {
        [self hideMenu];
    }
    
    __weak UIViewController* weakSelf = self;
    
    // Run later to avoid the "took a long time" log message.
    dispatch_async(dispatch_get_main_queue(), ^{
        isExiting = TRUE;
        lastReducedStatusBarHeight = 0.0;
        if ([weakSelf respondsToSelector:@selector(presentingViewController)]) {
            [[weakSelf presentingViewController] dismissViewControllerAnimated:YES completion:nil];
        } else {
            [[weakSelf parentViewController] dismissViewControllerAnimated:YES completion:nil];
        }
    });
}

- (void)navigateTo:(NSURL*)url
{
    if ([url.scheme isEqualToString:@"file"]) {
        [self.webView loadFileURL:url allowingReadAccessToURL:url];
    } else {
        NSURLRequest* request = [NSURLRequest requestWithURL:url];
        [self.webView loadRequest:request];
    }
}

- (void)goBack:(id)sender
{
    [self.webView goBack];
}

- (void)goForward:(id)sender
{
    [self.webView goForward];
}

- (void)injectScript
{
    // Toggle modal WebView instead of injecting JavaScript
    if (self.isModalVisible) {
        [self hideModalWebView];
    } else {
        [self showModalWebView];
    }
}

- (void)buttonTouchDown:(UIButton *)sender {
    [UIView animateWithDuration:0.1 animations:^{
        if (sender == self.AIButton) {
            sender.backgroundColor = [UIColor colorWithHexString:@"#8A3FD1"]; // Darker purple
        } else if (sender == self.closeButton) {
            sender.backgroundColor = [UIColor colorWithHexString:@"#BDBDBD"]; // Darker gray
        } else if (sender == self.menuButton) {
            sender.backgroundColor = [UIColor colorWithHexString:@"#BDBDBD"]; // Darker gray
        }
    }];
}

- (void)buttonTouchUp:(UIButton *)sender {
    [UIView animateWithDuration:0.1 animations:^{
        if (sender == self.AIButton) {
            sender.backgroundColor = [UIColor colorWithHexString:@"#AB4CFF"]; // Original purple
        } else if (sender == self.closeButton) {
            sender.backgroundColor = [UIColor colorWithHexString:@"#E0E0E0"]; // Original gray
        } else if (sender == self.menuButton) {
            sender.backgroundColor = [UIColor colorWithHexString:@"#E0E0E0"]; // Original gray
        }
    }];
}

#pragma mark - Modal WebView Methods

- (void)showModalWebView {
    NSLog(@"showModalWebView called, isModalVisible: %@", self.isModalVisible ? @"YES" : @"NO");
    
    if (self.isModalVisible) {
        return;
    }
    
    dispatch_async(dispatch_get_main_queue(), ^{
        // Create modal container (fullscreen overlay)
        self.modalContainer = [[UIView alloc] initWithFrame:self.view.bounds];
        self.modalContainer.backgroundColor = [UIColor colorWithRed:0.0 green:0.0 blue:0.0 alpha:0.5]; // Semi-transparent background
        
        // Create modal WebView container with specific dimensions
        UIView *modalWebViewContainer = [[UIView alloc] init];
        
        // Calculate 80% width and 60% height of screen
        CGFloat screenWidth = self.view.bounds.size.width;
        CGFloat screenHeight = self.view.bounds.size.height;
        CGFloat modalWidth = screenWidth * 0.8; // 80% of screen width
        CGFloat modalHeight = screenHeight * 0.6; // 60% of screen height
        
        // Center the modal
        modalWebViewContainer.frame = CGRectMake(
            (screenWidth - modalWidth) / 2,
            (screenHeight - modalHeight) / 2,
            modalWidth,
            modalHeight
        );
        
        // Add rounded corners and background to modal container
        modalWebViewContainer.backgroundColor = [UIColor whiteColor];
        modalWebViewContainer.layer.cornerRadius = 16.0; // 16pt border radius
        modalWebViewContainer.layer.masksToBounds = YES;
        modalWebViewContainer.clipsToBounds = YES;
        
        // Create modal WebView - NO PADDING, fill entire modal container
        WKWebViewConfiguration *modalConfiguration = [[WKWebViewConfiguration alloc] init];
        modalConfiguration.allowsInlineMediaPlayback = YES;
        modalConfiguration.mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone;
        
        self.modalWebView = [[WKWebView alloc] initWithFrame:CGRectMake(0, 0, modalWidth, modalHeight) configuration:modalConfiguration];
        
        // Configure modal WebView settings
        self.modalWebView.navigationDelegate = self;
        self.modalWebView.backgroundColor = [UIColor whiteColor];
        self.modalWebView.scrollView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
        
        // Set WebViewClient for modal
        self.modalWebView.navigationDelegate = self;
        
        // Add tap gesture to close modal when clicking outside WebView area
        UITapGestureRecognizer *modalTapGesture = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(hideModalWebView)];
        [self.modalContainer addGestureRecognizer:modalTapGesture];
        
        // Add WebView to modal WebView container
        [modalWebViewContainer addSubview:self.modalWebView];
        
        // Add modal WebView container to modal container
        [self.modalContainer addSubview:modalWebViewContainer];
        
        // Add modal container to the main view
        [self.view addSubview:self.modalContainer];
        [self.view bringSubviewToFront:self.modalContainer];
        
        // Load Google.com in modal WebView
        [self.modalWebView loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:@"https://google.com"]]];
        
        self.isModalVisible = YES;
    });
}

- (void)hideModalWebView {
    NSLog(@"hideModalWebView called, isModalVisible: %@", self.isModalVisible ? @"YES" : @"NO");
    
    if (!self.isModalVisible) {
        return;
    }
    
    dispatch_async(dispatch_get_main_queue(), ^{
        NSLog(@"Removing modal from view");
        [self.modalContainer removeFromSuperview];
        self.modalContainer = nil;
        self.modalWebView = nil;
        self.isModalVisible = NO;
        NSLog(@"Modal hidden successfully");
    });
}

#pragma mark - Menu Methods

- (void)showMenu {
    if (self.isMenuVisible) {
        return;
    }
    
    dispatch_async(dispatch_get_main_queue(), ^{
        // Create menu container (semi-transparent overlay)
        self.menuContainer = [[UIView alloc] initWithFrame:self.view.bounds];
        self.menuContainer.backgroundColor = [UIColor colorWithRed:0.0 green:0.0 blue:0.0 alpha:0.3]; // Semi-transparent background
        
        // Add tap gesture to close menu when tapping outside
        UITapGestureRecognizer *tapGesture = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(hideMenu)];
        [self.menuContainer addGestureRecognizer:tapGesture];
        
        // Create menu popup container
        UIView *menuPopup = [[UIView alloc] init];
        menuPopup.backgroundColor = [UIColor whiteColor];
        menuPopup.layer.cornerRadius = 12.0;
        menuPopup.layer.shadowColor = [UIColor blackColor].CGColor;
        menuPopup.layer.shadowOffset = CGSizeMake(0, 2);
        menuPopup.layer.shadowOpacity = 0.3;
        menuPopup.layer.shadowRadius = 4.0;
        
        // Calculate menu position (bottom-right area above footer)
        CGFloat menuWidth = 120.0;
        CGFloat menuHeight = 100.0;
        CGFloat menuX = self.view.bounds.size.width - menuWidth - 16; // 16pt from right edge
        CGFloat menuY = self.view.bounds.size.height - 120.0 - menuHeight - 16; // Above footer
        
        menuPopup.frame = CGRectMake(menuX, menuY, menuWidth, menuHeight);
        
        // Create menu items
        UIButton *forwardButton = [UIButton buttonWithType:UIButtonTypeSystem];
        forwardButton.frame = CGRectMake(0, 0, menuWidth, 50);
        [forwardButton setTitle:@"Forward" forState:UIControlStateNormal];
        [forwardButton setTitleColor:[UIColor blackColor] forState:UIControlStateNormal];
        forwardButton.titleLabel.font = [UIFont systemFontOfSize:16.0];
        forwardButton.backgroundColor = [UIColor clearColor];
        [forwardButton addTarget:self action:@selector(goForward) forControlEvents:UIControlEventTouchUpInside];
        [menuPopup addSubview:forwardButton];
        
        // Add separator
        UIView *separator = [[UIView alloc] initWithFrame:CGRectMake(8, 50, menuWidth - 16, 1)];
        separator.backgroundColor = [UIColor colorWithRed:0.9 green:0.9 blue:0.9 alpha:1.0];
        [menuPopup addSubview:separator];
        
        UIButton *refreshButton = [UIButton buttonWithType:UIButtonTypeSystem];
        refreshButton.frame = CGRectMake(0, 51, menuWidth, 49);
        [refreshButton setTitle:@"Refresh" forState:UIControlStateNormal];
        [refreshButton setTitleColor:[UIColor blackColor] forState:UIControlStateNormal];
        refreshButton.titleLabel.font = [UIFont systemFontOfSize:16.0];
        refreshButton.backgroundColor = [UIColor clearColor];
        [refreshButton addTarget:self action:@selector(refreshPage) forControlEvents:UIControlEventTouchUpInside];
        [menuPopup addSubview:refreshButton];
        
        // Disable forward button if can't go forward
        forwardButton.enabled = self.webView.canGoForward;
        if (!forwardButton.enabled) {
            [forwardButton setTitleColor:[UIColor lightGrayColor] forState:UIControlStateNormal];
        }
        
        [self.menuContainer addSubview:menuPopup];
        [self.view addSubview:self.menuContainer];
        [self.view bringSubviewToFront:self.menuContainer];
        
        self.isMenuVisible = YES;
    });
}

- (void)hideMenu {
    if (!self.isMenuVisible) {
        return;
    }
    
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.menuContainer removeFromSuperview];
        self.menuContainer = nil;
        self.isMenuVisible = NO;
    });
}

- (void)goForward {
    if (self.webView.canGoForward) {
        [self.webView goForward];
        // Navigation state will be updated in didFinishNavigation
    }
    [self hideMenu];
}

- (void)refreshPage {
    [self.webView reload];
    [self hideMenu];
}

- (void)updateCloseButtonTitle {
    // Always check the current state of the WebView
    if (self.webView.canGoBack) {
        [self.closeButton setTitle:@"Back" forState:UIControlStateNormal];
    } else {
        [self.closeButton setTitle:@"Close" forState:UIControlStateNormal];
    }
}

- (void)closeOrGoBack {
    // Check if we can go back
    if (self.webView.canGoBack) {
        // Go back in WebView history
        [self.webView goBack];
        // Navigation state will be updated in didFinishNavigation after the back navigation completes
    } else {
        // Close the InAppBrowser
        [self close];
    }
}

- (void)viewWillAppear:(BOOL)animated
{
    [self rePositionViews];
    
    [super viewWillAppear:animated];
}

- (void)viewWillDisappear:(BOOL)animated
{
    [super viewWillDisappear:animated];
    
    // Hide modal if it's visible when view disappears
    if (self.isModalVisible) {
        [self hideModalWebView];
    }
    
    // Hide menu if it's visible when view disappears
    if (self.isMenuVisible) {
        [self hideMenu];
    }
}



- (float) getStatusBarOffset {
    if (@available(iOS 13.0, *)) {
        UIWindowScene *windowScene = (UIWindowScene *)UIApplication.sharedApplication.connectedScenes.allObjects.firstObject;
        if ([windowScene isKindOfClass:[UIWindowScene class]]) {
            return windowScene.statusBarManager.statusBarFrame.size.height;
        }
    }
    // This is deprecated but needed for iOS 12 and below
    #pragma clang diagnostic push
    #pragma clang diagnostic ignored "-Wdeprecated-declarations"
    return (float) [[UIApplication sharedApplication] statusBarFrame].size.height;
    #pragma clang diagnostic pop
}

- (void) rePositionViews {
    CGFloat statusBarHeight = [self getStatusBarOffset];
    CGFloat footerHeight = 120.0; // Updated to match Android (120pt)
    
    // Calculate the available height for the webView
    CGFloat availableHeight = self.view.bounds.size.height - statusBarHeight;
    
    if (browserOptions.footer) {
        // When footer is enabled, reduce available height for webView
        availableHeight -= footerHeight;
        
        // Position footer at the bottom with 16pt padding
        CGRect footerFrame = CGRectMake(0, self.view.bounds.size.height - footerHeight, self.view.bounds.size.width, footerHeight);
        self.toolbar.frame = footerFrame;
        
        // Position webView container with 16pt margins and account for status bar
        CGRect webViewContainerFrame = CGRectMake(16, statusBarHeight + 16, self.view.bounds.size.width - 32, availableHeight - 32);
        
        // Find the WebView container (first subview that's not toolbar, addressLabel, or spinner)
        UIView *webViewContainer = nil;
        for (UIView *subview in self.view.subviews) {
            if (subview != self.toolbar && subview != self.addressLabel && subview != self.spinner) {
                webViewContainer = subview;
                break;
            }
        }
        
        if (webViewContainer) {
            webViewContainer.frame = webViewContainerFrame;
        }
        
        // Position buttons and title in footer with 16pt padding
        [self.AIButton sizeToFit];
        CGRect aiButtonFrame = self.AIButton.frame;
        aiButtonFrame.origin.x = 16; // 16pt padding from left
        aiButtonFrame.origin.y = (footerHeight - aiButtonFrame.size.height) / 2;
        self.AIButton.frame = aiButtonFrame;

        // Position menu button if enabled
        if (browserOptions.menu) {
            [self.menuButton sizeToFit];
            CGRect menuButtonFrame = self.menuButton.frame;
            menuButtonFrame.origin.x = CGRectGetMaxX(aiButtonFrame) + 8; // 8pt spacer after AI button
            menuButtonFrame.origin.y = (footerHeight - menuButtonFrame.size.height) / 2;
            self.menuButton.frame = menuButtonFrame;
            self.menuButton.hidden = NO;
        } else {
            self.menuButton.hidden = YES;
        }

        [self.closeButton sizeToFit];
        CGRect closeButtonFrame = self.closeButton.frame;
        closeButtonFrame.origin.x = self.view.bounds.size.width - closeButtonFrame.size.width - 16; // 16pt padding from right
        closeButtonFrame.origin.y = (footerHeight - closeButtonFrame.size.height) / 2;
        self.closeButton.frame = closeButtonFrame;

        // Calculate title position based on whether menu is visible
        CGFloat titleLabelX;
        if (browserOptions.menu) {
            titleLabelX = CGRectGetMaxX(self.menuButton.frame) + 16;
        } else {
            titleLabelX = CGRectGetMaxX(aiButtonFrame) + 16;
        }
        CGFloat titleLabelWidth = CGRectGetMinX(closeButtonFrame) - titleLabelX - 16;
        self.footerTitleLabel.frame = CGRectMake(titleLabelX, 0, titleLabelWidth, footerHeight);
        
    } else {
        // Standard toolbar positioning (when footer is disabled)
        CGRect viewBounds = self.view.bounds;
        viewBounds.origin.y = statusBarHeight;
        viewBounds.size.height -= statusBarHeight;
        
        // account for web view height portion that may have been reduced by a previous call to this method
        viewBounds.size.height = viewBounds.size.height + lastReducedStatusBarHeight;
        lastReducedStatusBarHeight = statusBarHeight;
        
        if ((browserOptions.toolbar) && ([browserOptions.toolbarposition isEqualToString:kInAppBrowserToolbarBarPositionTop])) {
            // if we have to display the toolbar on top of the web view, we need to account for its height
            viewBounds.origin.y += TOOLBAR_HEIGHT;
            self.toolbar.frame = CGRectMake(self.toolbar.frame.origin.x, statusBarHeight, self.toolbar.frame.size.width, self.toolbar.frame.size.height);
        }
        
        // Apply 16pt margins to WebView container when footer is disabled
        viewBounds.origin.x += 16.0;
        viewBounds.origin.y += 16.0;
        viewBounds.size.width -= 32.0;
        viewBounds.size.height -= 32.0;
        
        // Find the WebView container and update its frame
        UIView *webViewContainer = nil;
        for (UIView *subview in self.view.subviews) {
            if (subview != self.toolbar && subview != self.addressLabel && subview != self.spinner) {
                webViewContainer = subview;
                break;
            }
        }
        
        if (webViewContainer) {
            webViewContainer.frame = viewBounds;
        }
    }
}

// Helper function to convert hex color string to UIColor
// Assumes input like "#00FF00" (#RRGGBB).
// Taken from https://stackoverflow.com/questions/1560081/how-can-i-create-a-uicolor-from-a-hex-string
- (UIColor *)colorFromHexString:(NSString *)hexString {
    unsigned rgbValue = 0;
    NSScanner *scanner = [NSScanner scannerWithString:hexString];
    [scanner setScanLocation:1]; // bypass '#' character
    [scanner scanHexInt:&rgbValue];
    return [UIColor colorWithRed:((rgbValue & 0xFF0000) >> 16)/255.0 green:((rgbValue & 0xFF00) >> 8)/255.0 blue:(rgbValue & 0xFF)/255.0 alpha:1.0];
}

#pragma mark WKNavigationDelegate

- (void)webView:(WKWebView *)theWebView didStartProvisionalNavigation:(WKNavigation *)navigation{
    
    // Check if this is the modal WebView
    if (theWebView == self.modalWebView) {
        // Handle modal WebView navigation
        return;
    }
    
    // loading url, start spinner, update back/forward
    
    self.addressLabel.text = NSLocalizedString(@"Loading...", nil);
    self.backButton.enabled = theWebView.canGoBack;
    self.forwardButton.enabled = theWebView.canGoForward;
    
    // Update close button title based on current navigation state
    [self updateCloseButtonTitle];
    
    NSLog(browserOptions.hidespinner ? @"Yes" : @"No");
    if(!browserOptions.hidespinner) {
        [self.spinner startAnimating];
    }
    
    return [self.navigationDelegate didStartProvisionalNavigation:theWebView];
}

- (void)webView:(WKWebView *)theWebView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler
{
    // Check if this is the modal WebView
    if (theWebView == self.modalWebView) {
        // Allow all navigation in modal WebView
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }
    
    NSURL *url = navigationAction.request.URL;
    NSURL *mainDocumentURL = navigationAction.request.mainDocumentURL;
    
    BOOL isTopLevelNavigation = [url isEqual:mainDocumentURL];
    
    if (isTopLevelNavigation) {
        self.currentURL = url;
    }
    
    [self.navigationDelegate webView:theWebView decidePolicyForNavigationAction:navigationAction decisionHandler:decisionHandler];
}

- (void)webView:(WKWebView *)theWebView didFinishNavigation:(WKNavigation *)navigation
{
    // Check if this is the modal WebView
    if (theWebView == self.modalWebView) {
        // Handle modal WebView navigation completion
        return;
    }
    
    // update url, stop spinner, update back/forward
    
    self.addressLabel.text = [self.currentURL absoluteString];
    self.backButton.enabled = theWebView.canGoBack;
    self.forwardButton.enabled = theWebView.canGoForward;
    theWebView.scrollView.contentInset = UIEdgeInsetsZero;
    
    // Update close button title based on current navigation state
    [self updateCloseButtonTitle];
    
    [self.spinner stopAnimating];
    
    [self.navigationDelegate didFinishNavigation:theWebView];
}
    
- (void)webView:(WKWebView*)theWebView failedNavigation:(NSString*) delegateName withError:(nonnull NSError *)error{
    // Check if this is the modal WebView
    if (theWebView == self.modalWebView) {
        // Handle modal WebView navigation error
        NSLog(@"Modal WebView navigation error: %@", [error localizedDescription]);
        return;
    }
    
    // log fail message, stop spinner, update back/forward
    NSLog(@"webView:%@ - %ld: %@", delegateName, (long)error.code, [error localizedDescription]);
    
    self.backButton.enabled = theWebView.canGoBack;
    self.forwardButton.enabled = theWebView.canGoForward;
    [self.spinner stopAnimating];
    
    self.addressLabel.text = NSLocalizedString(@"Load Error", nil);
    
    [self.navigationDelegate webView:theWebView didFailNavigation:error];
}

- (void)webView:(WKWebView*)theWebView didFailNavigation:(null_unspecified WKNavigation *)navigation withError:(nonnull NSError *)error
{
    [self webView:theWebView failedNavigation:@"didFailNavigation" withError:error];
}
    
- (void)webView:(WKWebView*)theWebView didFailProvisionalNavigation:(null_unspecified WKNavigation *)navigation withError:(nonnull NSError *)error
{
    [self webView:theWebView failedNavigation:@"didFailProvisionalNavigation" withError:error];
}

#pragma mark WKScriptMessageHandler delegate
- (void)userContentController:(nonnull WKUserContentController *)userContentController didReceiveScriptMessage:(nonnull WKScriptMessage *)message {
    if (![message.name isEqualToString:IAB_BRIDGE_NAME]) {
        return;
    }
    //NSLog(@"Received script message %@", message.body);
    [self.navigationDelegate userContentController:userContentController didReceiveScriptMessage:message];
}

#pragma mark CDVScreenOrientationDelegate

- (BOOL)shouldAutorotate
{
    if ((self.orientationDelegate != nil) && [self.orientationDelegate respondsToSelector:@selector(shouldAutorotate)]) {
        return [self.orientationDelegate shouldAutorotate];
    }
    return YES;
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations
{
    if ((self.orientationDelegate != nil) && [self.orientationDelegate respondsToSelector:@selector(supportedInterfaceOrientations)]) {
        return [self.orientationDelegate supportedInterfaceOrientations];
    }
    
    return 1 << UIInterfaceOrientationPortrait;
}

- (void)viewWillTransitionToSize:(CGSize)size withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
{
    [coordinator animateAlongsideTransition:^(id<UIViewControllerTransitionCoordinatorContext> context)
    {
        [self rePositionViews];
        
        // Reposition modal if it's visible
        if (self.isModalVisible && self.modalContainer) {
            // Update modal container frame
            self.modalContainer.frame = CGRectMake(0, 0, size.width, size.height);
            
            // Find and update modal WebView container
            for (UIView *subview in self.modalContainer.subviews) {
                // Recalculate modal dimensions
                CGFloat modalWidth = size.width * 0.8;
                CGFloat modalHeight = size.height * 0.6;
                
                subview.frame = CGRectMake(
                    (size.width - modalWidth) / 2,
                    (size.height - modalHeight) / 2,
                    modalWidth,
                    modalHeight
                );
            }
        }
        
        // Reposition menu if it's visible
        if (self.isMenuVisible && self.menuContainer) {
            // Update menu container frame
            self.menuContainer.frame = CGRectMake(0, 0, size.width, size.height);
            
            // Find and update menu popup
            for (UIView *subview in self.menuContainer.subviews) {
                if (subview != self.menuContainer) { // Menu popup
                    CGFloat menuWidth = 120.0;
                    CGFloat menuHeight = 100.0;
                    CGFloat menuX = size.width - menuWidth - 16;
                    CGFloat menuY = size.height - 120.0 - menuHeight - 16;
                    
                    subview.frame = CGRectMake(menuX, menuY, menuWidth, menuHeight);
                }
            }
        }
    } completion:^(id<UIViewControllerTransitionCoordinatorContext> context)
    {

    }];

    [super viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
}

#pragma mark UIAdaptivePresentationControllerDelegate

- (void)presentationControllerWillDismiss:(UIPresentationController *)presentationController {
    isExiting = TRUE;
}

@end //CDVWKInAppBrowserViewController
