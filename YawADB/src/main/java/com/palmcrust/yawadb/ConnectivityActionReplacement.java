package com.palmcrust.yawadb;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;


@TargetApi(21)
class ConnectivityActionReplacement extends ConnectivityManager.NetworkCallback {

    private final Context context;
    private final ConnectivityManager manager;
    private final BroadcastReceiver receiver;


    ConnectivityActionReplacement(Context context, BroadcastReceiver receiver) {
        this.context = context;
        this.receiver = receiver;
        this.manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

    }

    void connect() {
        NetworkRequest request = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        manager.registerNetworkCallback(request, this);
    }

    void disconnect() {
        manager.unregisterNetworkCallback(this);
    }

    @Override
    public void onAvailable(Network network) {
        receiver.onReceive(context, new Intent(YawAdbConstants.ConnectivityAction));
    }
}

