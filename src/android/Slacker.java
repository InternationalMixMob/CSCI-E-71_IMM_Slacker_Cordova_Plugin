package edu.cscie71.imm.slacker.plugin;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import edu.cscie71.imm.app.slacker.client.ISlackerClient;
import edu.cscie71.imm.app.slacker.client.SlackerClient;
import org.json.JSONObject;

public class Slacker extends CordovaPlugin {

    private static final String TAG = "Slacker";
    private static final String PREFS = "Slacker";

    private ISlackerClient slackerClient = new SlackerClient();
    private String token = "";
    private String immTestChannel = "C0F6U0R5E";
    private String authURL = "https://slack.com/oauth/authorize";
    private String slackClientID = "";
    private String slackClientSecret = "";
    private String scope = "channels:read chat:write:user chat:write:bot";
    private Dialog dialog;
    private CallbackContext callbackContext;
    private WebView inAppWebView;

    @Override
    public void initialize(CordovaInterface cordovaInterface, CordovaWebView cordovaWebView) {
        super.initialize(cordovaInterface, cordovaWebView);
        int slackClient = cordova.getActivity().getResources().getIdentifier("slack_client_id", "string", cordova.getActivity().getPackageName());
        slackClientID = cordova.getActivity().getString(slackClient);
        int slackSecret = cordova.getActivity().getResources().getIdentifier("slack_client_secret", "string", cordova.getActivity().getPackageName());
        slackClientSecret = cordova.getActivity().getString(slackSecret);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        token = getOAuthToken();
        if (action.equals("postMessage")) {
            final String message = args.getString(0);
            final String channelId;
            // check for our channelId
            if (args.length() > 1)
                channelId = args.getString(1);
            else
                channelId = immTestChannel;
            final Slacker slacker = this;
            final CallbackContext cc = callbackContext;
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    slacker.postMessage(message, channelId, cc);
                }
            });
        } else if (action.equals("getChannelList")) {
            final boolean excludeArchivedChannels = args.getBoolean(0);
            final Slacker slacker = this;
            final CallbackContext cc = callbackContext;
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    slacker.getChannelList(excludeArchivedChannels, cc);
                }
            });
        } else if (action.equals("slackAuthenticate")) {
            final Slacker slacker = this;
            this.callbackContext = callbackContext;
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    slacker.openAuthScreen();
                }
            });
        } else {
            return false;
        }
        return true;
    }

    private void postMessage(String message, String channelId, CallbackContext callbackContext) {
        String response = slackerClient.postMessage(token, channelId, message);
        if (response.contains("\"ok\":true")) {
            callbackContext.success(message);
        } else {
            callbackContext.error("Error posting to the Slack channel.");
        }
    }

    private void getChannelList(boolean excludeArchivedChannels, CallbackContext callbackContext) {
        String response = slackerClient.getChannelList(token, excludeArchivedChannels);
        if (response.contains("\"ok\":true")) {
            callbackContext.success(response);
        } else {
            callbackContext.error("Error getting channel list from Slack.");
        }
    }

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
    private void openAuthScreen() {
        Runnable runnable = new Runnable() {
            @SuppressLint("NewApi")
            public void run() {
                dialog = new Dialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
                dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);

                LinearLayout mainLayout = new LinearLayout(cordova.getActivity());
                mainLayout.setOrientation(LinearLayout.VERTICAL);

                inAppWebView = new WebView(cordova.getActivity());
                inAppWebView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
                inAppWebView.setWebChromeClient(new WebChromeClient());
                WebViewClient client = new AuthBrowser();
                inAppWebView.setWebViewClient(client);
                WebSettings settings = inAppWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setJavaScriptCanOpenWindowsAutomatically(true);

                inAppWebView.loadUrl(authURL + "?client_id=" + slackClientID + "&scope=" + scope);
                inAppWebView.getSettings().setLoadWithOverviewMode(true);
                inAppWebView.getSettings().setUseWideViewPort(true);
                inAppWebView.requestFocus();
                inAppWebView.requestFocusFromTouch();

                mainLayout.addView(inAppWebView);

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(dialog.getWindow().getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;

                dialog.setContentView(mainLayout);
                dialog.show();
                dialog.getWindow().setAttributes(lp);
            }
        };
        this.cordova.getActivity().runOnUiThread(runnable);
    }

    private void closeAuthScreen() {
        final WebView childView = this.inAppWebView;
        // The JS protects against multiple calls, so this should happen only when
        // closeDialog() is called by other native code.
        if (childView == null) {
            return;
        }
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                childView.setWebViewClient(new WebViewClient() {
                    // NB: wait for about:blank before dismissing
                    public void onPageFinished(WebView view, String url) {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                });
                // NB: From SDK 19: "If you call methods on WebView from any thread
                // other than your app's UI thread, it can cause unexpected results."
                // http://developer.android.com/guide/webapps/migrating.html#Threads
                childView.loadUrl("about:blank");
            }
        });
    }

    @Override
    public void onReset() {
        closeAuthScreen();
    }

    /* License end */

    private void storeOAuthToken(String token) {
        Context context = cordova.getActivity();
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString("token", token);
        editor.apply();
    }

    private String getOAuthToken() {
        Context context = cordova.getActivity();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Return null as default value
        return prefs.getString("token", "");
    }

    /**
     * WebViewClient that detects callback URLs from Slack and stores the OAuth token
     */
    public class AuthBrowser extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (url.contains("response?")) {
                String[] pairs = url.split("&");
                for (String pair : pairs) {
                    if (pair.contains("code")) {
                        final String code = pair.substring(pair.indexOf("code=") + 5);

                        cordova.getThreadPool().execute(new Runnable() {
                            @Override
                            public void run() {
                                String response = slackerClient.getOAuthToken(slackClientID, slackClientSecret, code);
                                try {
                                    JSONObject json = new JSONObject(response);
                                    token = json.getString("access_token");
                                    storeOAuthToken(token);
                                    closeAuthScreen();
                                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                                } catch (JSONException e) {
                                    Log.e(TAG, "Something went wrong parsing the OAuth response");
                                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
                                }
                            }
                        });
                    } else if (pair.contains("error")) {
                        // This will happen when the user hits cancel
                        closeAuthScreen();
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
                    }
                }
            }
        }
    }
}
